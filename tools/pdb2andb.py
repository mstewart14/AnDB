"""Converts Pilot-DB 1.x databases (.pdb, type DB00, creator DBOS) to AnDB .andb files.

Usage: python3 -I tools/pdb2andb.py OUT_DIR FILE.pdb...

Each .andb file is named after the database and written in AnDB's own SQLite layout at schema
version 6 (see DatabaseFile.kt; update SCHEMA_VERSION and the tables here if that changes). Add it
with Import database... in AnDB. Fields become AnDB fields of the same kind, Pilot-DB's list views
become list views, and records keep Pilot-DB's stored order. Field types other than Text, Checkbox,
Integer, Date, Time, Note and List (Link, Linked, Calculated, Float) aren't handled: such a database
is reported and skipped.

Written from the file format as found in the Pilot-DB databank's files; no Pilot-DB code is used.
The app info block is a flags word and field count, then chunks (type, size, data): 0 field names,
1 field types, 2 a List field's items, 64 a list view (flags, column count, 32-byte name, then field
and width pairs). Records start with one offset per field. Text is NUL-terminated cp1252; Checkbox
one byte; Integer 4 bytes; Date year (2 bytes), month, day; Time hour, minute; List the index of
the item; Note a preview, two unused bytes, then the full text.
"""
import datetime
import json
import os
import sqlite3
import struct
import sys

SCHEMA_VERSION = 6
APPLICATION_ID = 0x416E4442  # "AnDB"
ENCODING = 'cp1252'  # Palm OS Latin-1 superset

# Pilot-DB field type -> (AnDB type code, SQL column type)
TYPES = {
    0: ('string', 'TEXT'),
    1: ('boolean', 'INTEGER'),
    2: ('integer', 'INTEGER'),
    3: ('date', 'TEXT'),
    4: ('time', 'TEXT'),
    5: ('note', 'TEXT'),
    6: ('list', 'TEXT'),
}
CHUNK_NAMES, CHUNK_TYPES, CHUNK_LIST_ITEMS, CHUNK_VIEW = 0, 1, 2, 64


def text(b):
    return b.decode(ENCODING, errors='replace')


def cstring(b, start=0):
    end = b.find(b'\0', start)
    if end < 0:
        end = len(b)
    return text(b[start:end]), end + 1


def read_pdb(path):
    d = open(path, 'rb').read()
    if d[60:68] != b'DB00DBOS':
        raise ValueError('not a Pilot-DB 1.x database: %r' % d[60:68])
    name = d[:32].split(b'\0')[0]
    appinfo = struct.unpack('>I', d[52:56])[0]
    n = struct.unpack('>H', d[76:78])[0]
    entries = [struct.unpack('>IB', d[78 + 8 * i:83 + 8 * i]) for i in range(n)]
    first = entries[0][0] if entries else len(d)
    ends = [o for o, _ in entries[1:]] + [len(d)]
    records = [d[o:e] for (o, attr), e in zip(entries, ends) if not attr & 0x80]  # skip deleted
    return text(name).strip(), d[appinfo:first], records


def read_design(app):
    count = struct.unpack('>H', app[2:4])[0]
    names, types, items, views = [], [], {}, []
    p = 4
    while p + 4 <= len(app):
        kind, size = struct.unpack('>HH', app[p:p + 4])
        c = app[p + 4:p + 4 + size]
        p += 4 + size
        if kind == CHUNK_NAMES:
            names = [text(s) for s in c.split(b'\0')[:count]]
        elif kind == CHUNK_TYPES:
            types = list(struct.unpack('>%dH' % count, c[:2 * count]))
        elif kind == CHUNK_LIST_ITEMS:
            field, n = struct.unpack('>HH', c[:4])
            items[field] = [text(s).strip() for s in c[6:].split(b'\0')[:n]]
        elif kind == CHUNK_VIEW:
            ncols = struct.unpack('>H', c[2:4])[0]
            title = cstring(c[4:36])[0]
            cols = [struct.unpack('>HH', c[36 + 4 * i:40 + 4 * i]) for i in range(ncols)]
            views.append((title, cols))
    return names, types, items, views


def unique_names(names):
    seen, out = {}, []
    for raw in names:
        name = raw.strip() or 'Field'
        seen[name] = seen.get(name, 0) + 1
        out.append(name if seen[name] == 1 else '%s %d' % (name, seen[name]))
    return out


