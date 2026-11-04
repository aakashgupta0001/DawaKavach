package com.dawacheck.service;

import com.dawacheck.domain.AlertRecord;
import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.Interaction;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.domain.Salt;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.AlertRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Module 5, the safety check engine. The heart of the application.
 *
 * Five checks run over one member's active medicines, in this order:
 *
 * <ol>
 *   <li>the same salt appearing in more than one medicine;
 *   <li>how much of one salt the whole list adds up to in a day;
 *   <li>every pair of salts, against the interaction dataset first and the risk
 *       model only where the dataset is silent;
 *   <li>any salt the member is recorded as reacting to;
 *   <li>the STOPP-style checks that apply from age 65.
 * </ol>
 *
 * Every one of them works on salts. Brand names are only ever used to tell the
 * family which boxes an alert is about, because two boxes with different names
 * and the same salt is precisely the thing this application exists to catch.
 */
@Service
public class SafetyCheckService {

  private static final Logger log = LoggerFactory.getLogger(SafetyCheckService.class);

  private static final Map<String, String> SEVERITY_COLOUR = Map.of(
      "major", "red", "moderate", "yellow", "minor", "green");

  private static final Map<String, Integer> COLOUR_ORDER = Map.of("red", 0, "yellow", 1, "green", 2);

  private final KnowledgeBase kb;
  private final ExplanationService explanations;
  private final RiskPredictionService risk;
  private final AlertRecordRepository alertRepo;
  private final ObjectMapper json;

  public SafetyCheckService(KnowledgeBase kb, ExplanationService explanations,
      RiskPredictionService risk, AlertRecordRepository alertRepo, ObjectMapper json) {
    this.kb = kb;
    this.explanations = explanations;
    this.risk = risk;
    this.alertRepo = alertRepo;
    this.json = json;
  }

  /** One use of one salt: which entry it came from and the strength on that box. */
  private record Use(CabinetEntry entry, Salt salt, String strength) {
  }

  /* ================================================================= */

  public List<Dto.AlertView> check(FamilyMember member, List<CabinetEntry> entries) {
    List<CabinetEntry> active = entries.stream().filter(e -> e.active).toList();
    List<Dto.AlertView> alerts = new ArrayList<>();
    Counter seq = new Counter(member);

    Map<Long, List<Use>> bySalt = groupBySalt(active);

    repeatedSalts(member, bySalt, seq, alerts);
    dailyTotals(member, bySalt, seq, alerts);
    pairwise(member, active, seq, alerts);
    allergies(member, active, seq, alerts);
    elderlyRules(member, active, seq, alerts);

    return sortAndDeduplicate(alerts);
  }

  /** Runs the check and keeps a snapshot in the {@code alerts} table. */
  @Transactional
  public List<Dto.AlertView> checkAndRecord(FamilyMember member, List<CabinetEntry> entries) {
    List<Dto.AlertView> alerts = check(member, entries);
    try {
      alertRepo.deleteByMemberId(member.id);
      for (Dto.AlertView a : alerts) {
        AlertRecord row = new AlertRecord();
        row.member = member;
        row.type = a.type();
        row.severity = a.severity();
        row.colour = a.colour();
        row.source = a.source();
        row.saltIds = String.join(",", a.salts());
        row.entryIds = String.join(",", a.entryIds());
        row.payloadJson = json.writeValueAsString(a);
        row.createdAt = Instant.now();
        alertRepo.save(row);
      }
    } catch (JsonProcessingException e) {
      // The snapshot is a convenience for the summary and the reminder job. If
      // it cannot be written the alerts themselves are still correct, so the
      // request continues rather than failing in front of the user.
      log.warn("Could not store the alert snapshot for member {}: {}", member.id, e.getMessage());
    }
    return alerts;
  }

  public Dto.AlertCounts counts(List<Dto.AlertView> alerts) {
    long red = alerts.stream().filter(a -> "red".equals(a.colour())).count();
    long yellow = alerts.stream().filter(a -> "yellow".equals(a.colour())).count();
    long green = alerts.stream().filter(a -> "green".equals(a.colour())).count();
    return new Dto.AlertCounts(red, yellow, green, alerts.size());
  }

  /* ================= 1. the same salt in more than one medicine ================= */

