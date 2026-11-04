# DawaCheck

A family medicine safety checker. It reads the medicines a family is taking,
maps Indian brand names to their salts, and warns when two medicines share a
salt or form a risky combination, explaining each warning in plain Hindi or
English.

**It never tells anyone to start, stop or change a medicine.** Every alert ends
by asking the family to confirm with their doctor or pharmacist, and a filter on
the server blocks any wording that strays from that before it reaches the
screen.

The problem it exists for: one salt is sold under many brand names, and no
single doctor sees the whole list. A fever tablet, a cold tablet and a pain
tablet can all contain paracetamol. Nobody involved has done anything wrong, and
nobody can see it from the boxes.

```bash
mvn -f backend/pom.xml spring-boot:run
```

Then open <http://localhost:8090/> and log in as **aakash@example.com** with the
password **dawacheck**. The demo family is already loaded.

## Layout

```
backend/     Java 21 + Spring Boot: the API, the safety engine, the database
frontend/    HTML5, CSS3 and JavaScript with the Fetch API; no build step
```

The backend serves `frontend/` directly in development, so the pages and the API
share an origin and the frontend needs no CORS and no API base URL. See
[backend/README.md](backend/README.md) for the API and the schema, and
[frontend/README.md](frontend/README.md) for the pages.

## Tests

```bash
mvn -f backend/pom.xml test
```

213 tests over the backend, on an in-memory database seeded the same way a real
first start is. `backend/README.md` says what they cover and why two of the
conventions in there are the way they are.

## Version 1 modules

| # | Module | Where it lives |
|---|---|---|
| 1 | User and family management | `AuthController`, `MemberService` |
| 2 | Medicine knowledge base and admin | `KnowledgeBase`, `AdminService` |
| 3 | Medicine scanner | `OcrService`, `ScanService` |
| 4 | Family medicine cabinet | `CabinetService` |
| 5 | Safety check engine | `SafetyCheckService` |
| 6 | AI interaction risk predictor | `RiskPredictionService` |
| 7 | AI explanation assistant | `ExplanationService`, `SafetyFilter` |
| 8 | Generic alternatives finder | `GenericService` |
| 9 | Reminders and doctor summary | `ReminderService`, `SummaryService` |

## The worked example

Dadaji, 72, is taking seven medicines prescribed by three different doctors.
Three of them contain paracetamol: Dolo 650 twice a day, Combiflam twice a day
and Sinarest three times a day. That is **3450 mg a day against a 3000 mg
ceiling**, and it is why the application exists, so it is the state it ships in.

The same list also produces the aspirin and ibuprofen collision, the ibuprofen
and telmisartan collision, and three STOPP-style checks that apply from age 65.

## What is not built

- **Tesseract is not installed on this machine**, so the scanner offers three
  sample bills instead of pretending to have read a photo. Point
  `dawacheck.ocr.tessdata` at a Tesseract language folder and it reads real
  uploads; everything after the reading step is the same code either way.
- **The trained risk model is not running.** `RiskPredictionService` falls back
  to a transparent heuristic over shared drug classes, and says so on the admin
  page and in every alert it produces. Point `dawacheck.predict.url` at the
  FastAPI service to use the real model.
- **No mail server is configured**, so due reminders are logged rather than
  emailed. Set `spring.mail.*` to send them.
- **MySQL is not installed**, so the default profile runs on a file-backed H2
  database in MySQL compatibility mode. The schema is the same;
  `-Dspring-boot.run.profiles=mysql` switches over.
- Modules 10 to 12 (authenticity and recall checks, the caregiver and WhatsApp
  layer, the symptom diary) are version 2 and are not started.
- **The frontend has no tests.** The backend suite covers the rules and the API;
  the pages have been walked by hand in both languages but nothing checks them
  automatically.
