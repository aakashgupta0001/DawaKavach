package com.dawacheck.service;

import com.dawacheck.config.AppProperties;
import com.dawacheck.domain.AppUser;
import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.GenericProduct;
import com.dawacheck.domain.Interaction;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.domain.Reminder;
import com.dawacheck.domain.Salt;
import com.dawacheck.domain.SaltSynonym;
import com.dawacheck.domain.Scan;
import com.dawacheck.domain.ScanItem;
import com.dawacheck.repo.AlertRecordRepository;
import com.dawacheck.repo.CabinetEntryRepository;
import com.dawacheck.repo.FamilyMemberRepository;
import com.dawacheck.repo.GenericProductRepository;
import com.dawacheck.repo.InteractionRepository;
import com.dawacheck.repo.MedicineRepository;
import com.dawacheck.repo.ReminderRepository;
import com.dawacheck.repo.SaltRepository;
import com.dawacheck.repo.SaltSynonymRepository;
import com.dawacheck.repo.ScanRepository;
import com.dawacheck.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the knowledge base and one demo family into an empty database.
 *
 * The knowledge base here is a genuine extract, not invented data: twenty-four
 * salts with their ATC codes and classes, twenty-six Indian brands with the
 * compositions printed on their boxes, seventeen interaction pairs with the
 * severity grades DDInter gives them, and fourteen Jan Aushadhi generics. It is
 * what the admin CSV import would load, written out so the application is
 * useful the first time it starts.
 *
 * The demo family is the worked example from section 5. Dadaji, 72, is taking
 * seven medicines prescribed by three different doctors, and three of them
 * contain paracetamol: Dolo 650 twice a day, Combiflam twice a day and Sinarest
 * three times a day come to 3450 mg against a 3000 mg daily ceiling. Nobody
 * involved did anything wrong, and nobody can see it from the boxes. That is the
 * case the application exists for, so it is the case it ships with.
 *
 * Set {@code dawacheck.seed=false} to start with an empty database instead.
 */
@Service
public class SeedService {

  private static final Logger log = LoggerFactory.getLogger(SeedService.class);

  public static final String DEMO_EMAIL = "aakash@example.com";
  public static final String DEMO_PASSWORD = "dawacheck";

  private final AppProperties props;
  private final AlertRecordRepository alerts;
  private final UserRepository users;
  private final FamilyMemberRepository members;
  private final SaltRepository salts;
  private final SaltSynonymRepository synonyms;
  private final MedicineRepository medicines;
  private final InteractionRepository interactions;
  private final GenericProductRepository generics;
  private final CabinetEntryRepository entries;
  private final ReminderRepository reminders;
  private final ScanRepository scans;
  private final PasswordEncoder encoder;
  private final KnowledgeBase kb;

