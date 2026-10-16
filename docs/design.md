# AnDB design notes

## Origin

AnDB recreates the *behaviour* of Pilot-DB (GPL PalmOS flat-file database,
last release 1.1.3, 2005). There is no compatibility with Pilot-DB's `.pdb`
format and no code is derived from it.

## Screens

| Screen | Behaviour | Status |
|---|---|---|
| Chooser | List of databases, alphabetical. New (opens its design), starting blank, from a built-in template (Wine tasting, Books, Recipes, Home inventory) or from the design of an existing database ("Create using…": fields, list views, filters, no records), Rename, Duplicate (design only, or with records; links and lookups to itself point into the copy; a design-only copy restarts IDs), Delete. Export a database as an `.andb` file (its SQLite file; design only, or with records) through the system save dialog; Import database… adds one from such a file under its own name (numbered if taken), its links to itself pointing at it. Links to other databases carry those databases' names, so on another device a database of the same name takes their place (see Links). | Done |
| Chooser, more | Categories, manual order, templates of your own (save a design as one), share straight to another app. | Planned |
| List view | Records as a table with a header row; tap a header to sort (again to reverse). Named list views: columns (show all fields, or chosen ones in order, narrow/medium/wide) and sort levels (field, ascending/descending, case-sensitive). Find: case-sensitive, whole word, one field or all, on the text as shown. Saved named filters. Previous/next in the record view follows the list's order, filter and find. | Done |
| Record view | One record, all fields, an input per type. Previous/next (buttons or swipe); at the last record next becomes + (a new record, as does swiping on), protected mode (lock), + for a new record, duplicate, delete. Edits save on leaving the record. | Done |
| Design view | One row per field: name, type, settings. Add, edit, delete; reorder by drag and drop or menu. Warns before changes that lose or convert values. | Done |
| CSV | From the list menu. Export: the records the list shows (filter and Find), every field, as RFC 4180 UTF-8 with a byte-order mark; dates/times ISO, checkboxes yes/no, ratings as numbers, multiple choices joined by "; ". Import: first row as column names (optional); each column to a field of the same name, a new text field, or skipped; values converted per type (a Link by its record's title, matched once); optionally keep the file's IDs. A report gives rows imported, rows skipped (required field empty, ID taken) and unreadable values. | Done |

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
| Link → Contact, File | Device links with snapshots, chosen with the system pickers; tap to open in their own app | Done |
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
- Records from before a field became required stay as they are until edited,
  but say so when shown ("This record is missing required fields: …", the
  fields marked) without stopping you browsing on. Editing one enforces the rule.

### ID fields

| Kind | Example | Notes |
|---|---|---|
| Increment | `1, 2, 3…`, `INV-0042` | Start value, optional prefix and zero padding. Stored as an integer (sorts numerically); prefix and padding are display only. Next value is stored, so numbers are never reused after deletes. |
| UUID, random | `3f2b9c1e-…` | v4. |
| UUID, time-ordered | `0192f3a4-…` | v7; sorts in creation order. |
| Short code | `K7QX-29MD` | Random, unambiguous alphabet (no 0/O, 1/I/L). |

- Enforced unique with a unique index on the column.
- Adding an ID field to a database with records backfills them in creation order.
- Duplicating a record assigns a new ID; CSV import asks whether to keep the
  file's IDs (an increment's counter then moves past them) or assign new ones.
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
reference by default: Android caps lasting grants per app (512 on recent
versions). A Photo field can instead **copy photos into AnDB**: each is stored
in the database (`andb_photo`, scaled to at most 2048 px, JPEG 90; read in
pieces, as a cursor can't carry a large one), linked as `andb-photo:<id>`
relative to its database, so copies stay when the original is deleted, count
towards no cap, and travel with exports and record-including duplicates. A
picked photo rides with the unsaved edit and is stored in the record's own
transaction; a stored photo no record shows is deleted. Turning copying on
copies the photos already linked, where still readable; turning it off keeps
the copies, and new choices are links again.

**Records (next).**

- A Link field with source Record names a target database (any, including
  this one) and a **display field** of it (what the link shows; the first
  field by default). One linked record per Link field.
- Record view: the linked record's display value; tap to open it; choose a
  record from a searchable list; remove. The list offers a menu for each field
  of the target database that sorts its records into a few groups (2–30
  values, e.g. an aroma's Type and Category) to narrow it, and shows those
  values under each title.
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
- Pointing a Link field at another database (e.g. one rebuilt from scratch,
  which gets a new id) offers **Relink by title**, on by default, with a
  preview of the counts: each link whose snapshot title matches exactly one
  record there (ignoring case) is relinked; the rest keep their snapshot and
  show as unavailable. Off, the links are cleared. Linked fields showing
  through it move to the field of the same name. A link is only resolved in
  its field's current database, never by record id alone.
- Deleting a database records its name in every Link field pointing at it.
  Opening a database whose Link field points at a deleted one: if exactly one
  database now has that name, the field moves there by itself, relinked by
  title, and a message says so. Otherwise the list shows "<field> links to a
  deleted database" with **Fix**, which opens the field's settings with the
  database matching the most saved titles already chosen. Whenever a database
  is chosen, its display field defaults to the field matching the most saved
  titles.

**Contacts and files (done).** Chosen with the system contact and file
pickers, so AnDB needs no permission for either. A contact is kept by its lookup
uri (which finds it after a sync changes its row id) with its name as the
snapshot; it isn't checked for being gone, as that would need permission to
read all contacts, and the Contacts app says so itself. A file keeps lasting
read access, like a photo, with its name as the snapshot, and is marked
unavailable if it can't be read. Tapping either opens it in its own app.
Later: fallback identifiers (contact phone/email, iCalendar UID) to re-match
after a sync.
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
