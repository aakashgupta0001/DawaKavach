package com.dawacheck.service;

import com.dawacheck.domain.GenericProduct;
import com.dawacheck.domain.Interaction;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.domain.Salt;
import com.dawacheck.domain.SaltSynonym;
import com.dawacheck.domain.ScanItem;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.GenericProductRepository;
import com.dawacheck.repo.InteractionRepository;
import com.dawacheck.repo.MedicineRepository;
import com.dawacheck.repo.SaltRepository;
import com.dawacheck.repo.SaltSynonymRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Module 2 from the admin side: loading the knowledge base and fixing what the
 * matcher could not place.
 *
 * The public datasets are not clean. A brand row may have an empty composition,
 * a salt name the synonym table has never seen, or a price in a column that
 * usually holds a pack size. Rows like that are skipped and counted rather than
 * guessed at, because a brand mapped to the wrong salt is worse than a brand
 * that is missing: a missing brand makes the scanner ask the user, while a wrong
 * one makes every later check wrong without anyone noticing.
 */
@Service
public class AdminService {

  private static final Logger log = LoggerFactory.getLogger(AdminService.class);

  /** Enough of a CSV to be worth reporting a problem about, rather than all of it. */
  private static final int MAX_WARNINGS = 12;

  private final SaltRepository salts;
  private final SaltSynonymRepository synonyms;
  private final MedicineRepository medicines;
  private final InteractionRepository interactions;
  private final GenericProductRepository generics;
  private final ScanService scanService;
  private final KnowledgeBase kb;
  private final ViewMapper view;
  private final RiskPredictionService risk;

  public AdminService(SaltRepository salts, SaltSynonymRepository synonyms,
      MedicineRepository medicines, InteractionRepository interactions,
      GenericProductRepository generics, ScanService scanService, KnowledgeBase kb,
      ViewMapper view, RiskPredictionService risk) {
    this.salts = salts;
    this.synonyms = synonyms;
    this.medicines = medicines;
    this.interactions = interactions;
    this.generics = generics;
    this.scanService = scanService;
    this.kb = kb;
    this.view = view;
    this.risk = risk;
  }

  /* ================= overview ================= */

  @Transactional(readOnly = true)
  public Dto.AdminOverview overview() {
    long saltCount = salts.count();
    long synonymCount = synonyms.count();
    long medicineCount = medicines.count();
    long interactionCount = interactions.count();
    long genericCount = generics.count();

    // Row counts come from the tables themselves rather than from an import log,
    // so this panel always describes what is actually loaded.
    List<Dto.DatasetRow> datasets = List.of(
        new Dto.DatasetRow(Ids.encode(Ids.DATASET, 1L), "Indian medicine brands",
            medicineCount, null, "medicines + medicine_salts"),
        new Dto.DatasetRow(Ids.encode(Ids.DATASET, 2L), "DDInter 2.0 interactions",
            interactionCount, null, "interactions"),
        new Dto.DatasetRow(Ids.encode(Ids.DATASET, 3L), "Jan Aushadhi product list",
            genericCount, null, "generic_products"),
        new Dto.DatasetRow(Ids.encode(Ids.DATASET, 4L), "Salt synonyms",
            synonymCount, null, "salt_synonyms"));

    return new Dto.AdminOverview(
        datasets,
        new Dto.Counts(medicineCount, saltCount, synonymCount, interactionCount, genericCount),
        mappingQueue(),
        new Dto.ModelCard(
            risk.usingTrainedModel() ? "FastAPI POST /predict" : "built-in heuristic",
            RiskPredictionService.THRESHOLD,
            "Morgan fingerprints (PubChem SMILES) + ATC class",
            "ROC-AUC >= 0.85",
            risk.usingTrainedModel()));
  }

