package com.dawacheck.service;

import com.dawacheck.domain.Interaction;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.domain.Salt;
import com.dawacheck.domain.SaltSynonym;
import com.dawacheck.repo.InteractionRepository;
import com.dawacheck.repo.MedicineRepository;
import com.dawacheck.repo.SaltRepository;
import com.dawacheck.repo.SaltSynonymRepository;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Module 2, the medicine knowledge base, held in memory.
 *
 * The safety check compares every salt of every medicine against every other, so
 * a family of eight medicines asks for the same handful of salts and interaction
 * rows dozens of times in one request. Reading them from MySQL each time would
 * be a lot of trips for data that changes only when the admin imports a file, so
 * the tables are loaded once into maps and rebuilt on {@link #reload()}.
 *
 * The real catalogue is a quarter of a million brands. At that size this
 * snapshot would be replaced by a database index with a trigram or full-text
 * search for the brand lookup; the maps here hold what version 1 imports.
 */
@Service
public class KnowledgeBase {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);
  private static final JaroWinklerSimilarity JARO = new JaroWinklerSimilarity();

  /** Below this score a brand guess is not offered at all. */
  private static final double SEARCH_FLOOR = 0.5;

  /** A salt name matching counts slightly less than the brand name matching. */
  private static final double SALT_MATCH_WEIGHT = 0.92;

  private final SaltRepository saltRepo;
  private final SaltSynonymRepository synonymRepo;
  private final MedicineRepository medicineRepo;
  private final InteractionRepository interactionRepo;

  private volatile Snapshot snapshot;

  public KnowledgeBase(SaltRepository saltRepo, SaltSynonymRepository synonymRepo,
      MedicineRepository medicineRepo, InteractionRepository interactionRepo) {
    this.saltRepo = saltRepo;
    this.synonymRepo = synonymRepo;
    this.medicineRepo = medicineRepo;
    this.interactionRepo = interactionRepo;
  }

  private record Snapshot(
      Map<Long, Salt> salts,
      Map<Long, List<String>> synonyms,
      Map<Long, Medicine> medicines,
      Map<String, Interaction> interactions,
      Map<String, List<Long>> saltsByClass,
      long synonymCount) {
  }

  /** Call after any import or seed so the next request sees the new rows. */
  public void reload() {
    snapshot = null;
  }

  private Snapshot snapshot() {
    Snapshot current = snapshot;
    if (current == null) {
      current = build();
      snapshot = current;
    }
    return current;
  }

  @Transactional(readOnly = true)
  protected Snapshot build() {
    Map<Long, Salt> salts = new LinkedHashMap<>();
    Map<String, List<Long>> byClass = new HashMap<>();
    for (Salt s : saltRepo.findAll()) {
      salts.put(s.id, s);
      for (String cls : RiskPredictionService.classes(s)) {
        byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(s.id);
      }
    }

    Map<Long, List<String>> synonyms = new HashMap<>();
    List<SaltSynonym> allSynonyms = synonymRepo.findAll();
    for (SaltSynonym syn : allSynonyms) {
      synonyms.computeIfAbsent(syn.salt.id, k -> new ArrayList<>()).add(syn.synonym);
    }

    Map<Long, Medicine> medicines = new LinkedHashMap<>();
    for (Medicine m : medicineRepo.findAll()) {
      // Touch the collection while the session is open.
      m.composition.size();
      medicines.put(m.id, m);
    }

    Map<String, Interaction> interactions = new HashMap<>();
    for (Interaction i : interactionRepo.findAll()) {
      interactions.put(pairKey(i.saltAId, i.saltBId), i);
    }

    log.info("Knowledge base loaded: {} salts, {} synonyms, {} medicines, {} interactions",
        salts.size(), allSynonyms.size(), medicines.size(), interactions.size());
    return new Snapshot(salts, synonyms, medicines, interactions, byClass, allSynonyms.size());
  }

  /* ---------------- lookups ---------------- */

  public Salt salt(Long id) {
    return snapshot().salts().get(id);
  }

  public Salt requireSalt(Long id) {
    Salt s = salt(id);
    if (s == null) {
      throw new NotFoundException("That salt is not in the knowledge base.");
    }
    return s;
  }

  public Medicine medicine(Long id) {
    return snapshot().medicines().get(id);
  }

  public Medicine requireMedicine(Long id) {
    Medicine m = medicine(id);
    if (m == null) {
      throw new NotFoundException("Medicine not found.");
    }
    return m;
  }

  public List<Salt> allSalts() {
    return List.copyOf(snapshot().salts().values());
  }

  public List<Medicine> allMedicines() {
    return List.copyOf(snapshot().medicines().values());
  }

  public List<String> synonyms(Long saltId) {
    return snapshot().synonyms().getOrDefault(saltId, List.of());
  }

  public long synonymCount() {
    return snapshot().synonymCount();
  }

  /** Salt ids carrying a pharmacological class, for the elderly rules. */
  public List<Long> saltsWithClass(String cls) {
    return snapshot().saltsByClass().getOrDefault(cls, List.of());
  }

  /** The recorded interaction for a pair of salts, in either order, or null. */
  public Interaction interaction(Long a, Long b) {
    return snapshot().interactions().get(pairKey(a, b));
  }

  public static String pairKey(Long a, Long b) {
    return a <= b ? a + "|" + b : b + "|" + a;
  }

  /* ---------------- brand matching (section 10) ---------------- */

  public record Match(Medicine medicine, double score) {
  }

  /**
   * Ranks brands against a piece of text, which may be a search box query or a
   * line read off a pharmacy bill.
   *
   * Jaro-Winkler is the right measure here because it rewards a matching prefix:
   * OCR mangles the tail of a word far more often than its beginning, so
   * "ECOSPFLN 75" still lands on "Ecosprin 75". The same comparison runs against
   * every salt name and synonym, so typing "paracetamol" finds Dolo and Crocin
   * even though neither brand name contains the word.
   */
  public List<Match> search(String text, int limit) {
    String query = cleanOcrLine(text);
    if (query.isBlank()) {
      query = text == null ? "" : text.trim();
    }
    if (query.length() < 2) {
      return List.of();
    }
    List<String> candidates = candidateQueries(query);

    List<Match> ranked = new ArrayList<>();
    for (Medicine m : snapshot().medicines().values()) {
      double best = 0;
      for (String q : candidates) {
        best = Math.max(best, similarity(q, m.brand));
        for (MedicineSalt ms : m.composition) {
          double saltScore = similarity(q, ms.salt.name);
          for (String syn : synonyms(ms.salt.id)) {
            saltScore = Math.max(saltScore, similarity(q, syn));
          }
          best = Math.max(best, saltScore * SALT_MATCH_WEIGHT);
        }
      }
      if (best > SEARCH_FLOOR) {
        ranked.add(new Match(m, best));
      }
    }
    ranked.sort(Comparator.comparingDouble(Match::score).reversed());
    return ranked.size() > limit ? List.copyOf(ranked.subList(0, limit)) : List.copyOf(ranked);
  }

  /**
   * A bill line carries more than the brand: "ECOSPRIN 75 Aspirin Tablets IP"
   * is one line, and comparing all of it against "Ecosprin 75" scores badly
   * because of everything after the brand. The brand is almost always at the
   * start, so the first few words are tried as well as the whole line and the
   * best score wins.
   */
  private static List<String> candidateQueries(String cleaned) {
    String upper = cleaned.toUpperCase(Locale.ROOT);
    List<String> out = new ArrayList<>();
    out.add(upper);
    String[] words = upper.split(" ");
    for (int take = Math.min(3, words.length); take >= 1; take--) {
      String prefix = String.join(" ", Arrays.copyOfRange(words, 0, take));
      if (prefix.length() >= 3 && !out.contains(prefix)) {
        out.add(prefix);
      }
    }
    return out;
  }

  private static double similarity(String upperQuery, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return 0;
    }
    Double score = JARO.apply(upperQuery, candidate.toUpperCase(Locale.ROOT));
    return score == null ? 0 : score;
  }

  /**
   * Strips everything off a bill line that is not a medicine name: prices, pack
   * sizes, batch numbers, expiry dates and dosage words. What is left is the
   * brand, which is what the matcher is given.
   */
  public static String cleanOcrLine(String line) {
    if (line == null) {
      return "";
    }
    return line
        .replaceAll("(?i)B\\.?\\s?No\\.?\\s*[A-Z0-9]+", " ")
        .replaceAll("(?i)EXP\\.?\\s*\\d{1,2}\\s*[/-]\\s*\\d{2,4}", " ")
        .replaceAll("(?i)\\b\\d+\\s*N\\b", " ")
        .replaceAll("(?i)(?:rs\\.?|₹)\\s*[\\d.,]+", " ")
        // A bare two-decimal number at the end of the line is the price. The
        // size test is what keeps it from eating a strength: "Alprax 0.25" is a
        // brand name whose number is the only thing telling it apart from
        // "Alprax 0.5", while a strip price is rarely under five rupees. Erring
        // towards keeping the number costs little, because the matcher already
        // scores the first few words of the line as well as the whole of it.
        .replaceAll("\\s(?:[1-9]\\d+|[5-9])\\.\\d{2}\\s*$", " ")
        .replaceAll("(?i)\\b(tab|tabs|tablet|tablets|cap|caps|capsule|strip|mg|ml)\\b", " ")
        .replaceAll("[^A-Za-z0-9 .-]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /** Salt ids of a medicine, sorted, as the generics matcher keys on. */
  public static String saltIdKey(Medicine m) {
    Set<String> ids = new TreeSet<>();
    for (MedicineSalt ms : m.composition) {
      ids.add(String.valueOf(ms.salt.id));
    }
    return String.join("|", ids);
  }

  /** Salt ids with milligram strengths, sorted: the exact-match key. */
  public static String saltStrengthKey(Medicine m) {
    Set<String> parts = new TreeSet<>();
    for (MedicineSalt ms : m.composition) {
      parts.add(ms.salt.id + "@" + Strengths.parse(ms.strength).value());
    }
    return String.join("|", parts);
  }
}