  private void repeatedSalts(FamilyMember member, Map<Long, List<Use>> bySalt, Counter seq,
      List<Dto.AlertView> out) {
    bySalt.forEach((saltId, uses) -> {
      if (uses.size() < 2) {
        return;
      }
      Salt salt = uses.get(0).salt();
      List<String> brands = uses.stream().map(u -> u.entry().medicine.brand).toList();
      Set<String> distinct = new LinkedHashSet<>(brands);

      Facts f = new Facts();
      f.salt = salt.name;
      f.count = uses.size();
      f.distinctBrands = distinct.size();
      f.brand = distinct.iterator().next();
      f.brandsEn = ExplanationService.listToText(List.copyOf(distinct), "en");
      f.brandsHi = ExplanationService.listToText(List.copyOf(distinct), "hi");

      out.add(alert(seq, "DUPLICATE_SALT", "major", "red", "rule", member)
          .salts(List.of(Ids.encode(Ids.SALT, saltId)), List.of(salt.name))
          .entries(uses.stream().map(u -> u.entry().id).toList(), brands)
          .explanation(explanations.explain("duplicate", f))
          .build());
    });
  }

  /* ================= 2. how much of one salt in a day ================= */

  private void dailyTotals(FamilyMember member, Map<Long, List<Use>> bySalt, Counter seq,
      List<Dto.AlertView> out) {
    bySalt.forEach((saltId, uses) -> {
      Salt salt = uses.get(0).salt();
      Double ceiling = salt.dailyCeilingMg;
      if (ceiling == null) {
        return;
      }

      double total = 0;
      String unit = "mg";
      for (Use u : uses) {
        Strengths.Amount amount = Strengths.parse(u.strength());
        // A ceiling is stated in milligrams, so an international-unit strength
        // cannot be added into it. Skip rather than compare unlike quantities.
        if (!"mg".equals(amount.unit())) {
          return;
        }
        unit = amount.unit();
        total += amount.value() * Math.max(1, u.entry().dosesPerDay);
      }

      // Worth raising when several medicines add up, or when one alone already
      // goes past the ceiling. A single medicine inside its limit is not news,
      // and neither is a total nowhere near the limit.
      if (uses.size() < 2 && total <= ceiling) {
        return;
      }
      if (total <= ceiling * 0.6) {
        return;
      }

      boolean over = total > ceiling;
      List<String> brands = uses.stream().map(u -> u.entry().medicine.brand).toList();
      // The brand list for this alert carries the arithmetic, because "3450 mg"
      // means nothing to a family without the three numbers it came from.
      List<String> detailedEn = uses.stream()
          .map(u -> "%s (%s x %d/day)".formatted(
              u.entry().medicine.brand, u.strength(), Math.max(1, u.entry().dosesPerDay)))
          .toList();
      List<String> detailedHi = uses.stream()
          .map(u -> "%s (%s x %d/दिन)".formatted(
              u.entry().medicine.brand, u.strength(), Math.max(1, u.entry().dosesPerDay)))
          .toList();

      Facts f = new Facts();
      f.salt = salt.name;
      f.total = total;
      f.unit = unit;
      f.ceiling = ceiling;
      f.brandsEn = ExplanationService.listToText(detailedEn, "en");
      f.brandsHi = ExplanationService.listToText(detailedHi, "hi");

      out.add(alert(seq, "SALT_DAILY_TOTAL", over ? "major" : "moderate",
          over ? "red" : "yellow", "rule", member)
          .salts(List.of(Ids.encode(Ids.SALT, saltId)), List.of(salt.name))
          .entries(uses.stream().map(u -> u.entry().id).toList(), brands)
          .dailyTotal(new Dto.DailyTotal(Strengths.round2(total), unit, ceiling))
          .explanation(explanations.explain("total", f))
          .build());
    });
  }

  /* ================= 3. every pair of salts ================= */