  /**
   * Lines the scanner read but could not place, grouped by the cleaned text and
   * counted.
   *
   * This is the list worth working through: a line that twelve families have
   * scanned is a brand missing from the catalogue, and importing it fixes all
   * twelve at once. The guess shown beside it is the matcher's best effort, which
   * is often right and is never applied without someone agreeing to it.
   */
  @Transactional(readOnly = true)
  public List<Dto.MappingRow> mappingQueue() {
    Map<String, List<ScanItem>> grouped = new LinkedHashMap<>();
    for (ScanItem item : scanService.unresolvedItems()) {
      grouped.computeIfAbsent(ScanService.normalise(item.cleanedText), k -> new ArrayList<>())
          .add(item);
    }

    List<Dto.MappingRow> rows = new ArrayList<>();
    grouped.forEach((text, items) -> {
      ScanItem first = items.get(0);
      List<KnowledgeBase.Match> matches = kb.search(first.cleanedText, 1);
      Dto.MedicineView guess = matches.isEmpty() ? null
          : view.medicine(matches.get(0).medicine()).withConfidence(matches.get(0).score());
      String note;
      if (matches.isEmpty()) {
        note = "Nothing in the catalogue came close; the brand may be missing.";
      } else {
        int score = (int) Math.round(matches.get(0).score() * 100);
        int floor = (int) Math.round(ScanService.MATCH_CONFIDENCE_FLOOR * 100);
        note = score < floor
            ? "Best guess scored %d%%, below the %d%% a line needs before it is offered as the answer."
                .formatted(score, floor)
            : "Best guess scored %d%%. Nobody has confirmed it yet, so these lines are still unmapped."
                .formatted(score);
      }
      rows.add(new Dto.MappingRow(
          Ids.encode(Ids.QUEUE, first.id),
          first.detectedText,
          guess == null ? null : guess.id(),
          items.size(),
          note,
          guess));
    });
    rows.sort((a, b) -> Long.compare(b.count(), a.count()));
    return rows;
  }

  /**
   * Accepts the admin's decision for one queued line and applies it to every
   * line that read the same way.
   */
  @Transactional
  public void resolveMapping(String queueApiId, String medicineApiId) {
    long anchorId = Ids.decode(Ids.QUEUE, queueApiId);
    Medicine medicine = kb.requireMedicine(Ids.decode(Ids.MEDICINE, medicineApiId));

    List<ScanItem> unresolved = scanService.unresolvedItems();
    ScanItem anchor = unresolved.stream().filter(i -> i.id.equals(anchorId)).findFirst()
        .orElseThrow(() -> new NotFoundException("That queued line has already been resolved."));

    String key = ScanService.normalise(anchor.cleanedText);
    int applied = 0;
    for (ScanItem item : unresolved) {
      if (key.equals(ScanService.normalise(item.cleanedText))) {
        item.matchedMedicineId = medicine.id;
        applied++;
      }
    }
    log.info("Mapped {} scanned line(s) reading \"{}\" to {}", applied, anchor.cleanedText,
        medicine.brand);
  }

  /* ================= CSV import ================= */

