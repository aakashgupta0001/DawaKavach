# DawaKavach

A medicine safety checker for families. Take a photo of a pharmacy bill, and DawaCheck tells you if two medicines in the house contain the same salt or shouldn't be taken together.

We're building this for our Java Full Stack Development lab (AIML). Version 1 is what we're submitting. Version 2 is the stuff we want to add after that.

> Heads up: this is still a work in progress, so some of the steps below may change as we build.

## Why we're making this

If you've ever looked at an elderly relative's medicine box, you know the problem. There's a strip from the heart doctor, one from the bone doctor, and a cold tablet someone picked up from the chemist. Every box has a different name on it, and nobody checks whether two of them are actually the same salt.

Paracetamol is the easy example. It's in plain fever tablets, but it's also inside a lot of cold and pain combination tablets. Take one of each and you're doubling up without knowing it.

This isn't just a hunch. A 2025 study of 600 older adults across six Indian cities found about a third of them were taking five or more medicines a day, and duplicate therapy was one of the most common prescribing problems. The paper is linked in our project report.

## What v1 does

- Reads medicine names from a photo of a pharmacy bill or medicine box. You can also just type the name.
- Matches each brand to its salts, even when OCR gets a letter or two wrong
- Keeps a separate medicine list for each person in the family
- Warns you when the same salt shows up twice, or when two salts are known to interact (we use the DDInter 2.0 database for this)
- For pairs DDInter doesn't cover, our own ML model gives a "possible risk" score. It's always labelled as a prediction, not a fact.
- Explains each warning in simple Hindi or English, plus a couple of questions you can ask your doctor
- Shows cheaper generic options with the same salt and strength (Jan Aushadhi)
- Sends dose and refill reminders by email, and makes a one-page PDF you can carry to your next appointment

The last two are bonus modules. If we run short on time, those go first.

## Planned for v2

- Scan the QR code on the medicine pack and check its batch against CDSCO's monthly list of substandard drugs
- Let families know if a batch they already bought gets flagged later
- Flag combinations the government has banned, and remind people before medicines expire
- A WhatsApp bot, so grandparents can just send a photo instead of installing another app
- Caregiver mode, for when you're managing a parent's medicines from another city
- Extra checks for older people (based on STOPP/START), an allergy check and a symptom diary
- Read-aloud and a large text mode

## Tech stack

- Backend: Java 21 and Spring Boot (Web, Security, Data JPA, Mail, Scheduling)
- Frontend: plain HTML, CSS and JavaScript
- Database: MySQL
- OCR: Tesseract through Tess4J
- Brand matching: Apache Commons Text (Jaro-Winkler)
- ML: Python, scikit-learn, XGBoost and RDKit, served with FastAPI
- LLM: Claude or Gemini API (we haven't picked one yet)

Python is only used for the ML part. Everything else is Java.

## Folder structure

```
dawacheck/
├── backend/       Spring Boot app (the HTML/CSS/JS pages live in src/main/resources/static)
├── ml-service/    FastAPI app and the trained model
├── notebooks/     Jupyter notebooks we used to train the model
├── data/          import scripts and sample CSV files
└── docs/          project report (PDF)
```

## Running it locally

You'll need Java 21, Maven, MySQL 8, Python 3.10 or newer, and Tesseract.

Install Tesseract first. On Ubuntu it's `sudo apt install tesseract-ocr`. On Windows we used the UB Mannheim installer. Note down where it installs, because Tess4J needs to find the `tessdata` folder.

**1. Create the database**

```sql
CREATE DATABASE dawacheck;
```

**2. Set your environment variables**

You can also put these in `backend/src/main/resources/application.properties`. Just don't commit your API key.

```
DB_URL=jdbc:mysql://localhost:3306/dawacheck
DB_USER=root
DB_PASSWORD=your_password
LLM_API_KEY=your_key
ML_SERVICE_URL=http://localhost:8000
TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
```

The `TESSDATA_PREFIX` path depends on your Tesseract version and OS, so check yours.

**3. Start the ML service**

```bash
cd ml-service
pip install -r requirements.txt
uvicorn app:app --port 8000
```

**4. Start the backend**

```bash
cd backend
mvn spring-boot:run
```

**5. Open the app**

Go to http://localhost:8080, sign up and add a family member.

The first time you run it, log in as admin and import the datasets from the Admin page (put the CSV files in `data/`). If you skip this, medicine search will come up empty.

## Main endpoints

These are the ones you'll hit most often:

```
POST /api/scans                      upload a bill photo
POST /api/scans/{id}/confirm         save the confirmed medicines
GET  /api/members/{id}/cabinet       a person's current medicines
GET  /api/members/{id}/alerts        warnings with explanations
GET  /api/medicines/{id}/generics    cheaper same-salt options
```

The full list is in the project report.

## Data we use

- DDInter 2.0 for drug interactions and how serious they are
- Public Indian medicine datasets from Kaggle for brand-to-salt mapping and prices
- PubChem for chemical structures (used by the ML model)
- The Jan Aushadhi product list for generic options

Prices in these datasets can be a few years old, so the app shows them as approximate. If you want to use any of this outside college, check each dataset's terms first.

## Known issues

- OCR has a hard time with shiny foil strips and blurry photos. Printed bills work much better.
- Handwritten prescriptions don't work yet.
- If a brand isn't in the dataset, we can't find it.
- The ML model can be wrong. Also, a pair missing from DDInter doesn't mean it's safe.

## Please read this

DawaCheck is a student project, not a medical device. It never tells anyone to stop or change a medicine, and you shouldn't use it that way. If it flags something, show it to your doctor or pharmacist.

## Team

- Aakash Gupta
- Aryan Chauchan
- Aaditya
- Anushka

Java Full Stack Development Lab, AIML
