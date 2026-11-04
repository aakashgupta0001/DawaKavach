package com.dawacheck.service;

import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.Scan;
import com.dawacheck.domain.ScanItem;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.CabinetEntryRepository;
import com.dawacheck.repo.ScanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Module 3, the medicine scanner.
 *
 * Three things happen here and the order matters. The photo is read, each line
 * is matched to the brands it might be, and then nothing at all happens until
 * the user has confirmed every line. That last part is the whole point: OCR on a
 * phone photo of an Indian pharmacy bill is wrong often enough that saving its
 * output unseen would fill the cabinet with medicines nobody is taking, and the
 * safety check would then be built on fiction. Section 14 requires the
 * confirmation step, and this service has no path around it.
 */
@Service
public class ScanService {

  private static final Logger log = LoggerFactory.getLogger(ScanService.class);

  /** Below this match score the user is asked to type the name instead. */
  public static final double MATCH_CONFIDENCE_FLOOR = 0.62;

  /**
   * Below this OCR confidence the line is flagged however well it matches.
   *
   * A handwritten slip read at 0.34 can still score highly against a brand,
   * because Jaro-Winkler compares what was read rather than what was written. If
   * the characters themselves are not trusted, a confident match on them is not
   * evidence of anything, so the user is asked either way.
   */
  public static final double OCR_CONFIDENCE_FLOOR = 0.5;

  private static final int SUGGESTIONS_PER_LINE = 3;

  /** An unconfirmed upload is not kept beyond this. */
  private static final int UNCONFIRMED_HOURS = 24;

  /**
   * Lines that are part of the bill rather than part of the medicine list.
   *
   * Without this the store name and the GST number become rows the family is
   * asked to confirm, and a shop called Shree Medical scores well enough against
   * Shelcal for the matcher to offer it.
   */
  private static final Pattern FURNITURE = Pattern.compile(
      "(?i)\\b(gst|gstin|bill\\s*no|invoice|total|sub\\s*total|paid|cash|upi|card|change|"
          + "customer|patient|mob|phone|ph\\s*no|dl\\s*no|address|stores?|pharmacy|medicals?|"
          + "chemist|thank|signature|regd|licence|license)\\b");

  private final ScanRepository scans;
  private final CabinetEntryRepository entries;
  private final MemberService memberService;
  private final CabinetService cabinetService;
  private final SafetyCheckService safety;
  private final OcrService ocr;
  private final KnowledgeBase kb;
  private final MedicineService medicines;
  private final ViewMapper view;
  private final CurrentUser currentUser;
  private final ObjectMapper json;

  public ScanService(ScanRepository scans, CabinetEntryRepository entries,
      MemberService memberService, CabinetService cabinetService, SafetyCheckService safety,
      OcrService ocr, KnowledgeBase kb, MedicineService medicines, ViewMapper view,
      CurrentUser currentUser, ObjectMapper json) {
    this.scans = scans;
    this.entries = entries;
    this.memberService = memberService;
    this.cabinetService = cabinetService;
    this.safety = safety;
    this.ocr = ocr;
    this.kb = kb;
    this.medicines = medicines;
    this.view = view;
    this.currentUser = currentUser;
    this.json = json;
  }

  /* ================= step 1: read ================= */

  @Transactional
  public Dto.ScanView create(String memberApiId, MultipartFile image, Integer sampleIndex) {
    FamilyMember member = memberService.require(memberApiId);

    List<OcrService.Line> lines;
    String rawText;
    String engine;
    String sampleLabel = null;

    boolean hasImage = image != null && !image.isEmpty();
    if (hasImage) {
      OcrService.Result result = ocr.read(bytes(image));
      lines = result.lines();
      rawText = result.rawText();
      engine = result.engine();
    } else if (sampleIndex != null) {
      OcrSamples.Sample sample = OcrSamples.at(sampleIndex);
      lines = sample.lines().stream()
          .map(l -> new OcrService.Line(l.text(), l.confidence()))
          .toList();
      rawText = sample.rawText();
      engine = "sample bill (no photo read)";
      sampleLabel = sample.label();
    } else {
      throw new BadRequestException("Choose a photo of the bill or the medicine box.");
    }

    Scan scan = new Scan();
    scan.member = member;
    scan.status = Scan.AWAITING;
    scan.imageName = hasImage ? image.getOriginalFilename() : sampleLabel;
    scan.imageBytes = hasImage ? bytes(image) : null;
    scan.rawText = rawText;
    scan.fixtureLabel = sampleLabel;
    scan.ocrEngine = engine;
    scan.createdAt = Instant.now();

    for (OcrService.Line line : lines) {
      String cleaned = KnowledgeBase.cleanOcrLine(line.text());
      if (!looksLikeMedicine(line.text(), cleaned)) {
        continue;
      }
      List<Dto.MedicineView> suggestions = suggest(cleaned);
      if (suggestions.isEmpty()) {
        // Nothing in the catalogue came close. The line is still shown, so the
        // user can type the name, and it is also what the admin mapping queue
        // reads to find brands worth importing.
        log.debug("No brand match for scanned line: {}", cleaned);
      }
      ScanItem item = new ScanItem();
      item.scan = scan;
      item.detectedText = line.text();
      item.cleanedText = cleaned;
      item.ocrConfidence = line.confidence();
      item.suggestionsJson = writeSuggestions(suggestions);
      scan.items.add(item);
    }

    if (scan.items.isEmpty()) {
      throw new BadRequestException(
          "No medicine names could be picked out of that photo. Try again with the medicine "
              + "list in the frame, or add the medicine by name instead.");
    }

    scans.save(scan);
    return viewOf(scan);
  }

