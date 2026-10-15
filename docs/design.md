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
| String, Boolean, Integer, Float, Date, Time | Defaults; integer auto-increment | v1 |
| Note | Title + long text | v1 |
| List | Fixed items, **or** lookup: items sourced from a column of another database (stores the value, a snapshot) | v1 |
| Link | Reference to a record (stores identity). Sources: AnDB database, Contact, Photo/Media, File | v1 |
| Linked | Read-only live display of a field of the linked record | v1 |
| Email, Phone, URL | Tap to act (mail, dial, open) | v1 |
| Rating (1–5), Scale (0–10) | Integer with a dedicated input | v1 |
| Section header | Form label, stores nothing | v1 |
| Created / Modified timestamps | Automatic | v1 |
| Link → Calendar event | Needs custom event search UI and `READ_CALENDAR` | v1.5 |
| Photo/File (attachment), Barcode, Location, Signature | Barcode via ZXing (no ML Kit) | v1.5 |
| Calculated | Formulas, including aggregates over backlinks | later |

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

- Real per-database tables, created/altered at runtime.
- Metadata tables describing them: field definitions (type, order, properties,
  list items, link targets), list views, sorts, filters.
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