def value(kind, b, items):
    """Field bytes [b] of Pilot-DB type [kind] as AnDB stores them; None for empty."""
    if kind == 0:
        s = cstring(b)[0].strip()
        return s or None
    if kind == 1:
        return 1 if b[:1] not in (b'', b'\0') else 0
    if kind == 2:
        return struct.unpack('>i', b[:4])[0] if len(b) >= 4 else None
    if kind == 3:
        if len(b) < 4:
            return None
        y, m, day = struct.unpack('>HBB', b[:4])
        try:
            return datetime.date(y, m, day).isoformat()
        except ValueError:
            return None
    if kind == 4:
        if len(b) < 2 or b[0] > 23 or b[1] > 59:
            return None
        return '%02d:%02d' % (b[0], b[1])
    if kind == 5:
        # A preview, two unused bytes, then the full text; old notes may have only the preview.
        preview, p = cstring(b)
        full = cstring(b, p + 2)[0] if len(b) > p + 2 else ''
        note = (full if full.strip() else preview).strip()
        return note or None
    if kind == 6:
        i = b[0] if b else None
        return items[i] if i is not None and i < len(items) else None
    raise ValueError('unsupported field type %d' % kind)


def width(pixels):
    return 's' if pixels <= 30 else 'm' if pixels <= 60 else 'l'


def convert(path, out_dir):
    name, app, records = read_pdb(path)
    raw_names, types, items, views = read_design(app)
    unsupported = sorted({t for t in types if t not in TYPES})
    if unsupported:
        raise ValueError('unsupported field types %s' % unsupported)
    names = unique_names(raw_names)
    out = os.path.join(out_dir, safe_filename(name) + '.andb')
    if os.path.exists(out):
        os.remove(out)
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z')

    db = sqlite3.connect(out)
    db.executescript(
        """
        PRAGMA application_id = %d;
        CREATE TABLE andb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE andb_field (
            id INTEGER PRIMARY KEY,
            position INTEGER NOT NULL,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            props TEXT NOT NULL DEFAULT '{}'
        , required INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE andb_view (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL,
            columns TEXT NOT NULL DEFAULT '[]', sort TEXT NOT NULL DEFAULT '[]');
        CREATE TABLE andb_filter (id INTEGER PRIMARY KEY, position INTEGER NOT NULL, name TEXT NOT NULL,
            match_all INTEGER NOT NULL DEFAULT 1, conditions TEXT NOT NULL DEFAULT '[]');
        CREATE TABLE andb_photo (id INTEGER PRIMARY KEY AUTOINCREMENT, data BLOB NOT NULL);
        """ % APPLICATION_ID
    )
    db.executemany('INSERT INTO andb_meta (key, value) VALUES (?, ?)', [('name', name), ('created', now)])

    # Field ids are 1-based positions, so Pilot-DB field i is AnDB field i + 1.
    columns = []
    for i, (fname, kind) in enumerate(zip(names, types)):
        code, sql_type = TYPES[kind]
        props = json.dumps({'items': items.get(i, [])}, ensure_ascii=False) if kind == 6 else '{}'
        db.execute(
            'INSERT INTO andb_field (id, position, name, type, props, required) VALUES (?, ?, ?, ?, ?, 0)',
            (i + 1, i, fname, code, props),
        )
        columns.append('f%d %s' % (i + 1, sql_type))
    db.execute(
        'CREATE TABLE record (_id INTEGER PRIMARY KEY AUTOINCREMENT, _created TEXT NOT NULL, '
        '_modified TEXT NOT NULL%s)' % ''.join(', ' + c for c in columns)
    )

    n = len(types)
    placeholders = ', '.join('?' * (n + 2))
    fields = ', '.join('f%d' % (i + 1) for i in range(n))
    for r in records:
        if len(r) < 2 * n:
            continue
        offs = list(struct.unpack('>%dH' % n, r[:2 * n])) + [len(r)]
        values = [value(types[i], r[offs[i]:offs[i + 1]], items.get(i, [])) for i in range(n)]
        db.execute('INSERT INTO record (_created, _modified, %s) VALUES (%s)' % (fields, placeholders), [now, now] + values)

    for position, (title, cols) in enumerate(views):
        view_cols = [{'fieldId': f + 1, 'width': width(w)} for f, w in cols if f < n]
        db.execute(
            'INSERT INTO andb_view (position, name, columns, sort) VALUES (?, ?, ?, ?)',
            (position, title.strip() or 'View %d' % (position + 1), json.dumps(view_cols), '[]'),
        )
    if views:
        db.execute("INSERT INTO andb_meta (key, value) VALUES ('current_view', '1')")

    db.execute('PRAGMA user_version = %d' % SCHEMA_VERSION)
    db.commit()
    db.execute('VACUUM')
    db.close()
    return out, name, len(records), len(views)


def safe_filename(name):
    return ''.join(c if c.isalnum() or c in ' -_().,' else '_' for c in name).strip() or 'database'


if __name__ == '__main__':
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    failed = 0
    for path in sys.argv[2:]:
        try:
            out, name, nrec, nviews = convert(path, out_dir)
            print('%-30s -> %-38s %5d records, %d views' % (os.path.basename(path), os.path.basename(out), nrec, nviews))
        except Exception as e:  # report and go on with the others
            failed += 1
            print('%-30s FAILED: %s' % (os.path.basename(path), e))
    sys.exit(1 if failed else 0)