  private List<Dto.MedicineView> suggest(String cleaned) {
    List<Dto.MedicineView> out = new ArrayList<>();
    for (KnowledgeBase.Match match : kb.search(cleaned, SUGGESTIONS_PER_LINE)) {
      out.add(view.medicine(match.medicine()).withConfidence(match.score()));
    }
    return out;
  }

  private static final Pattern HAS_WORD = Pattern.compile("[A-Za-z]{3}");

  /**
   * Keeps a line if it could be a medicine: it has a run of letters and it is
   * not part of the bill's own furniture.
   *
   * Deliberately not a test of whether the catalogue recognises it. A line the
   * matcher cannot place is exactly the one worth showing, because the brand may
   * be missing from the catalogue rather than from the bill, and the user can
   * type the name. Those lines are also what the admin mapping queue reads.
   */
  private boolean looksLikeMedicine(String original, String cleaned) {
    if (cleaned.length() < 3) {
      return false;
    }
    if (!HAS_WORD.matcher(cleaned).find()) {
      return false;
    }
    return !FURNITURE.matcher(original).find();
  }

  /* ================= step 2: the user confirms ================= */

  @Transactional(readOnly = true)
  public Dto.ScanView get(String scanApiId) {
    return viewOf(require(scanApiId));
  }

  /**
   * Saves the lines the user confirmed and runs the safety check over the result.
   *
   * A line the user skipped is not saved. A line with no medicine chosen is not
   * saved either, whatever the matcher thought of it, because an unanswered line
   * is not a confirmation.
   */
  @Transactional
  public Dto.ConfirmResult confirm(String scanApiId, Dto.ConfirmRequest req) {
    Scan scan = require(scanApiId);
    if (Scan.CONFIRMED.equals(scan.status)) {
      throw new BadRequestException("This scan has already been saved.");
    }

    Map<String, ScanItem> byId = new HashMap<>();
    for (ScanItem item : scan.items) {
      byId.put(Ids.encode(Ids.SCAN_ITEM, item.id), item);
    }

    List<Dto.CabinetView> added = new ArrayList<>();
    for (Dto.ConfirmItem choice : req.items() == null ? List.<Dto.ConfirmItem>of() : req.items()) {
      ScanItem item = byId.get(choice.itemId());
      if (item == null) {
        throw new BadRequestException("That scanned line does not belong to this scan.");
      }
      if (choice.skip() || choice.medicineId() == null || choice.medicineId().isBlank()) {
        continue;
      }
      CabinetEntry entry = new CabinetEntry();
      entry.member = scan.member;
      entry.medicine = kb.requireMedicine(Ids.decode(Ids.MEDICINE, choice.medicineId()));
      entry.schedule = choice.schedule() == null || choice.schedule().isBlank()
          ? "As advised" : choice.schedule().trim();
      entry.dosesPerDay = choice.dosesPerDay() == null ? 1 : Math.max(1, choice.dosesPerDay());
      entry.prescribedBy = choice.prescribedBy() == null || choice.prescribedBy().isBlank()
          ? "Not recorded" : choice.prescribedBy().trim();
      entry.startDate = LocalDate.now();
      entry.active = true;
      entry.fromScanId = scan.id;
      entries.save(entry);

      item.confirmed = true;
      item.matchedMedicineId = entry.medicine.id;
      added.add(view.entry(entry));
    }

    scan.status = Scan.CONFIRMED;
    scan.imageRetained = req.keepImage();
    // The photo is deleted once it has been confirmed, unless the user asked to
    // keep it (section 14). The text that was read stays, because that is what
    // the confirmed medicines were derived from.
    if (!req.keepImage()) {
      scan.imageBytes = null;
    }
    scans.save(scan);

    List<Dto.AlertView> alerts =
        safety.checkAndRecord(scan.member, cabinetService.rows(scan.member));
    return new Dto.ConfirmResult(added, alerts);
  }

