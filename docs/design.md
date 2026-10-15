# AnDB design notes

## Origin

AnDB recreates the *behaviour* of Pilot-DB (GPL PalmOS flat-file database,
last release 1.1.3, 2005). There is no compatibility with Pilot-DB's `.pdb`
format and no code is derived from it.

## Screens

| Screen | Behaviour | Status |
|---|---|---|
| Chooser | List of databases, alphabetical. New (opens its design), Rename, Delete. | Done |
| Chooser, more | Categories, manual order, create from template / "Create using…" (copy a design), Duplicate (design only or with data), Share. | Planned |
| List view | Records as a table with a header row; tap a header to sort (again to reverse). Named list views: columns (show all fields, or chosen ones in order, narrow/medium/wide) and sort levels (field, ascending/descending, case-sensitive). Find: case-sensitive, whole word, one field or all, on the text as shown. Saved named filters. Previous/next in the record view follows the list's order, filter and find. | Done |
| Record view | One record, all fields, an input per type. Previous/next (buttons or swipe), protected mode (lock), + for a new record, duplicate, delete. Edits save on leaving the record. | Done |
| Design view | One row per field: name, type, settings. Add, edit, delete; reorder by drag and drop or menu. Warns before changes that lose or convert values. | Done |

## Field types

| Type | Notes | Status |
|---|---|---|
| Text, Note, Checkbox, Integer, Decimal, Date, Time | Numbers take digits only; date and time pickers | Done |
| Email, Phone, Web address | Tap to act (mail, dial, open) | Done |
| Rating (1–5), Scale (0–10) | Stars; slider | Done |
| Section header | Form label, stores nothing | Done |
| Created / Modified | Automatic timestamps | Done |
| ID | Assigned when a record is first saved, read-only, unique. Kinds below | Done |
| List | Fixed items or lookup (another field's current values; stores the value, a snapshot). Single or multiple choice | Done |
| Link → Photo | Gallery photo by reference, with a snapshot. See Links | Done |
| Link → Record, Linked | A record of any database, and live display of its fields. See Links | Next |
| Link → Contact, File | Device links with snapshots | Planned |
| Link → Calendar event | Needs custom event search UI and `READ_CALENDAR` | v1.5 |
| Photo copied into app storage, Barcode, Location, Signature | Barcode via ZXing (no ML Kit) | v1.5 |
| Calculated | Formulas, including aggregates over backlinks | Later |

Default values per field are still planned.

### Required fields

- A Required switch for fields the user fills in (not checkboxes, IDs,
  timestamps, section headers).
- A record is never stored with a required field empty; storage refuses it.
  A new record exists only on screen until saved complete. Leaving with
  required fields empty asks: keep editing, or discard changes. A new record
  with nothing entered is dropped without asking.
- Records from before a field became required stay as they are until edited.

### ID fields

| Kind | Example | Notes |
|---|---|---|
| Increment | `1, 2, 3…`, `INV-0042` | Start value, optional prefix and zero padding. Stored as an integer (sorts numerically); prefix and padding are display only. Next value is stored, so numbers are never reused after deletes. |
| UUID, random | `3f2b9c1e-…` | v4. |
| UUID, time-ordered | `0192f3a4-…` | v7; sorts in creation order. |
| Short code | `K7QX-29MD` | Random, unambiguous alphabet (no 0/O, 1/I/L). |

- Enforced unique with a unique index on the column.
- Adding an ID field to a database with records backfills them in creation order.
- Duplicating a record assigns a new ID; CSV import will ask whether to keep or
  regenerate IDs.
- Changing the kind regenerates every ID, so it is a destructive change (warn).

### Lists

- Fixed items (add, edit, reorder, remove) or a lookup of another field's
  current values, in this or another database.
- "Allow multiple choices": chips in the record view; joined (`Fiction,
  Signed`) in the list, and sorted by the joined text.
- Single → multiple keeps each value; multiple → single keeps the first (warn).

### Links

Every link stores the linked item's identity plus a **snapshot** (its name, and
for photos a small thumbnail) so a record still shows something useful when the
item is gone: "unavailable" with a way to link again, never a blank.