  /**
   * Imports one of the public datasets.
   *
   * The expected columns per target, with a header row:
   *
   * <pre>
   *   salts         name, atc, classes, daily_ceiling_mg
   *   synonyms      salt, synonym
   *   medicines     brand, maker, form, pack, price, composition
   *   interactions  salt_a, salt_b, severity, mechanism, mechanism_hi
   *   generics      name, composition, form, pack, mrp
   * </pre>
   *
   * A composition is written the way a box writes it: {@code Ibuprofen 400 mg +
   * Paracetamol 325 mg}. Salts are looked up by name and then by synonym, and a
   * row naming a salt that is in neither is skipped rather than creating one,
   * because a typo would otherwise become a new salt that nothing interacts with.
   */
  @Transactional
  public Dto.ImportResult importCsv(MultipartFile file, String target) {
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("Choose a CSV file to import.");
    }
    String name = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
    if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new BadRequestException("Only .csv files can be imported.");
    }
    String what = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);

    List<String> warnings = new ArrayList<>();
    long read = 0;
    long imported = 0;

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new BadRequestException("That file is empty.");
      }
      Map<String, Integer> columns = header(headerLine);

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        read++;
        List<String> cells = splitCsv(line);
        try {
          boolean ok = switch (what) {
            case "salts" -> importSalt(columns, cells);
            case "synonyms", "salt_synonyms" -> importSynonym(columns, cells);
            case "medicines", "medicines + medicine_salts" -> importMedicine(columns, cells);
            case "interactions" -> importInteraction(columns, cells);
            case "generics", "generic_products" -> importGeneric(columns, cells);
            default -> throw new BadRequestException(
                "Unknown import target: " + target + ". Expected salts, synonyms, medicines, "
                    + "interactions or generics.");
          };
          if (ok) {
            imported++;
          } else if (warnings.size() < MAX_WARNINGS) {
            warnings.add("Row %d skipped: a required column was empty or unreadable."
                .formatted(read));
          }
        } catch (BadRequestException e) {
          throw e;
        } catch (RuntimeException e) {
          if (warnings.size() < MAX_WARNINGS) {
            warnings.add("Row %d skipped: %s".formatted(read, e.getMessage()));
          }
        }
      }
    } catch (IOException e) {
      throw new BadRequestException("That file could not be read. Please upload it again.");
    }

    long skipped = read - imported;
    if (skipped > 0 && warnings.size() >= MAX_WARNINGS) {
      warnings.add("%d rows were skipped in total; only the first %d are listed."
          .formatted(skipped, MAX_WARNINGS));
    }
    kb.reload();
    log.info("Imported {} of {} rows from {} into {}", imported, read, name, what);
    return new Dto.ImportResult(what, name, read, imported, skipped, warnings,
        Instant.now().toString());
  }

  private boolean importSalt(Map<String, Integer> columns, List<String> cells) {
    String name = cell(columns, cells, "name");
    if (name.isBlank()) {
      return false;
    }
    Salt salt = salts.findByNameIgnoreCase(name).orElseGet(Salt::new);
    salt.name = name;
    salt.atc = cell(columns, cells, "atc");
    salt.drugClasses = cell(columns, cells, "classes").replace(';', ',');
    String ceiling = cell(columns, cells, "daily_ceiling_mg");
    salt.dailyCeilingMg = ceiling.isBlank() ? null : Double.valueOf(ceiling);
    salts.save(salt);
    return true;
  }

  private boolean importSynonym(Map<String, Integer> columns, List<String> cells) {
    String saltName = cell(columns, cells, "salt");
    String synonym = cell(columns, cells, "synonym");
    if (saltName.isBlank() || synonym.isBlank()) {
      return false;
    }
    Salt salt = salts.findByNameIgnoreCase(saltName)
        .orElseThrow(() -> new IllegalArgumentException("no salt named " + saltName));
    boolean exists = synonyms.findBySaltId(salt.id).stream()
        .anyMatch(s -> s.synonym.equalsIgnoreCase(synonym));
    if (exists) {
      return false;
    }
    SaltSynonym row = new SaltSynonym();
    row.salt = salt;
    row.synonym = synonym;
    synonyms.save(row);
    return true;
  }

  private boolean importMedicine(Map<String, Integer> columns, List<String> cells) {
    String brand = cell(columns, cells, "brand");
    String composition = cell(columns, cells, "composition");
    if (brand.isBlank() || composition.isBlank()) {
      return false;
    }
    Medicine medicine = medicines.findByBrandIgnoreCase(brand).orElseGet(Medicine::new);
    medicine.brand = brand;
    medicine.maker = cell(columns, cells, "maker");
    medicine.form = orElse(cell(columns, cells, "form"), "Tablet");
    medicine.pack = cell(columns, cells, "pack");
    String price = cell(columns, cells, "price");
    medicine.price = price.isBlank() ? null : Double.valueOf(price);

    List<MedicineSalt> parsed = parseComposition(composition, medicine);
    if (parsed.isEmpty()) {
      return false;
    }
    medicine.composition.clear();
    medicine.composition.addAll(parsed);
    medicines.save(medicine);
    return true;
  }

  private boolean importInteraction(Map<String, Integer> columns, List<String> cells) {
    Salt a = findSalt(cell(columns, cells, "salt_a"));
    Salt b = findSalt(cell(columns, cells, "salt_b"));
    String severity = cell(columns, cells, "severity").toLowerCase(Locale.ROOT);
    if (a == null || b == null || a.id.equals(b.id)) {
      return false;
    }
    if (!List.of("major", "moderate", "minor").contains(severity)) {
      throw new IllegalArgumentException("severity must be major, moderate or minor");
    }
    long lo = Math.min(a.id, b.id);
    long hi = Math.max(a.id, b.id);
    Interaction row = interactions.findBySaltAIdAndSaltBId(lo, hi).orElseGet(Interaction::new);
    row.saltAId = lo;
    row.saltBId = hi;
    row.severity = severity;
    row.mechanism = cell(columns, cells, "mechanism");
    String mechanismHi = cell(columns, cells, "mechanism_hi");
    if (!mechanismHi.isBlank()) {
      row.mechanismHi = mechanismHi;
    }
    interactions.save(row);
    return true;
  }

  private boolean importGeneric(Map<String, Integer> columns, List<String> cells) {
    String name = cell(columns, cells, "name");
    String composition = cell(columns, cells, "composition");
    if (name.isBlank() || composition.isBlank()) {
      return false;
    }
    GenericProduct g = new GenericProduct();
    g.name = name;
    g.composition = composition;
    g.form = orElse(cell(columns, cells, "form"), "Tablet");
    g.pack = cell(columns, cells, "pack");
    String mrp = cell(columns, cells, "mrp");
    g.mrp = mrp.isBlank() ? null : Double.valueOf(mrp);

    Map<Long, String> resolved = resolveComposition(composition);
    if (resolved.isEmpty()) {
      return false;
    }
    applyGenericKeys(g, resolved);
    generics.save(g);
    return true;
  }

  /** Builds the two match keys a generic is found by. */
  public static void applyGenericKeys(GenericProduct g, Map<Long, String> saltToStrength) {
    TreeSet<String> ids = new TreeSet<>();
    TreeSet<String> withStrength = new TreeSet<>();
    saltToStrength.forEach((saltId, strength) -> {
      ids.add(String.valueOf(saltId));
      withStrength.add(saltId + "@" + Strengths.parse(strength).value());
    });
    g.saltIds = String.join("|", ids);
    g.saltKey = String.join("|", withStrength);
  }

  /* ================= composition parsing ================= */

  private List<MedicineSalt> parseComposition(String composition, Medicine owner) {
    List<MedicineSalt> out = new ArrayList<>();
    for (Map.Entry<Long, String> entry : resolveComposition(composition).entrySet()) {
      MedicineSalt ms = new MedicineSalt();
      ms.medicine = owner;
      ms.salt = salts.findById(entry.getKey()).orElseThrow();
      ms.strength = entry.getValue();
      out.add(ms);
    }
    return out;
  }

  /**
   * Reads "Ibuprofen 400 mg + Paracetamol 325 mg" into salt ids and strengths.
   *
   * The separator varies across datasets, so {@code +}, {@code &} and a comma
   * are all accepted. Within a part the trailing number and unit are the
   * strength and everything before it is the salt name.
   */
  private Map<Long, String> resolveComposition(String composition) {
    Map<Long, String> out = new LinkedHashMap<>();
    for (String part : composition.split("[+&,]")) {
      String piece = part.trim();
      if (piece.isEmpty()) {
        continue;
      }
      java.util.regex.Matcher m = java.util.regex.Pattern
          .compile("^(.*?)[\\s(]*([0-9]+(?:\\.[0-9]+)?\\s*(?:mg|mcg|g|iu|IU)?)[\\s)]*$")
          .matcher(piece);
      String saltName = m.matches() ? m.group(1).trim() : piece;
      String strength = m.matches() ? m.group(2).trim() : "";
      Salt salt = findSalt(saltName);
      if (salt == null) {
        throw new IllegalArgumentException("unknown salt \"" + saltName + "\"");
      }
      out.put(salt.id, strength.isBlank() ? "as labelled" : normaliseStrength(strength));
    }
    return out;
  }

  private static String normaliseStrength(String strength) {
    String compact = strength.replaceAll("\\s+", "");
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("([0-9.]+)([a-zA-Z]*)").matcher(compact);
    if (!m.matches()) {
      return strength;
    }
    String unit = m.group(2).isBlank() ? "mg" : m.group(2);
    return m.group(1) + " " + ("IU".equalsIgnoreCase(unit) ? "IU" : unit.toLowerCase(Locale.ROOT));
  }

  /** By name, then by synonym. Nothing is created on a miss. */
  private Salt findSalt(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String wanted = name.trim();
    Optional<Salt> direct = salts.findByNameIgnoreCase(wanted);
    if (direct.isPresent()) {
      return direct.get();
    }
    for (SaltSynonym synonym : synonyms.findAll()) {
      if (synonym.synonym.equalsIgnoreCase(wanted)) {
        return synonym.salt;
      }
    }
    return null;
  }

  /* ================= CSV plumbing ================= */

  private static Map<String, Integer> header(String line) {
    Map<String, Integer> columns = new HashMap<>();
    List<String> cells = splitCsv(line);
    for (int i = 0; i < cells.size(); i++) {
      columns.put(cells.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'), i);
    }
    return columns;
  }

  private static String cell(Map<String, Integer> columns, List<String> cells, String name) {
    Integer index = columns.get(name);
    if (index == null || index >= cells.size()) {
      return "";
    }
    return cells.get(index).trim();
  }

  /**
   * Splits one CSV line, honouring double quotes.
   *
   * A mechanism sentence contains commas, so a plain {@code split(",")} would
   * tear it into several columns and quietly shift everything after it.
   */
  static List<String> splitCsv(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (c == ',' && !quoted) {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    cells.add(current.toString());
    return cells;
  }

  private static String orElse(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