  /* ================= samples and cleanup ================= */

  public List<Dto.OcrFixtureRow> samples() {
    // Offered when the photo cannot be read on this machine. With Tesseract
    // configured the scanner reads the real upload and these are not needed.
    if (ocr.available()) {
      return List.of();
    }
    List<Dto.OcrFixtureRow> out = new ArrayList<>();
    List<OcrSamples.Sample> all = OcrSamples.all();
    for (int i = 0; i < all.size(); i++) {
      out.add(new Dto.OcrFixtureRow(i, all.get(i).label(), all.get(i).lines().size()));
    }
    return out;
  }

  /**
   * Clears uploads that were never confirmed.
   *
   * Someone who starts a scan and closes the tab has left a photo of a
   * prescription on the server. Nothing uses it, so it goes.
   */
  @Scheduled(cron = "0 15 3 * * *")
  @Transactional
  public void purgeUnconfirmedUploads() {
    Instant cutoff = Instant.now().minus(UNCONFIRMED_HOURS, ChronoUnit.HOURS);
    List<Scan> stale = scans.findByStatusAndCreatedAtBefore(Scan.AWAITING, cutoff);
    if (stale.isEmpty()) {
      return;
    }
    scans.deleteAll(stale);
    log.info("Removed {} scan(s) that were uploaded but never confirmed", stale.size());
  }

  /* ================= helpers ================= */

  private Scan require(String scanApiId) {
    long id = Ids.decode(Ids.SCAN, scanApiId);
    return scans.findByIdAndMemberOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new NotFoundException("Scan not found or already cleared."));
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new BadRequestException("That photo could not be read from the upload.");
    }
  }

  private String writeSuggestions(List<Dto.MedicineView> suggestions) {
    try {
      return json.writeValueAsString(suggestions);
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("Could not store brand suggestions for a scanned line: {}", e.getMessage());
      return "[]";
    }
  }

  private List<Dto.MedicineView> readSuggestions(ScanItem item) {
    try {
      return json.readValue(item.suggestionsJson, new TypeReference<List<Dto.MedicineView>>() {
      });
    } catch (RuntimeException | IOException e) {
      // Fall back to matching again rather than showing the line with no guesses.
      return suggest(item.cleanedText);
    }
  }

  private Dto.ScanView viewOf(Scan scan) {
    List<Dto.ScanItemView> items = new ArrayList<>();
    for (ScanItem item : scan.items) {
      List<Dto.MedicineView> suggestions = readSuggestions(item);
      double top = suggestions.isEmpty() || suggestions.get(0).confidence() == null
          ? 0 : suggestions.get(0).confidence();
      items.add(new Dto.ScanItemView(
          Ids.encode(Ids.SCAN_ITEM, item.id),
          item.detectedText,
          item.cleanedText,
          item.ocrConfidence,
          top < MATCH_CONFIDENCE_FLOOR || item.ocrConfidence < OCR_CONFIDENCE_FLOOR,
          suggestions,
          item.confirmed,
          Ids.encode(Ids.MEDICINE, item.matchedMedicineId)));
    }
    return new Dto.ScanView(
        Ids.encode(Ids.SCAN, scan.id),
        Ids.encode(Ids.MEMBER, scan.member.id),
        scan.status,
        scan.createdAt.toString(),
        scan.imageName,
        scan.rawText,
        scan.fixtureLabel,
        scan.ocrEngine,
        ocr.available(),
        scan.imageRetained,
        items);
  }

  /** Lines the catalogue could not place, for the admin mapping queue. */
  @Transactional(readOnly = true)
  public List<ScanItem> unresolvedItems() {
    List<ScanItem> out = new ArrayList<>();
    for (Scan scan : scans.findAll()) {
      for (ScanItem item : scan.items) {
        if (item.matchedMedicineId == null) {
          out.add(item);
        }
      }
    }
    return out;
  }

  static String normalise(String text) {
    return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
  }
}
