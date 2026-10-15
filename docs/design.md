# AnDB design notes

## Origin

AnDB recreates the *behaviour* of Pilot-DB (GPL PalmOS flat-file database,
last release 1.1.3, 2005). There is no compatibility with Pilot-DB's `.pdb`
format and no code is derived from it.

## Screens (from Pilot-DB)

| Screen | Behaviour |
|---|---|
| Chooser | List of databases; categories; alphabetical or manual order; New, Create from template / "Create using…" (copy a design), Duplicate (design only or with data), Rename, Delete, Share. |
| List view | Records as a table. Named list views (column set, order, width). Tap header to sort; multi-field sort editor (per field: descending, case-sensitive). Find (case-sensitive, whole word, one field or all). Saved named filters. |
| Record view | One record, all fields or a view's subset. Prev/next (swipe). Protected mode (lock toggle) to prevent accidental edits. Duplicate record. |
| Design view | One row per field: name, type, properties. Reorder, insert, delete. Warn before destructive schema changes. |

## Field types

| Type | Notes | Phase |
|---|---|---|
| String, Boolean (shown as "Checkbox"), Integer, Float, Date, Time | Default values | v1 |
| ID | Assigned on record creation, read-only, unique. Kinds below | v1 |
| Note | Title + long text | v1 |
| List | Fixed items, **or** lookup: items sourced from a column of another database (stores the value, a snapshot). Single or multiple selection | v1 |
| Link | Reference to a record (stores identity). Sources: AnDB database, Contact, Photo/Media, File | v1 |
| Linked | Read-only live display of a field of the linked record | v1 |
| Email, Phone, URL | Tap to act (mail, dial, open) | v1 |
| Rating (1–5), Scale (0–10) | Integer with a dedicated input | v1 |
| Section header | Form label, stores nothing | v1 |
| Created / Modified timestamps | Automatic | v1 |
| Link → Calendar event | Needs custom event search UI and `READ_CALENDAR` | v1.5 |
| Photo/File (attachment), Barcode, Location, Signature | Barcode via ZXing (no ML Kit) | v1.5 |
| Calculated | Formulas, including aggregates over backlinks | later |

### ID fields

| Kind | Example | Notes |
|---|---|---|
| Increment | `1, 2, 3…`, `INV-0042` | Start value, optional prefix and zero padding. Stored as an integer (sorts numerically); prefix and padding are display only. Next value is stored, so numbers are never reused after deletes. |
| UUID, random | `3f2b9c1e-…` | v4. |
| UUID, time-ordered | `0192f3a4-…` | v7; sorts in creation order. |
| Short code | `K7QX-29MD` | Random, unambiguous alphabet (no 0/O, 1/I/L). |

- Enforced unique with a unique index on the column.
- Adding an ID field to a database with records backfills them in creation order.
- Duplicating a record assigns a new ID; CSV import asks whether to keep or
  regenerate IDs.
- Changing the kind regenerates every ID, so it is a destructive change (warn).

### Multi-select lists

- "Allow multiple" switch on the List field; same fixed items or lookup source.
- Record view: checkboxes/chips. List view: values joined (`Fiction, Signed`),
  sorted by the joined text.
- Filters: includes any of / all of / none of. Statistics: count per item.
- Single → multiple keeps each value; multiple → single keeps the first (warn).

### Links

- **Backlinks**: a record shows every record in other databases linking to it,
  with "+" to create a pre-linked child.
- Deleting a link target: block, clear links, or warn (configurable).
- **Device links** (contacts, media, files, events) cache a display snapshot so
  a deleted item, revoked permission or Android's unused-app permission reset
  shows "Name (unavailable)" with a re-link action instead of a blank.
- Store fallback identifiers (contact name/phone/email, iCalendar UID) so links
  can be re-matched on another device after sync.
- Photos default to **copy into app storage**; reference mode optional per field.
- Writing to device data only via intents ("Add to calendar", "Save as contact"),
  which need no permission. AnDB never edits contacts/events directly.

### Other features

- v1: CSV import/export (RFC 4180, UTF-8), share database as `.sqlite`, templates.
- v1.5: conditional field visibility, validation (required, min/max, pattern),
  per-field summary stats (count/sum/avg/min/max, value distribution).
- Later: sync via a Nextcloud-synced folder, quick-add home-screen widget.
- Out of scope: public form links, notifications, payments, AI form builder.

## Storage

Plain SQLite (not Room — schemas are user-defined at runtime):

- One SQLite file per database (`PRAGMA application_id` = "AnDB") in the
  app's `databases/andb/`; the chooser lists the files, no separate catalog.
- `andb_meta` (name, created), `andb_field` (type, order, properties as JSON:
  list items, link targets, ID kind…), later list views, sorts, filters.
- `record` table: `_id`, `_created`, `_modified`, then one column per stored
  field named `f<field id>`, so renaming a field never touches records.
  Deleting or retyping a column rebuilds the table (Android's SQLite has no
  `DROP COLUMN` before Android 14); retyped values are converted with `CAST`.
- Multi-select values in a side table per field (record id, value) rather than
  packed text, so filters and per-item counts are plain SQL without relying on
  SQLite JSON functions on older Android versions.
- Dates/times stored as ISO 8601 text.

## Standards and constraints

- Kotlin + Jetpack Compose + Material 3; Android app architecture guide
  (UI/data layers, unidirectional data flow).
- minSdk 26, target/compile 36.
- ktlint, detekt, Android Lint in `./gradlew check`.
- OWASP MASVS: no `INTERNET` permission; user data excluded from cloud backup
  (device-to-device transfer allowed); least-privilege pickers over broad
  permissions.
- F-Droid: no proprietary dependencies; dependency-info blob disabled.
- Semantic Versioning; GPL-3.0-or-later.

## Open decisions

- Cloud backup currently excluded. Revisit if users want Google backup.
- Encryption at rest (SQLCipher) — not planned for v1.
