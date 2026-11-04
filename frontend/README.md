# DawaCheck — frontend

Version 1 of the DawaCheck web frontend: plain HTML5, CSS3 and JavaScript with
the Fetch API, exactly as section 11 of the project documentation specifies.
There is no build step, no framework and no npm install — the files here drop
straight into `src/main/resources/static/` of the Spring Boot project.

## Running it

The backend serves these files, so start that and the pages come with it:

```bash
mvn -f backend/pom.xml spring-boot:run
```

Then open <http://localhost:8090/> and log in as aakash@example.com with the
password dawacheck.

Any static server also works for looking at the pages alone, but the API calls
will fail unless `API_BASE` points at a running backend.

## The one switch

[`js/config.js`](js/config.js) decides where the data comes from:

```js
USE_MOCK: false   // real fetch() calls against the Spring Boot API (the default)
USE_MOCK: true    // served in the browser by mock-data + safety-engine
```

Nothing else changes. Every page calls `DawaAPI.*` and never calls `fetch()`
itself, and each `DawaAPI` method returns the same JSON shape in both modes. The
browser mock is still in the repository because it is how these pages were built
before the backend existed, and it is the quickest way to work on a page with no
server running — which is why `mock-data.js` and `safety-engine.js` are still
loaded by every page even though nothing calls into them while `USE_MOCK` is
false.

| `DawaAPI` method | Endpoint (section 13) | Module |
|---|---|---|
| `register` / `login` | `POST /api/auth/register`, `POST /api/auth/login` | 1 |
| `listMembers` / `addMember` | `GET`, `POST /api/members` | 1 |
| `searchMedicines` | `GET /api/medicines/search?q=` | 2 |
| `importCsv` | `POST /api/admin/import` | 2 |
| `createScan` | `POST /api/scans` | 3 |
| `confirmScan` | `POST /api/scans/{id}/confirm` | 3, 4 |
| `getCabinet` | `GET /api/members/{id}/cabinet` | 4 |
| `updateCabinetEntry` / `deleteCabinetEntry` | `PUT`, `DELETE /api/cabinet/{entryId}` | 4 |
| `getAlerts` | `GET /api/members/{id}/alerts` | 5, 7 |
| `predictPair` | `POST /api/predict` | 6 |
| `getGenerics` | `GET /api/medicines/{id}/generics` | 8 |
| `summaryPdfUrl` | `GET /api/members/{id}/summary.pdf` | 9 |

## Pages

| File | What it covers |
|---|---|
| `index.html` | Landing, disclaimer, sign-up and login (module 1) |
| `dashboard.html` | Family members with their alert counts (module 1) |
| `member.html` | Cabinet, alerts, generics, reminders and doctor summary (modules 4, 5, 7, 8, 9) |
| `scan.html` | Upload, confirm each OCR line, then the safety check (module 3) |
| `admin.html` | CSV import, mapping queue, model card, brand comparison (module 2) |

## Layout

```
frontend/
  css/
    base.css         tokens, reset, typography, large-text and high-contrast modes
    layout.css       app shell, grids, auth split
    components.css   buttons, cards, alert cards, forms, tables, modal, toast
    print.css        the doctor summary as a printable one-pager
  js/
    config.js        USE_MOCK, API_BASE, upload limits, match threshold
    api.js           the only place fetch() is called; Jaro-Winkler lives here too
    mock-data.js     stand-in for the MySQL tables, shaped like the real JSON
    safety-engine.js browser stand-in for modules 5, 6 and 7
    i18n.js          English and Hindi UI labels
    ui.js            shared DOM helpers and components
    shell.js         header, footer, language and accessibility controls
    auth.js dashboard.js member.js scan.js admin.js
```

### What `safety-engine.js` is for

It is a browser-side stand-in for three backend pieces, used only while
`USE_MOCK` is true. The backend now implements all three for real, in
`SafetyCheckService`, `RiskPredictionService` and `ExplanationService`:

- **Module 5** — repeated salts, same-salt daily totals, DDInter lookups,
  STOPP-style elderly rules and allergy checks, all computed on salts rather
  than brand names.
- **Module 6** — a transparent heuristic in place of the trained model behind
  `POST /predict`, with the same probability-and-threshold output so the
  "Possible risk (AI prediction)" state is real in the demo.
- **Module 7** — template explanations in English and Hindi, each with doctor
  questions and the closing line, plus the banned-phrase filter that blocks
  wording such as "stop taking" before it reaches the screen.

With `USE_MOCK` false, `api.js` does not call into this file at all: the alert
objects arrive from `GET /api/members/{id}/alerts` in the same shape.

## Safety and accessibility

Built in, not bolted on:

- Every alert ends with "confirm with your doctor or pharmacist", in both
  languages, and no wording ever advises starting, stopping or changing a dose.
- AI-predicted risks carry their own badge, probability and caveat, kept visibly
  separate from DDInter facts.
- Nothing scanned reaches the cabinet without the user confirming it line by
  line; the photo is deleted after confirmation unless the user ticks the box.
- The language toggle needs no round trip — explanations arrive with both
  languages filled in.
- Large-text and high-contrast modes (`A+` and `◐` in the header) persist per
  browser; alerts can be read aloud through the Web Speech API in Hindi or
  English where the device has the voice.
- Touch targets are at least 42px, every control is reachable by keyboard, and
  each page has a skip link.

## Demo data

Four members, 16 medicines and the interactions between them, seeded by the
backend. Dadaji (72) is the worked example from section 5: paracetamol appears in
three of his medicines (3450 mg a day against a 3000 mg ceiling), ibuprofen
collides with his aspirin and his telmisartan, and three elderly rules fire.
Admin → **Reset demo data** restores the starting state.

The scan page offers three sample bills whenever the backend reports that it
cannot read a photo on this machine: a clean printed bill, a blurred medicine
box, and a handwritten slip read at 34% confidence, where the user is asked to
type the name however well the garbled text happens to match a brand.

Prices come from public datasets, may be years old, and are labelled approximate
throughout.