**Photos (done).** Chosen with the system photo picker (no permission); the
app keeps lasting read access. The record view shows the thumbnail and name,
full screen on tap; the list view shows only the thumbnail. Linked by
reference: Android caps lasting grants per app (512 on recent versions), so
very large photo collections would need the planned copy-into-app mode.

**Records (next).**

- A Link field with source Record names a target database (any, including
  this one) and a **display field** of it (what the link shows; the first
  field by default). One linked record per Link field.
- Record view: the linked record's display value; tap to open it; choose a
  record from a searchable list; remove.
- **Linked** fields: read-only live display of a field of the record linked
  through a chosen Link field of the same database. Shown in the record view
  and as list columns; Find matches them.
- **Backlinks**: a record lists the records (in any database) linking to it,
  grouped by database and field; tap to open; "+" creates a new record there,
  already linked.
- Deleting a record that others link to: the confirmation says how many; their
  links then show the snapshot marked deleted. (Blocking or clearing instead
  may become options later.)
- Record ids are never reused, so a link can't silently point at a new record
  after its target is deleted.

**Device links (planned).** Contacts and files the same way: snapshot, fallback
identifiers (contact name/phone/email, iCalendar UID) to re-match after sync.
Writing to device data only via intents ("Add to calendar", "Save as
contact"), which need no permission; AnDB never edits contacts or events.

### Filters

- Named filters, matching all or any of their conditions; operators by kind of
  field: text (contains, is, starts with, empty…), numbers and increment IDs
  (=, <, ≥…), dates and times (before, after…; created/modified by date),
  checkboxes (checked or not), multiple choice (includes any/all/none of).
- Text ignores case; empty values never compare less or greater; conditions on
  deleted fields are ignored. The applied filter is remembered per database.

### Other features

- v1: CSV import/export (RFC 4180, UTF-8), share database as a file, templates.
- v1.5: conditional field visibility, validation (min/max, pattern), default
  values, per-field summary stats (count/sum/avg/min/max, value distribution).
- Later: sync via a Nextcloud-synced folder, quick-add home-screen widget.
- Out of scope: public form links, notifications, payments, AI form builder.

## Storage

Plain SQLite (not Room — schemas are user-defined at runtime):

- One SQLite file per database (`PRAGMA application_id` = "AnDB") in the
  app's `databases/andb/`; the chooser lists the files, no separate catalog.
  `PRAGMA user_version` is the schema version; files are upgraded on open
  (and new files created at version 1 go through the same upgrades).
- `andb_meta`: name, created, ID counters, current list view and filter.
- `andb_field`: type, position, required, settings as JSON (list items,
  lookup, ID kind, link source…).
- `andb_view`, `andb_filter`: list views and filters, details as JSON.
- `record` table: `_id`, `_created`, `_modified`, then one column per stored
  field named `f<field id>`, so renaming a field never touches records.
  Deleting or retyping a column rebuilds the table (Android's SQLite has no
  `DROP COLUMN` before Android 14); retyped values are converted with `CAST`.
- Side tables per field: `list_f<id>` (multiple choices: record id, value),
  `link_f<id>` (link snapshots: record id, name, thumbnail). Plain SQL for
  filters and counts, without relying on SQLite JSON functions on older
  Android versions.
- Dates/times stored as ISO 8601 text.

## Standards and constraints

- Kotlin + Jetpack Compose + Material 3; Android app architecture guide
  (UI/data layers, unidirectional data flow).
- minSdk 26, target/compile 36.
- ktlint, detekt, Android Lint in `./gradlew check`; storage tested on a
  device with instrumented tests.
- OWASP MASVS: no `INTERNET` permission; user data excluded from cloud backup
  (device-to-device transfer allowed); least-privilege pickers over broad
  permissions.
- F-Droid: no proprietary dependencies; dependency-info blob disabled.
- Semantic Versioning; GPL-3.0-or-later.

## Open decisions

- Cloud backup currently excluded. Revisit if users want Google backup.
- Encryption at rest (SQLCipher) — not planned for v1.
- Photo copy-into-app mode: when, and whether it becomes the default.