  private void pairwise(FamilyMember member, List<CabinetEntry> active, Counter seq,
      List<Dto.AlertView> out) {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < active.size(); i++) {
      for (int j = i + 1; j < active.size(); j++) {
        CabinetEntry eA = active.get(i);
        CabinetEntry eB = active.get(j);
        for (MedicineSalt msA : eA.medicine.composition) {
          for (MedicineSalt msB : eB.medicine.composition) {
            if (msA.salt.id.equals(msB.salt.id)) {
              continue; // the duplicate check above already covers this
            }
            // Order the pair by salt id so the same two salts always produce the
            // same wording. Otherwise the title reads "Ibuprofen with
            // Telmisartan" or the other way round depending on which medicine
            // happens to be earlier in the list.
            boolean aFirst = msA.salt.id < msB.salt.id;
            Salt sa = aFirst ? msA.salt : msB.salt;
            Salt sb = aFirst ? msB.salt : msA.salt;
            CabinetEntry entryA = aFirst ? eA : eB;
            CabinetEntry entryB = aFirst ? eB : eA;
            String key = KnowledgeBase.pairKey(sa.id, sb.id) + "::"
                + KnowledgeBase.pairKey(eA.id, eB.id);
            if (!seen.add(key)) {
              continue;
            }
            Interaction known = kb.interaction(sa.id, sb.id);
            if (known != null) {
              out.add(knownInteraction(member, seq, entryA, entryB, sa, sb, known));
            } else {
              Dto.AlertView predicted = predictedInteraction(member, seq, entryA, entryB, sa, sb);
              if (predicted != null) {
                out.add(predicted);
              }
            }
          }
        }
      }
    }
  }

  private Dto.AlertView knownInteraction(FamilyMember member, Counter seq, CabinetEntry eA,
      CabinetEntry eB, Salt sa, Salt sb, Interaction known) {
    Facts f = new Facts();
    f.saltA = sa.name;
    f.saltB = sb.name;
    f.brandA = eA.medicine.brand;
    f.brandB = eB.medicine.brand;
    f.mechanismEn = known.mechanism == null ? "" : known.mechanism;
    f.mechanismHi = known.mechanismHi;
    f.severity = known.severity;
    f.severityHi = ExplanationService.severityHi(known.severity);

    return alert(seq, "INTERACTION_KNOWN", known.severity,
        SEVERITY_COLOUR.getOrDefault(known.severity, "yellow"), "dataset", member)
        .salts(List.of(Ids.encode(Ids.SALT, sa.id), Ids.encode(Ids.SALT, sb.id)),
            List.of(sa.name, sb.name))
        .entries(List.of(eA.id, eB.id), List.of(eA.medicine.brand, eB.medicine.brand))
        .dataset(known.sourceDataset, known.mechanism)
        .explanation(explanations.explain("interaction", f))
        .build();
  }

  private Dto.AlertView predictedInteraction(FamilyMember member, Counter seq, CabinetEntry eA,
      CabinetEntry eB, Salt sa, Salt sb) {
    Dto.PredictResult p = risk.predict(sa, sb);
    if (p.probability() < RiskPredictionService.THRESHOLD) {
      return null;
    }
    Facts f = new Facts();
    f.saltA = sa.name;
    f.saltB = sb.name;
    f.pct = (int) Math.round(p.probability() * 100);

    // A prediction is amber whatever the model scored it. Red is reserved for
    // what a dataset or a rule actually establishes (section 14).
    return alert(seq, "INTERACTION_PREDICTED", p.severity(), "yellow", "ai", member)
        .salts(List.of(Ids.encode(Ids.SALT, sa.id), Ids.encode(Ids.SALT, sb.id)),
            List.of(sa.name, sb.name))
        .entries(List.of(eA.id, eB.id), List.of(eA.medicine.brand, eB.medicine.brand))
        .model(risk.modelName(), p.probability(), RiskPredictionService.THRESHOLD)
        .explanation(explanations.explain("predicted", f))
        .build();
  }

  /* ================= 4. recorded allergies ================= */

  private void allergies(FamilyMember member, List<CabinetEntry> active, Counter seq,
      List<Dto.AlertView> out) {
    for (Long saltId : allergySaltIds(member)) {
      Salt salt = kb.salt(saltId);
      if (salt == null) {
        continue;
      }
      for (CabinetEntry e : active) {
        boolean contains = e.medicine.composition.stream().anyMatch(ms -> ms.salt.id.equals(saltId));
        if (!contains) {
          continue;
        }
        Facts f = new Facts();
        f.salt = salt.name;
        f.brand = e.medicine.brand;

        out.add(alert(seq, "ALLERGY", "major", "red", "rule", member)
            .salts(List.of(Ids.encode(Ids.SALT, saltId)), List.of(salt.name))
            .entries(List.of(e.id), List.of(e.medicine.brand))
            .explanation(explanations.explain("allergy", f))
            .build());
      }
    }
  }

  public static List<Long> allergySaltIds(FamilyMember member) {
    if (member.allergySaltIds == null || member.allergySaltIds.isBlank()) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (String part : member.allergySaltIds.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        ids.add(Long.valueOf(trimmed));
      }
    }
    return ids;
  }

  /* ================= 5. the checks that start at 65 ================= */

  /**
   * A subset of STOPP version 3, the European screening tool for potentially
   * inappropriate prescribing in older adults.
   *
   * These are prompts to review, never verdicts, and the wording says so. The
   * three implemented here are the ones the 2025 six-city study of older Indians
   * found most often: long-term benzodiazepines, acid-reducing medicines running
   * for years, and NSAIDs without stomach protection.
   */
  private void elderlyRules(FamilyMember member, List<CabinetEntry> active, Counter seq,
      List<Dto.AlertView> out) {
    if (member.age < 65) {
      return;
    }
    Map<Long, List<CabinetEntry>> saltsPresent = new LinkedHashMap<>();
    for (CabinetEntry e : active) {
      for (MedicineSalt ms : e.medicine.composition) {
        saltsPresent.computeIfAbsent(ms.salt.id, k -> new ArrayList<>()).add(e);
      }
    }

    List<Long> benzos = present(saltsPresent, "benzodiazepine");
    List<Long> ppis = present(saltsPresent, "ppi");
    List<Long> nsaids = new ArrayList<>(present(saltsPresent, "nsaid"));

    for (Long saltId : benzos) {
      List<CabinetEntry> uses = saltsPresent.get(saltId);
      CabinetEntry e = uses.stream().filter(x -> x.longTerm).findFirst().orElse(uses.get(0));
      Salt salt = kb.requireSalt(saltId);
      Facts f = new Facts();
      f.titleEn = "Long-term sleeping medicine at age %d".formatted(member.age);
      f.titleHi = "%d साल की उम्र में लंबे समय से नींद की दवा".formatted(member.age);
      f.bodyEn = ("%s contains %s, a benzodiazepine. In the 2025 six-city study of older Indians, "
          + "long-term use of this group was among the most common potentially inappropriate "
          + "medicines, mainly because of daytime drowsiness and falls.")
          .formatted(e.medicine.brand, salt.name);
      f.bodyHi = ("%s में %s है, जो बेंज़ोडायज़ेपीन समूह की दवा है। 2025 के छह शहरों के अध्ययन में "
          + "बड़ी उम्र के लोगों में इस समूह का लंबे समय तक इस्तेमाल सबसे आम तौर पर अनुचित दवाओं में "
          + "गिना गया, मुख्य रूप से दिन में नींद आने और गिरने के जोखिम के कारण।")
          .formatted(e.medicine.brand, salt.name);
      f.questionsEn = List.of("How long have I been on this medicine?",
          "Is it still needed for my sleep?",
          "If it is to be reviewed, how is that usually done?");
      f.questionsHi = List.of("मैं यह दवा कब से ले रहा/रही हूँ?",
          "क्या नींद के लिए यह अब भी ज़रूरी है?",
          "अगर इस पर फिर से विचार करना हो तो वह आम तौर पर कैसे होता है?");

      out.add(elderlyAlert(member, seq, "stopp-benzo", List.of(saltId), List.of(e), f));
    }

    for (Long saltId : ppis) {
      List<CabinetEntry> uses = saltsPresent.get(saltId);
      CabinetEntry e = uses.stream().filter(x -> x.longTerm).findFirst().orElse(null);
      if (e == null) {
        continue; // a defined course is exactly what these are for
      }
      Salt salt = kb.requireSalt(saltId);
      Facts f = new Facts();
      f.titleEn = "Acid-reducing medicine running long term";
      f.titleHi = "एसिड कम करने वाली दवा लंबे समय से चल रही है";
      f.bodyEn = ("%s contains %s, a proton pump inhibitor, and the start date on record is more "
          + "than a year old. These are usually meant for a defined course unless there is a "
          + "specific long-term reason.").formatted(e.medicine.brand, salt.name);
      f.bodyHi = ("%s में %s है, जो प्रोटॉन पंप इनहिबिटर है, और रिकॉर्ड में शुरू होने की तारीख एक "
          + "साल से पुरानी है। ऐसी दवाएँ आम तौर पर तय समय के कोर्स के लिए होती हैं, जब तक कोई खास "
          + "लंबी वजह न हो।").formatted(e.medicine.brand, salt.name);
      f.questionsEn = List.of("Is there a reason to continue this long term?",
          "When was the stomach problem last reviewed?",
          "Should calcium or vitamin B12 be checked?");
      f.questionsHi = List.of("इसे लंबे समय तक जारी रखने की कोई खास वजह है?",
          "पेट की समस्या की आख़िरी जाँच कब हुई थी?",
          "क्या कैल्शियम या विटामिन बी12 की जाँच करानी चाहिए?");

      out.add(elderlyAlert(member, seq, "stopp-ppi", List.of(saltId), List.of(e), f));
    }

    // Low-dose aspirin is taken to protect the heart, not for pain, so it is not
    // treated as the painkiller this rule is about.
    nsaids.removeIf(id -> {
      Salt s = kb.salt(id);
      return s != null && RiskPredictionService.classes(s).contains("antiplatelet");
    });
    if (!nsaids.isEmpty()) {
      Long saltId = nsaids.get(0);
      CabinetEntry e = saltsPresent.get(saltId).get(0);
      Salt salt = kb.requireSalt(saltId);
      boolean protectedStomach = !ppis.isEmpty();
      Facts f = new Facts();
      f.titleEn = "Painkiller of the NSAID group at age %d".formatted(member.age);
      f.titleHi = "%d साल की उम्र में NSAID समूह की दर्द निवारक दवा".formatted(member.age);
      f.bodyEn = ("%s contains %s. In older adults this group is linked with stomach bleeding and "
          + "extra load on the kidneys, especially when taken daily rather than occasionally. %s")
          .formatted(e.medicine.brand, salt.name,
              protectedStomach ? "An acid-reducing medicine is already on the list."
                  : "No stomach-protecting medicine appears on this list.");
      f.bodyHi = ("%s में %s है। बड़ी उम्र में यह समूह पेट में रक्तस्राव और गुर्दों पर अतिरिक्त भार "
          + "से जुड़ा है, खासकर जब यह कभी-कभी के बजाय रोज़ ली जाए। %s")
          .formatted(e.medicine.brand, salt.name,
              protectedStomach ? "सूची में एसिड कम करने वाली दवा पहले से है।"
                  : "इस सूची में पेट की रक्षा करने वाली कोई दवा नहीं दिखती।");
      f.questionsEn = List.of("How many days should this painkiller continue?",
          "Do I need a stomach-protecting medicine along with it?",
          "Should my kidney function be checked?");
      f.questionsHi = List.of("यह दर्द की दवा कितने दिन चलनी चाहिए?",
          "क्या इसके साथ पेट की रक्षा वाली दवा चाहिए?",
          "क्या गुर्दे की जाँच करानी चाहिए?");

      out.add(elderlyAlert(member, seq, "stopp-nsaid", nsaids, List.of(e), f));
    }
  }

  private List<Long> present(Map<Long, List<CabinetEntry>> saltsPresent, String cls) {
    return kb.saltsWithClass(cls).stream().filter(saltsPresent::containsKey).toList();
  }

  private Dto.AlertView elderlyAlert(FamilyMember member, Counter seq, String ruleKey,
      List<Long> saltIds, List<CabinetEntry> entries, Facts f) {
    return alert(seq, "ELDERLY_RULE", "moderate", "yellow", "rule", member)
        .salts(saltIds.stream().map(id -> Ids.encode(Ids.SALT, id)).toList(),
            saltIds.stream().map(id -> kb.requireSalt(id).name).toList())
        .entries(entries.stream().map(e -> e.id).toList(),
            entries.stream().map(e -> e.medicine.brand).toList())
        .rule("STOPP v3 (subset)", ruleKey)
        .explanation(explanations.explain("elderly", f))
        .build();
  }

  /* ================= assembly ================= */

  private Map<Long, List<Use>> groupBySalt(List<CabinetEntry> active) {
    Map<Long, List<Use>> bySalt = new LinkedHashMap<>();
    for (CabinetEntry e : active) {
      for (MedicineSalt ms : e.medicine.composition) {
        bySalt.computeIfAbsent(ms.salt.id, k -> new ArrayList<>())
            .add(new Use(e, ms.salt, ms.strength));
      }
    }
    return bySalt;
  }

  /**
   * Collapses findings that say the same thing, then orders them red, amber,
   * green.
   *
   * What counts as "the same thing" differs by type, and getting this wrong is
   * what makes a safety screen unreadable. A family holding two boxes of
   * Combiflam and two of Telma produces four ibuprofen-and-telmisartan brand
   * pairs, but there is one thing to ask the doctor, so an interaction collapses
   * on its salt pair and names the brands inside the alert. An allergy is the
   * opposite: each medicine containing that salt is its own thing to deal with,
   * so those stay separate.
   */
  private List<Dto.AlertView> sortAndDeduplicate(List<Dto.AlertView> alerts) {
    Set<String> seen = new LinkedHashSet<>();
    List<Dto.AlertView> unique = new ArrayList<>();
    for (Dto.AlertView a : alerts) {
      if (seen.add(identity(a))) {
        unique.add(a);
      }
    }
    unique.sort(Comparator.comparingInt(a -> COLOUR_ORDER.getOrDefault(a.colour(), 3)));
    return unique;
  }

  private static String identity(Dto.AlertView a) {
    String salts = new TreeSet<>(a.salts()).toString();
    return switch (a.type()) {
      // One line per salt, or per salt pair, however many boxes it came from.
      case "DUPLICATE_SALT", "SALT_DAILY_TOTAL", "INTERACTION_KNOWN", "INTERACTION_PREDICTED" ->
          a.type() + "::" + salts;
      // One line per rule, not per medicine that triggered it.
      case "ELDERLY_RULE" -> a.type() + "::" + a.ruleKey();
      // One line per medicine.
      default -> a.type() + "::" + salts + "::" + new TreeSet<>(a.entryIds());
    };
  }

  /** Numbers alerts within one member's check, so ids are stable in a response. */
  private static final class Counter {
    private final FamilyMember member;
    private int n = 0;

    Counter(FamilyMember member) {
      this.member = member;
    }

    String next() {
      return "al-" + Ids.encode(Ids.MEMBER, member.id) + "-" + (++n);
    }
  }

  private static Builder alert(Counter seq, String type, String severity, String colour,
      String source, FamilyMember member) {
    return new Builder(seq.next(), type, severity, colour, source, member);
  }

  /**
   * Assembles one alert. A small builder rather than a twenty-argument
   * constructor call, because an alert carries different extra fields depending
   * on where it came from and the calling code should name the ones it sets.
   */
  private static final class Builder {
    private final String id;
    private final String type;
    private final String severity;
    private final String colour;
    private final String source;
    private final FamilyMember member;

    private List<String> saltIds = List.of();
    private List<String> saltNames = List.of();
    private List<String> entryIds = List.of();
    private List<String> medicines = List.of();
    private Map<String, Dto.Explanation> explanation = Map.of();
    private String datasetName;
    private String mechanism;
    private String model;
    private Double probability;
    private Double threshold;
    private Dto.DailyTotal dailyTotal;
    private String ruleSet;
    private String ruleKey;

    Builder(String id, String type, String severity, String colour, String source,
        FamilyMember member) {
      this.id = id;
      this.type = type;
      this.severity = severity;
      this.colour = colour;
      this.source = source;
      this.member = member;
    }

    Builder salts(List<String> ids, List<String> names) {
      this.saltIds = ids;
      this.saltNames = names;
      return this;
    }

    Builder entries(List<Long> ids, List<String> brands) {
      this.entryIds = ids.stream().map(i -> Ids.encode(Ids.ENTRY, i)).toList();
      this.medicines = brands;
      return this;
    }

    Builder dataset(String name, String mechanism) {
      this.datasetName = name;
      this.mechanism = mechanism;
      return this;
    }

    Builder model(String name, double probability, double threshold) {
      this.model = name;
      this.probability = probability;
      this.threshold = threshold;
      return this;
    }

    Builder dailyTotal(Dto.DailyTotal total) {
      this.dailyTotal = total;
      return this;
    }

    Builder rule(String ruleSet, String ruleKey) {
      this.ruleSet = ruleSet;
      this.ruleKey = ruleKey;
      return this;
    }

    Builder explanation(Map<String, Dto.Explanation> explanation) {
      this.explanation = explanation;
      return this;
    }

    Dto.AlertView build() {
      return new Dto.AlertView(id, type, severity, colour, source,
          Ids.encode(Ids.MEMBER, member.id), saltIds, saltNames, entryIds, medicines,
          explanation, Instant.now().toString(), datasetName, mechanism, model, probability,
          threshold, dailyTotal, ruleSet, ruleKey);
    }
  }
}
