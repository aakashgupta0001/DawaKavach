# DawaCheck — backend

Java 21 and Spring Boot 3.4, with Spring Web, Security, Data JPA, Validation,
Mail and Scheduling, as section 11 of the project documentation specifies.

```bash
mvn spring-boot:run                                 # H2, port 8090
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## The database

The default profile uses a file-backed H2 database in MySQL compatibility mode,
at `backend/data/dawacheck.mv.db`. Deleting that folder makes the next start
re-seed the knowledge base and the demo family.

MySQL is the real target. Create the database with `utf8mb4` — alert
explanations are stored in Hindi as well as English — and put the credentials in
`application-mysql.properties`:

```sql
CREATE DATABASE dawacheck CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Fourteen tables, as designed in section 12:

| Table | Holds |
|---|---|
| `users` | one row per account, with a BCrypt password hash and the consent timestamp |
| `family_members` | the family, with age and recorded allergies |
| `salts` | the unit every check works on, with ATC code, classes and daily ceiling |
| `salt_synonyms` | Acetaminophen and PCM both resolving to Paracetamol |
| `medicines` | Indian brand names |
| `medicine_salts` | the brand-to-salt mapping the whole application rests on |
| `interactions` | DDInter pairs, graded major, moderate or minor |
| `cabinet_entries` | what each member is actually taking |
| `scans` | one uploaded photo and the text read from it |
| `scan_items` | one line of the bill and the brand it was matched to |
| `alerts` | a snapshot of the last safety check |
| `reminders` | dose and refill reminders |
| `generic_products` | the Jan Aushadhi list |
| `llm_cache` | generated explanations, keyed without anything identifying |

## The API

Ids on the wire are prefixed strings — `fm1`, `m12`, `s7`, `c4` — over ordinary
auto-increment primary keys. `com.dawacheck.dto.Ids` explains why.

| Method and path | Module |
|---|---|
| `POST /api/auth/register`, `/login`, `/logout`, `GET /api/auth/me` | 1 |
| `GET` `POST /api/members`, `GET` `PUT` `DELETE /api/members/{id}` | 1 |
| `GET /api/medicines/search?q=`, `GET /api/medicines/{id}`, `GET /api/salts` | 2 |
| `GET /api/medicines/compare?a=&b=` | 2 (feature 5) |
| `GET /api/admin/overview`, `POST /api/admin/import`, `POST /api/admin/mappings/{id}` | 2 |
| `POST /api/scans`, `GET /api/scans/{id}`, `POST /api/scans/{id}/confirm` | 3 |
| `GET /api/scans/samples` | 3 |
| `GET` `POST /api/members/{id}/cabinet`, `PUT` `DELETE /api/cabinet/{entryId}` | 4 |
| `GET /api/members/{id}/alerts` | 5, 7 |
| `POST /api/predict` | 6 |
| `GET /api/medicines/{id}/generics` | 8 |
| `GET /api/members/{id}/reminders`, `POST` `PUT` `DELETE /api/reminders` | 9 |
| `GET /api/members/{id}/summary`, `GET /api/members/{id}/summary.pdf` | 9 |

Everything under `/api/admin` requires `ROLE_ADMIN`. Everything else under
`/api` requires a session, and every read is scoped by owner id in the
repository, so asking for another family's member gives a 404 rather than their
medicine list.

## Configuration

Under the `dawacheck.` prefix in `application.properties`:

| Key | Effect when unset |
|---|---|
| `ocr.tessdata` | the scanner reports that OCR is unavailable and offers the sample bills |
| `ocr.language` | `eng` |
| `predict.url` | the built-in heuristic runs and is labelled as such |
| `static-dir` | `file:../frontend/`, so the pages are served from the sibling folder |
| `cors-origins` | only needed when the frontend is served from another origin |
| `seed` | `true`; set false to start with an empty database |

Mail is configured through the ordinary `spring.mail.*` keys. With no mail host
set, a due reminder is logged instead of sent.

## Tests

```bash
mvn -f backend/pom.xml test
```

213 tests, about 25 seconds. They run on an in-memory database seeded by
`SeedService` exactly as a real first start is, so they assert against the
knowledge base the application actually ships with rather than against fixtures
that could drift away from it.

| Where | What it covers |
|---|---|
| `SafetyCheckServiceTest` | module 5, one case per rule, including the worked example from section 5 in full |
| `ExplanationServiceTest` | module 7: both languages complete, the closing line, the safety filter in the path |
| `SafetyFilterTest` | the phrases that must never reach a family, in both languages |
| `RiskPredictionServiceTest`, `RiskPredictionBleedingTest` | module 6: the threshold means something, and blood thinners are caught |
| `KnowledgeBaseTest` | module 2: brand matching against the mistakes OCR actually makes |
| `ScanFlowTest` | module 3 end to end, including that nothing is saved before the user confirms |
| `ApiSecurityTest` | one account cannot reach another family, and an owner cannot reach `/api/admin` |
| `AdminServiceTest` | CSV import, and everything it refuses rather than guesses |
| `GenericAndCompareTest` | modules 8 and feature 5 |
| `CabinetAndReminderTest` | module 4 and reminder validation |
| `SummaryServiceTest` | module 9, including the Hindi PDF font fallback |
| `SeedServiceTest`, `StrengthsTest`, `IdsTest`, `OcrLineCleaningTest`, `CsvSplittingTest` | the seed and the small pieces |

Two conventions in there are worth keeping:

**The engine tests build their medicine list in memory.** `check` takes a member
and a list of entries and returns alerts without touching the database, so each
case reads as "these boxes are on the shelf, these warnings should come back".
`Fixtures` builds them from the real seeded catalogue, so a test cannot quietly
invent a composition.

**`ScanFlowTest` is deliberately not transactional.** The bug it was written
after was invisible inside a single long-lived persistence context: the member
behind a scan came back as a lazy proxy, reading its id gave null, and the safety
check that followed a confirmation ran over an empty list and reported nothing
wrong. A test sharing one transaction across the flow would have kept the real
member in the context and gone green. The tests that do write for real clear up
after themselves instead.

## Things worth knowing before changing this code

**The entities use public fields, and a `@ManyToOne` that is read through must
not be `LAZY`.** A lazy association is a generated proxy whose own fields are
never filled, and the loading happens in the getters, so `scan.member.id` reads
as null with no exception and no log line. `domain/package-info.java` says which
associations are lazy and why.

**The safety check is recomputed on every request**, not read back from the
`alerts` table. An alert is only true of the medicine list as it stands, and a
medicine marked stopped a moment ago must not still raise a warning. The table
is a snapshot for the summary and the reminder job.

**A recorded fact always beats a prediction.** The pairwise check looks in the
interaction dataset first and only asks the model where the dataset is silent,
and a prediction is amber whatever the model scored it. Red is for what a
dataset or a rule establishes.

**Explanations come from templates, not from a language model.** A template
cannot invent a dose or recommend stopping a medicine, and it says the same thing
every time the same facts come in. `ExplanationService` documents where a model
would be switched on and what may be sent to it: salt names, the alert type and
the language, never a name, an age or a photo.