  public SeedService(AppProperties props, AlertRecordRepository alerts, UserRepository users,
      FamilyMemberRepository members,
      SaltRepository salts, SaltSynonymRepository synonyms, MedicineRepository medicines,
      InteractionRepository interactions, GenericProductRepository generics,
      CabinetEntryRepository entries, ReminderRepository reminders, ScanRepository scans,
      PasswordEncoder encoder, KnowledgeBase kb) {
    this.props = props;
    this.alerts = alerts;
    this.users = users;
    this.members = members;
    this.salts = salts;
    this.synonyms = synonyms;
    this.medicines = medicines;
    this.interactions = interactions;
    this.generics = generics;
    this.entries = entries;
    this.reminders = reminders;
    this.scans = scans;
    this.encoder = encoder;
    this.kb = kb;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedOnStartup() {
    if (!props.isSeed()) {
      log.info("Seeding is switched off (dawacheck.seed=false)");
      return;
    }
    run();
  }

  /** Also reachable from the admin page, which is how the demo is reset. */
  @Transactional
  public void run() {
    boolean freshKnowledge = salts.count() == 0;
    if (freshKnowledge) {
      seedKnowledgeBase();
    }
    if (users.count() == 0) {
      seedDemoFamily();
    }
    kb.reload();
  }

  /**
   * Puts the demo family back to its starting state, leaving the knowledge base
   * alone. The admin page calls this.
   */
  @Transactional
  public void resetDemoData() {
    // Deleted in dependency order: an alert and a scan point at a member, a
    // reminder points at a cabinet entry, and a cabinet entry points at both a
    // member and a medicine.
    alerts.deleteAll();
    reminders.deleteAll();
    scans.deleteAll();
    entries.deleteAll();
    members.deleteAll();
    users.deleteAll();
    // Within one flush Hibernate runs its inserts before its deletes, so
    // without this the re-seeded account is inserted while the old row is still
    // there and the unique constraint on email rejects it.
    users.flush();
    seedDemoFamily();
    kb.reload();
    log.info("Demo family reset");
  }

  /* ================================================================= */
  /* knowledge base                                                    */
  /* ================================================================= */

  private record SaltRow(String name, String atc, String classes, Double ceiling,
      List<String> synonyms) {
  }

  private record MedRow(String brand, String maker, String form, String pack, double price,
      Map<String, String> composition) {
  }

  private record InteractionRow(String a, String b, String severity, String mechanism,
      String mechanismHi) {
  }

  private record GenericRow(String name, String form, String pack, double mrp,
      Map<String, String> composition) {
  }

  private void seedKnowledgeBase() {
    Map<String, Salt> byName = new HashMap<>();
    for (SaltRow row : saltRows()) {
      Salt salt = new Salt();
      salt.name = row.name();
      salt.atc = row.atc();
      salt.drugClasses = row.classes();
      salt.dailyCeilingMg = row.ceiling();
      salts.save(salt);
      byName.put(row.name(), salt);
      for (String synonym : row.synonyms()) {
        SaltSynonym s = new SaltSynonym();
        s.salt = salt;
        s.synonym = synonym;
        synonyms.save(s);
      }
    }

    for (MedRow row : medicineRows()) {
      Medicine m = new Medicine();
      m.brand = row.brand();
      m.maker = row.maker();
      m.form = row.form();
      m.pack = row.pack();
      m.price = row.price();
      row.composition().forEach((saltName, strength) -> {
        MedicineSalt ms = new MedicineSalt();
        ms.medicine = m;
        ms.salt = byName.get(saltName);
        ms.strength = strength;
        m.composition.add(ms);
      });
      medicines.save(m);
    }

    for (InteractionRow row : interactionRows()) {
      Long a = byName.get(row.a()).id;
      Long b = byName.get(row.b()).id;
      Interaction i = new Interaction();
      i.saltAId = Math.min(a, b);
      i.saltBId = Math.max(a, b);
      i.severity = row.severity();
      i.mechanism = row.mechanism();
      i.mechanismHi = row.mechanismHi();
      interactions.save(i);
    }

    for (GenericRow row : genericRows()) {
      GenericProduct g = new GenericProduct();
      g.name = row.name();
      g.form = row.form();
      g.pack = row.pack();
      g.mrp = row.mrp();
      Map<Long, String> resolved = new LinkedHashMap<>();
      List<String> parts = new ArrayList<>();
      row.composition().forEach((saltName, strength) -> {
        resolved.put(byName.get(saltName).id, strength);
        parts.add(saltName + " " + strength);
      });
      g.composition = String.join(" + ", parts);
      AdminService.applyGenericKeys(g, resolved);
      generics.save(g);
    }

    log.info("Knowledge base seeded: {} salts, {} medicines, {} interactions, {} generics",
        salts.count(), medicines.count(), interactions.count(), generics.count());
  }

  private List<SaltRow> saltRows() {
    return List.of(
        new SaltRow("Paracetamol", "N02BE01", "analgesic,antipyretic", 3000.0,
            List.of("Acetaminophen", "PCM", "Paracetamol IP")),
        new SaltRow("Ibuprofen", "M01AE01", "nsaid", 1200.0, List.of("Brufen salt")),
        new SaltRow("Aspirin", "B01AC06", "nsaid,antiplatelet", 150.0,
            List.of("Acetylsalicylic acid", "ASA")),
        new SaltRow("Telmisartan", "C09CA07", "arb,antihypertensive", null, List.of()),
        new SaltRow("Phenylephrine", "R01BA03", "decongestant", null, List.of()),
        new SaltRow("Chlorpheniramine", "R06AB04", "antihistamine,sedating", null,
            List.of("Chlorphenamine", "CPM", "Chlorpheniramine maleate")),
        new SaltRow("Pantoprazole", "A02BC02", "ppi", null, List.of()),
        new SaltRow("Metformin", "A10BA02", "antidiabetic", null, List.of("Metformin HCl")),
        new SaltRow("Alprazolam", "N05BA12", "benzodiazepine,sedating", null, List.of()),
        new SaltRow("Warfarin", "B01AA03", "anticoagulant", null, List.of("Warfarin sodium")),
        new SaltRow("Rosuvastatin", "C10AA07", "statin", null, List.of()),
        new SaltRow("Clopidogrel", "B01AC04", "antiplatelet", null, List.of()),
        new SaltRow("Amlodipine", "C08CA01", "ccb,antihypertensive", null, List.of()),
        new SaltRow("Cefixime", "J01DD08", "antibiotic", null, List.of()),
        new SaltRow("Montelukast", "R03DC03", "antiasthmatic", null, List.of()),
        new SaltRow("Levocetirizine", "R06AE09", "antihistamine", null, List.of()),
        new SaltRow("Diclofenac", "M01AB05", "nsaid", 150.0, List.of("Diclofenac sodium")),
        new SaltRow("Serratiopeptidase", "", "enzyme", null, List.of()),
        new SaltRow("Glimepiride", "A10BB12", "antidiabetic,sulfonylurea", null, List.of()),
        new SaltRow("Calcium carbonate", "A12AA04", "supplement", null, List.of()),
        new SaltRow("Cholecalciferol", "A11CC05", "supplement", null, List.of("Vitamin D3")),
        new SaltRow("Atorvastatin", "C10AA05", "statin", null, List.of()),
        new SaltRow("Losartan", "C09CA01", "arb,antihypertensive", null, List.of()),
        new SaltRow("Omeprazole", "A02BC01", "ppi", null, List.of()));
  }

  private List<MedRow> medicineRows() {
    return List.of(
        med("Dolo 650", "Micro Labs", "Tablet", "15 tablets", 31.5, "Paracetamol", "650 mg"),
        med("Crocin Advance", "GSK", "Tablet", "15 tablets", 30.0, "Paracetamol", "500 mg"),
        new MedRow("Combiflam", "Sanofi", "Tablet", "20 tablets", 44.0,
            ordered("Ibuprofen", "400 mg", "Paracetamol", "325 mg")),
        new MedRow("Sinarest", "Centaur", "Tablet", "10 tablets", 85.0,
            ordered("Paracetamol", "500 mg", "Phenylephrine", "5 mg",
                "Chlorpheniramine", "2 mg")),
        med("Telma 40", "Glenmark", "Tablet", "15 tablets", 148.0, "Telmisartan", "40 mg"),
        med("Ecosprin 75", "USV", "Tablet", "14 tablets", 12.0, "Aspirin", "75 mg"),
        med("Pan 40", "Alkem", "Tablet", "15 tablets", 158.0, "Pantoprazole", "40 mg"),
        med("Glycomet 500", "USV", "Tablet", "20 tablets", 36.0, "Metformin", "500 mg"),
        med("Alprax 0.25", "Torrent", "Tablet", "15 tablets", 33.0, "Alprazolam", "0.25 mg"),
        med("Warf 5", "Cipla", "Tablet", "30 tablets", 68.0, "Warfarin", "5 mg"),
        med("Rosuvas 10", "Sun Pharma", "Tablet", "10 tablets", 142.0, "Rosuvastatin", "10 mg"),
        med("Clopilet 75", "Sun Pharma", "Tablet", "10 tablets", 69.0, "Clopidogrel", "75 mg"),
        med("Amlong 5", "Micro Labs", "Tablet", "15 tablets", 58.0, "Amlodipine", "5 mg"),
        med("Zifi 200", "FDC", "Tablet", "10 tablets", 198.0, "Cefixime", "200 mg"),
        new MedRow("Montek LC", "Sun Pharma", "Tablet", "10 tablets", 190.0,
            ordered("Montelukast", "10 mg", "Levocetirizine", "5 mg")),
        new MedRow("Zerodol SP", "Ipca", "Tablet", "10 tablets", 108.0,
            ordered("Diclofenac", "100 mg", "Paracetamol", "325 mg",
                "Serratiopeptidase", "15 mg")),
        new MedRow("Shelcal 500", "Torrent", "Tablet", "15 tablets", 118.0,
            ordered("Calcium carbonate", "500 mg", "Cholecalciferol", "250 IU")),
        med("Amaryl 2", "Sanofi", "Tablet", "30 tablets", 196.0, "Glimepiride", "2 mg"),
        med("Atorva 10", "Zydus", "Tablet", "10 tablets", 92.0, "Atorvastatin", "10 mg"),
        med("Losar 50", "Unichem", "Tablet", "15 tablets", 86.0, "Losartan", "50 mg"),
        med("Omez 20", "Dr Reddys", "Capsule", "20 capsules", 98.0, "Omeprazole", "20 mg"),
        med("Calpol 500", "GSK", "Tablet", "15 tablets", 28.0, "Paracetamol", "500 mg"),
        med("Brufen 400", "Abbott", "Tablet", "15 tablets", 48.0, "Ibuprofen", "400 mg"),
        med("Pantocid 40", "Sun Pharma", "Tablet", "15 tablets", 162.0, "Pantoprazole", "40 mg"),
        med("Telmikind 40", "Mankind", "Tablet", "15 tablets", 132.0, "Telmisartan", "40 mg"),
        med("Dolo 500", "Micro Labs", "Tablet", "15 tablets", 24.0, "Paracetamol", "500 mg"));
  }

  private List<InteractionRow> interactionRows() {
    return List.of(
        new InteractionRow("Aspirin", "Ibuprofen", "major",
            "Ibuprofen can compete with aspirin at the platelet enzyme and blunt its "
                + "heart-protective effect; both also irritate the stomach lining.",
            "आइबुप्रोफेन प्लेटलेट एंजाइम पर एस्पिरिन से होड़ कर सकता है और उसका दिल को बचाने वाला "
                + "असर कम कर सकता है; दोनों पेट की परत को भी नुकसान पहुँचाते हैं।"),
        new InteractionRow("Warfarin", "Aspirin", "major",
            "Both reduce the blood clotting ability, so the chance of bleeding rises when they "
                + "are taken together.",
            "दोनों खून के थक्का बनने की क्षमता घटाते हैं, इसलिए साथ लेने पर रक्तस्राव का ख़तरा "
                + "बढ़ जाता है।"),
        new InteractionRow("Warfarin", "Ibuprofen", "major",
            "NSAIDs added to warfarin raise bleeding risk through platelet effects and stomach "
                + "irritation.",
            "वारफ़ेरिन के साथ NSAID लेने पर प्लेटलेट पर असर और पेट की जलन से रक्तस्राव का ख़तरा "
                + "बढ़ता है।"),
        new InteractionRow("Warfarin", "Diclofenac", "major",
            "NSAIDs added to warfarin raise bleeding risk through platelet effects and stomach "
                + "irritation.",
            "वारफ़ेरिन के साथ NSAID लेने पर प्लेटलेट पर असर और पेट की जलन से रक्तस्राव का ख़तरा "
                + "बढ़ता है।"),
        new InteractionRow("Clopidogrel", "Pantoprazole", "moderate",
            "Acid-reducing medicines of this class can slow the liver step that turns "
                + "clopidogrel into its active form.",
            "इस वर्ग की एसिड कम करने वाली दवाएँ जिगर के उस चरण को धीमा कर सकती हैं जो "
                + "क्लोपिडोग्रेल को सक्रिय रूप में बदलता है।"),
        new InteractionRow("Clopidogrel", "Omeprazole", "major",
            "Omeprazole strongly blocks the liver enzyme that activates clopidogrel, which can "
                + "reduce its effect.",
            "ओमेप्राज़ोल उस जिगर एंजाइम को तेज़ी से रोकता है जो क्लोपिडोग्रेल को सक्रिय करता है, "
                + "जिससे उसका असर घट सकता है।"),
        new InteractionRow("Aspirin", "Clopidogrel", "moderate",
            "Two blood-thinning medicines together raise bleeding risk; doctors sometimes "
                + "prescribe this on purpose after a stent.",
            "खून पतला करने वाली दो दवाएँ साथ में रक्तस्राव का ख़तरा बढ़ाती हैं; स्टेंट के बाद "
                + "डॉक्टर कभी-कभी यह जानबूझकर लिखते हैं।"),
        new InteractionRow("Telmisartan", "Ibuprofen", "moderate",
            "NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the "
                + "kidneys.",
            "NSAID रक्तचाप घटाने वाला असर कम कर सकती हैं और गुर्दों पर अतिरिक्त भार डाल सकती हैं।"),
        new InteractionRow("Telmisartan", "Diclofenac", "moderate",
            "NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the "
                + "kidneys.",
            "NSAID रक्तचाप घटाने वाला असर कम कर सकती हैं और गुर्दों पर अतिरिक्त भार डाल सकती हैं।"),
        new InteractionRow("Losartan", "Ibuprofen", "moderate",
            "NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the "
                + "kidneys.",
            "NSAID रक्तचाप घटाने वाला असर कम कर सकती हैं और गुर्दों पर अतिरिक्त भार डाल सकती हैं।"),
        new InteractionRow("Alprazolam", "Chlorpheniramine", "moderate",
            "Both cause drowsiness, so together they can make a person much sleepier and less "
                + "steady on their feet.",
            "दोनों से नींद आती है, इसलिए साथ लेने पर व्यक्ति को ज़्यादा नींद आ सकती है और चलने में "
                + "लड़खड़ाहट हो सकती है।"),
        new InteractionRow("Diclofenac", "Ibuprofen", "major",
            "Two medicines of the same painkiller family give no extra pain relief but add up "
                + "the stomach and kidney risk.",
            "एक ही दर्द निवारक परिवार की दो दवाओं से दर्द में अतिरिक्त आराम नहीं मिलता, पर पेट और "
                + "गुर्दे का ख़तरा जुड़ जाता है।"),
        new InteractionRow("Aspirin", "Diclofenac", "major",
            "Two medicines of the same painkiller family give no extra pain relief but add up "
                + "the stomach and kidney risk.",
            "एक ही दर्द निवारक परिवार की दो दवाओं से दर्द में अतिरिक्त आराम नहीं मिलता, पर पेट और "
                + "गुर्दे का ख़तरा जुड़ जाता है।"),
        new InteractionRow("Metformin", "Glimepiride", "minor",
            "Both lower blood sugar, so the combination needs sugar monitoring; it is a common "
                + "and intended pairing.",
            "दोनों खून की शक्कर घटाती हैं, इसलिए इस जोड़ी में शुगर की निगरानी ज़रूरी है; यह आम और "
                + "सोची-समझी जोड़ी है।"),
        new InteractionRow("Atorvastatin", "Clopidogrel", "minor",
            "A small shared liver pathway; usually of no practical importance at normal doses.",
            "जिगर का एक छोटा साझा रास्ता; सामान्य खुराक पर आम तौर पर इसका कोई व्यावहारिक महत्व "
                + "नहीं।"),
        new InteractionRow("Calcium carbonate", "Cefixime", "minor",
            "Calcium can reduce how much of the antibiotic the gut absorbs if taken at the same "
                + "time.",
            "एक ही समय पर लेने से कैल्शियम आँतों में एंटीबायोटिक के अवशोषण को घटा सकता है।"),
        new InteractionRow("Rosuvastatin", "Amlodipine", "minor",
            "A small rise in statin levels is possible; rarely matters at these strengths.",
            "स्टैटिन का स्तर थोड़ा बढ़ सकता है; इन मात्राओं पर यह शायद ही मायने रखता है।"));
  }

  private List<GenericRow> genericRows() {
    return List.of(
        generic("Paracetamol Tablets IP 650 mg", "Tablet", "10 tablets", 9.0,
            "Paracetamol", "650 mg"),
        generic("Paracetamol Tablets IP 500 mg", "Tablet", "10 tablets", 6.5,
            "Paracetamol", "500 mg"),
        generic("Ibuprofen Tablets IP 400 mg", "Tablet", "10 tablets", 8.0,
            "Ibuprofen", "400 mg"),
        generic("Telmisartan Tablets IP 40 mg", "Tablet", "10 tablets", 14.0,
            "Telmisartan", "40 mg"),
        generic("Pantoprazole Tablets IP 40 mg", "Tablet", "10 tablets", 18.5,
            "Pantoprazole", "40 mg"),
        generic("Metformin HCl Tablets IP 500 mg", "Tablet", "10 tablets", 6.0,
            "Metformin", "500 mg"),
        generic("Rosuvastatin Tablets IP 10 mg", "Tablet", "10 tablets", 22.0,
            "Rosuvastatin", "10 mg"),
        generic("Amlodipine Tablets IP 5 mg", "Tablet", "10 tablets", 4.5,
            "Amlodipine", "5 mg"),
        generic("Atorvastatin Tablets IP 10 mg", "Tablet", "10 tablets", 12.0,
            "Atorvastatin", "10 mg"),
        generic("Clopidogrel Tablets IP 75 mg", "Tablet", "10 tablets", 23.0,
            "Clopidogrel", "75 mg"),
        new GenericRow("Montelukast 10 mg + Levocetirizine 5 mg Tablets", "Tablet", "10 tablets",
            28.0, ordered("Montelukast", "10 mg", "Levocetirizine", "5 mg")),
        generic("Glimepiride Tablets IP 2 mg", "Tablet", "10 tablets", 8.5,
            "Glimepiride", "2 mg"),
        generic("Omeprazole Capsules IP 20 mg", "Capsule", "10 capsules", 10.0,
            "Omeprazole", "20 mg"),
        new GenericRow("Diclofenac 50 mg + Paracetamol 325 mg Tablets", "Tablet", "10 tablets",
            11.0, ordered("Diclofenac", "50 mg", "Paracetamol", "325 mg")));
  }

  /* ================================================================= */
  /* demo family                                                       */
  /* ================================================================= */

  private void seedDemoFamily() {
    AppUser owner = new AppUser();
    owner.name = "Aakash Gupta";
    owner.email = DEMO_EMAIL;
    owner.passwordHash = encoder.encode(DEMO_PASSWORD);
    // The demo account also opens the admin page, which is how the knowledge
    // base is loaded. An account created through the sign-up form gets OWNER and
    // cannot reach /api/admin.
    owner.role = "ADMIN";
    owner.consentAt = Instant.now();
    users.save(owner);

    Map<String, Salt> saltsByName = new HashMap<>();
    salts.findAll().forEach(s -> saltsByName.put(s.name, s));
    Map<String, Medicine> brands = new HashMap<>();
    medicines.findAll().forEach(m -> brands.put(m.brand, m));

    FamilyMember dadaji = member(owner, "Ram Prasad Gupta", 72, "Grandfather", "Dadaji",
        List.of(saltsByName.get("Cefixime")));
    FamilyMember dadi = member(owner, "Savitri Devi", 68, "Grandmother", "Dadi", List.of());
    FamilyMember papa = member(owner, "Suresh Gupta", 48, "Father", "Papa", List.of());
    FamilyMember self = member(owner, "Aakash Gupta", 21, "Self", "", List.of());

    String mehta = "Dr. Anil Mehta (Physician)";
    String pharmacy = "Self (bought at pharmacy)";

    CabinetEntry telma = entry(dadaji, brands.get("Telma 40"), "Morning after breakfast", 1,
        mehta, "2025-03-14", null, true);
    entry(dadaji, brands.get("Ecosprin 75"), "Night after dinner", 1, mehta, "2025-03-14", null,
        true);
    CabinetEntry pan = entry(dadaji, brands.get("Pan 40"), "Empty stomach, morning", 1, mehta,
        "2024-11-02", null, true);
    entry(dadaji, brands.get("Combiflam"), "Morning and night", 2, "Dr. R. Shetty (Orthopaedic)",
        "2026-10-20", "2026-10-30", false);
    entry(dadaji, brands.get("Dolo 650"), "When fever is above 100", 2, pharmacy, "2026-10-22",
        null, false);
    entry(dadaji, brands.get("Sinarest"), "Three times a day", 3, pharmacy, "2026-10-24", null,
        false);
    CabinetEntry alprax = entry(dadaji, brands.get("Alprax 0.25"), "Night, for sleep", 1,
        "Dr. P. Joshi (Neurology)", "2024-06-09", null, true);

    CabinetEntry glycomet = entry(dadi, brands.get("Glycomet 500"), "Morning and night", 2, mehta,
        "2023-08-01", null, true);
    entry(dadi, brands.get("Amaryl 2"), "Before breakfast", 1, mehta, "2025-01-18", null, true);
    entry(dadi, brands.get("Shelcal 500"), "After lunch", 1, "Dr. Meena Rao (Orthopaedic)",
        "2025-07-05", null, true);
    entry(dadi, brands.get("Rosuvas 10"), "Night", 1, mehta, "2025-02-11", null, true);
    entry(dadi, brands.get("Omez 20"), "Empty stomach, morning", 1, pharmacy, "2024-09-30", null,
        true);

    CabinetEntry pantocid = entry(papa, brands.get("Pantocid 40"), "Empty stomach, morning", 1,
        "Dr. S. Kulkarni (Gastro)", "2026-09-12", "2026-11-12", false);
    entry(papa, brands.get("Atorva 10"), "Night", 1, mehta, "2026-04-02", null, true);
    entry(papa, brands.get("Montek LC"), "Night", 1, "Dr. N. Bose (ENT)", "2026-10-10",
        "2026-10-31", false);

    entry(self, brands.get("Zifi 200"), "Morning and night", 2, "Dr. N. Bose (ENT)",
        "2026-10-25", "2026-10-30", false);

    reminder(telma, "dose", "08:00", true);
    reminder(pan, "dose", "07:15", true);
    reminder(alprax, "dose", "21:30", true);
    reminder(telma, "refill", "2026-11-08", true);
    reminder(glycomet, "dose", "08:00", true);
    reminder(pantocid, "dose", "07:30", false);

    seedMappingQueue(dadaji);
    log.info("Demo family seeded. Log in as {} with the password {}", DEMO_EMAIL, DEMO_PASSWORD);
  }

  /**
   * Scanned lines the catalogue could not place, which is what the admin mapping
   * queue works through.
   *
   * These are real unresolved {@code scan_items}, not a separate list: the queue
   * is a view over the scanner's own leftovers, and the repeat counts are the
   * number of times a family actually scanned that line. A line read twelve
   * times is a brand worth importing.
   */
  private void seedMappingQueue(FamilyMember member) {
    Scan scan = new Scan();
    scan.member = member;
    scan.status = Scan.CONFIRMED;
    scan.imageName = "earlier uploads (photos already deleted)";
    scan.rawText = "Lines from earlier scans that no brand in the catalogue matched.";
    scan.ocrEngine = "seeded";
    scan.imageRetained = false;
    scan.createdAt = Instant.now();

    record Pending(String detected, String cleaned, int seen) {
    }
    List<Pending> pending = List.of(
        new Pending("DOLO 65O TAB 15N", "DOLO 65O", 12),
        new Pending("ZINETAC 15O TAB", "ZINETAC 15O", 7),
        new Pending("OKACET CETRIZINE 10", "OKACET CETRIZINE 10", 4),
        new Pending("LIV 52 DS SYRUP", "LIV 52 DS SYRUP", 3));

    for (Pending p : pending) {
      for (int i = 0; i < p.seen(); i++) {
        ScanItem item = new ScanItem();
        item.scan = scan;
        item.detectedText = p.detected();
        item.cleanedText = p.cleaned();
        item.ocrConfidence = 0.55;
        item.suggestionsJson = "[]";
        item.confirmed = false;
        scan.items.add(item);
      }
    }
    scans.save(scan);
  }

  /* ================================================================= */
  /* small builders                                                    */
  /* ================================================================= */

  private FamilyMember member(AppUser owner, String name, int age, String relation,
      String nickname, List<Salt> allergies) {
    FamilyMember m = new FamilyMember();
    m.owner = owner;
    m.name = name;
    m.age = age;
    m.relation = relation;
    m.nickname = nickname;
    m.allergySaltIds = String.join(",", allergies.stream().map(s -> String.valueOf(s.id)).toList());
    return members.save(m);
  }

  private CabinetEntry entry(FamilyMember member, Medicine medicine, String schedule, int doses,
      String prescribedBy, String start, String end, boolean longTerm) {
    CabinetEntry e = new CabinetEntry();
    e.member = member;
    e.medicine = medicine;
    e.schedule = schedule;
    e.dosesPerDay = doses;
    e.prescribedBy = prescribedBy;
    e.startDate = LocalDate.parse(start);
    e.endDate = end == null ? null : LocalDate.parse(end);
    e.active = true;
    e.longTerm = longTerm;
    return entries.save(e);
  }

  private void reminder(CabinetEntry entry, String type, String at, boolean enabled) {
    Reminder r = new Reminder();
    r.entry = entry;
    r.type = type;
    r.remindAt = at;
    r.channel = "email";
    r.enabled = enabled;
    reminders.save(r);
  }

  private static MedRow med(String brand, String maker, String form, String pack, double price,
      String salt, String strength) {
    return new MedRow(brand, maker, form, pack, price, ordered(salt, strength));
  }

  private static GenericRow generic(String name, String form, String pack, double mrp,
      String salt, String strength) {
    return new GenericRow(name, form, pack, mrp, ordered(salt, strength));
  }

  /** Salt name to strength, keeping the order printed on the box. */
  private static Map<String, String> ordered(String... pairs) {
    Map<String, String> out = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      out.put(pairs[i], pairs[i + 1]);
    }
    return out;
  }
}
