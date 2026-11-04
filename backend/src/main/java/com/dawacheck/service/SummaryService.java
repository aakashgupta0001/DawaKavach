package com.dawacheck.service;

import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.dto.Dto;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 9, the doctor summary.
 *
 * One page the family can hand over at the clinic: who the member is, every
 * medicine with its salts and schedule, and what the safety check found, with
 * the questions to ask. It exists because the conversation it is meant to start
 * is the only place any of this gets acted on, and a phone screen passed across
 * a desk is not that.
 *
 * The PDF is built with OpenPDF, as section 11 specifies. The browser also
 * prints the summary tab through {@code frontend/css/print.css}; this is the
 * version that can be emailed or kept.
 */
@Service
public class SummaryService {

  private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm").withZone(ZONE);

  private static final String DISCLAIMER_EN =
      "This report is for information only. Any decision to start, stop or change a medicine is "
          + "the doctor's.";
  private static final String DISCLAIMER_HI =
      "यह रिपोर्ट जानकारी के लिए है। कोई भी दवा शुरू, बंद या बदलने का फ़ैसला डॉक्टर ही करेंगे।";

  /**
   * Fonts that can set Devanagari. The PDF base fourteen cannot, so a Hindi
   * summary needs a TrueType font embedded in the file. Nirmala UI ships with
   * Windows and the Noto paths cover a Linux server.
   */
  private static final List<String> DEVANAGARI_FONTS = List.of(
      // Windows ships Nirmala UI as a TrueType collection, so it needs the face
      // index. Windows 10 and 11 use the .ttc; older builds had a plain .ttf.
      "C:/Windows/Fonts/Nirmala.ttc,0",
      "C:/Windows/Fonts/Nirmala.ttf",
      "C:/Windows/Fonts/mangal.ttf",
      "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf",
      "/usr/share/fonts/truetype/lohit-devanagari/Lohit-Devanagari.ttf",
      "/System/Library/Fonts/Supplemental/DevanagariMT.ttc,0");

  private final MemberService memberService;
  private final CabinetService cabinetService;
  private final SafetyCheckService safety;
  private final ViewMapper view;

  public SummaryService(MemberService memberService, CabinetService cabinetService,
      SafetyCheckService safety, ViewMapper view) {
    this.memberService = memberService;
    this.cabinetService = cabinetService;
    this.safety = safety;
    this.view = view;
  }

  @Transactional(readOnly = true)
  public Dto.SummaryView summary(String memberApiId, String lang) {
    FamilyMember member = memberService.require(memberApiId);
    List<CabinetEntry> rows = cabinetService.rows(member);
    List<Dto.CabinetView> entries = new ArrayList<>();
    for (CabinetEntry e : rows) {
      if (e.active) {
        entries.add(view.entry(e));
      }
    }
    List<Dto.AlertView> alerts = safety.check(member, rows);
    return new Dto.SummaryView(memberService.withCounts(member), entries, alerts,
        Instant.now().toString(), "hi".equals(lang) ? DISCLAIMER_HI : DISCLAIMER_EN);
  }

  /* ================= the PDF ================= */

  @Transactional(readOnly = true)
  public byte[] pdf(String memberApiId, String requestedLang) {
    Dto.SummaryView s = summary(memberApiId, requestedLang);
    Fonts fonts = fonts(requestedLang);
    String lang = fonts.lang();

    Document document = new Document(PageSize.A4, 42, 42, 40, 40);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(document, out);
      document.addTitle("DawaCheck summary: " + s.member().name());
      document.addCreator("DawaCheck");
      document.open();

      document.add(paragraph(hi(lang) ? "DawaCheck — दवाओं का सारांश"
          : "DawaCheck — medicine summary", fonts.h1()));
      document.add(spacer(4));

      String who = "%s · %s, %d".formatted(s.member().name(),
          blankTo(s.member().relation(), hi(lang) ? "परिवार" : "Family"), s.member().age());
      document.add(paragraph(who, fonts.h2()));
      if (!s.member().allergyNames().isEmpty()) {
        document.add(paragraph((hi(lang) ? "दर्ज एलर्जी: " : "Recorded allergy: ")
            + String.join(", ", s.member().allergyNames()), fonts.small()));
      }
      document.add(paragraph((hi(lang) ? "तैयार: " : "Prepared: ") + STAMP.format(Instant.now()),
          fonts.faint()));
      document.add(spacer(12));

      document.add(paragraph(hi(lang) ? "अभी चल रही दवाएँ" : "Medicines being taken now",
          fonts.h2()));
      document.add(spacer(6));
      document.add(medicineTable(s, fonts, lang));
      document.add(spacer(14));

      document.add(paragraph(hi(lang) ? "जाँच में क्या मिला" : "What the check found", fonts.h2()));
      document.add(spacer(6));
      if (s.alerts().isEmpty()) {
        document.add(paragraph(hi(lang)
            ? "इस सूची में कोई चेतावनी नहीं मिली।"
            : "Nothing on this list raised a warning.", fonts.body()));
      } else {
        for (Dto.AlertView a : s.alerts()) {
          document.add(alertBlock(a, fonts, lang));
        }
      }

      document.add(spacer(14));
      document.add(paragraph(hi(lang) ? DISCLAIMER_HI : DISCLAIMER_EN, fonts.small()));
      if (!lang.equals(requestedLang)) {
        document.add(spacer(6));
        document.add(paragraph(
            "This copy is in English because no Devanagari font is installed on the server, and "
                + "the PDF base fonts cannot set Hindi. The summary tab in the browser prints "
                + "correctly in Hindi.", fonts.faint()));
      }
      document.close();
    } catch (DocumentException e) {
      log.error("Could not build the summary PDF for {}", memberApiId, e);
      throw new BadRequestException("The summary could not be prepared. Please try again.");
    }
    return out.toByteArray();
  }

  private PdfPTable medicineTable(Dto.SummaryView s, Fonts fonts, String lang)
      throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {3.1f, 3.4f, 2.6f, 2.4f});
    table.setWidthPercentage(100);
    header(table, fonts, hi(lang) ? "दवा" : "Medicine", hi(lang) ? "सॉल्ट" : "Salts",
        hi(lang) ? "कब लेनी है" : "Schedule", hi(lang) ? "किसने लिखी" : "Prescribed by");

    if (s.entries().isEmpty()) {
      PdfPCell empty = new PdfPCell(new Phrase(
          hi(lang) ? "कोई दवा दर्ज नहीं है।" : "No medicines recorded.", fonts.body()));
      empty.setColspan(4);
      empty.setPadding(6);
      table.addCell(empty);
      return table;
    }

    for (Dto.CabinetView e : s.entries()) {
      List<String> salts = new ArrayList<>();
      for (Dto.MedSalt ms : e.medicine().salts()) {
        salts.add(ms.name() + " " + ms.strength());
      }
      cell(table, e.medicine().brand() + "\n" + blankTo(e.medicine().form(), ""), fonts.body());
      cell(table, String.join("\n", salts), fonts.body());
      cell(table, e.schedule() + "\n" + e.dosesPerDay()
          + (hi(lang) ? " बार/दिन" : " a day"), fonts.body());
      cell(table, blankTo(e.prescribedBy(), "—"), fonts.small());
    }
    return table;
  }

  /**
   * One alert: what it is, which medicines it is about, the explanation, and the
   * questions to ask. The colour is printed as a word as well as a rule, because
   * a summary is often photocopied in black and white.
   */
  private PdfPTable alertBlock(Dto.AlertView a, Fonts fonts, String lang) {
    Dto.Explanation x = a.explanation().get(lang);
    PdfPTable block = new PdfPTable(1);
    block.setWidthPercentage(100);
    block.setSpacingAfter(9);

    PdfPCell cell = new PdfPCell();
    cell.setPadding(8);
    cell.setBorderColor(new Color(0x99, 0x99, 0x99));
    cell.setBorderWidthLeft(3f);
    cell.setBorderColorLeft(colourOf(a.colour()));

    cell.addElement(new Paragraph(label(a, lang), fonts.faint()));
    cell.addElement(new Paragraph(x.title(), fonts.h3()));
    cell.addElement(new Paragraph(x.body(), fonts.body()));
    if (a.dailyTotal() != null) {
      cell.addElement(new Paragraph(
          (hi(lang) ? "दिन का जोड़: " : "Daily total: ")
              + "%s %s (%s %s %s)".formatted(trim(a.dailyTotal().value()), a.dailyTotal().unit(),
                  hi(lang) ? "सीमा" : "limit", trim(a.dailyTotal().ceiling()),
                  a.dailyTotal().unit()),
          fonts.small()));
    }
    cell.addElement(new Paragraph(hi(lang) ? "डॉक्टर से पूछने के लिए:" : "To ask the doctor:",
        fonts.small()));
    for (String q : x.questions()) {
      cell.addElement(new Paragraph("  •  " + q, fonts.small()));
    }
    cell.addElement(new Paragraph(x.closing(), fonts.smallBold()));
    block.addCell(cell);
    return block;
  }

  /** Says where an alert came from: a rule, a dataset, or the model. */
  private static String label(Dto.AlertView a, String lang) {
    String severity = hi(lang) ? ExplanationService.severityHi(a.severity()) : a.severity();
    String source = switch (a.source()) {
      case "dataset" -> (hi(lang) ? "डेटासेट" : "dataset") + ": "
          + blankTo(a.datasetName(), "interactions");
      case "ai" -> (hi(lang) ? "AI अनुमान" : "AI prediction")
          + (a.probability() == null ? "" : " " + Math.round(a.probability() * 100) + "%");
      default -> a.ruleSet() == null ? (hi(lang) ? "नियम" : "rule") : a.ruleSet();
    };
    return "[" + severity + "] " + source;
  }

  private static Color colourOf(String colour) {
    return switch (colour) {
      case "red" -> new Color(0xb4, 0x23, 0x18);
      case "yellow" -> new Color(0xb5, 0x47, 0x08);
      default -> new Color(0x06, 0x76, 0x47);
    };
  }

  /* ================= fonts ================= */

  private record Fonts(String lang, Font h1, Font h2, Font h3, Font body, Font small,
      Font smallBold, Font faint) {
  }

  /**
   * Builds the font set for a language, falling back to English when Hindi
   * cannot be set.
   *
   * Writing Devanagari with a font that has no Devanagari glyphs does not fail:
   * it produces a page of blanks. An English summary is useful; a blank one is
   * not, so the language actually used is returned alongside the fonts and the
   * PDF says which it is.
   */
  private Fonts fonts(String requestedLang) {
    if ("hi".equals(requestedLang)) {
      BaseFont devanagari = loadDevanagari();
      if (devanagari != null) {
        return new Fonts("hi",
            new Font(devanagari, 17, Font.BOLD),
            new Font(devanagari, 12, Font.BOLD),
            new Font(devanagari, 11, Font.BOLD),
            new Font(devanagari, 10),
            new Font(devanagari, 9),
            new Font(devanagari, 9, Font.BOLD),
            new Font(devanagari, 8, Font.NORMAL, new Color(0x55, 0x55, 0x55)));
      }
      log.info("No Devanagari font found, so the summary PDF falls back to English");
    }
    return new Fonts("en",
        new Font(Font.HELVETICA, 17, Font.BOLD),
        new Font(Font.HELVETICA, 12, Font.BOLD),
        new Font(Font.HELVETICA, 11, Font.BOLD),
        new Font(Font.HELVETICA, 10),
        new Font(Font.HELVETICA, 9),
        new Font(Font.HELVETICA, 9, Font.BOLD),
        new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x55, 0x55, 0x55)));
  }

  private BaseFont loadDevanagari() {
    for (String candidate : DEVANAGARI_FONTS) {
      String file = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(','))
          : candidate;
      if (!Files.isReadable(Path.of(file))) {
        continue;
      }
      try {
        return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
      } catch (Exception e) {
        log.debug("Could not use {} for the Hindi PDF: {}", candidate, e.getMessage());
      }
    }
    return null;
  }

  /* ================= small helpers ================= */

  private static boolean hi(String lang) {
    return "hi".equals(lang);
  }

  private static Paragraph paragraph(String text, Font font) {
    Paragraph p = new Paragraph(text, font);
    p.setAlignment(Element.ALIGN_LEFT);
    return p;
  }

  private static Paragraph spacer(float points) {
    Paragraph p = new Paragraph(" ");
    p.setLeading(points);
    return p;
  }

  private static void header(PdfPTable table, Fonts fonts, String... titles) {
    for (String title : titles) {
      PdfPCell cell = new PdfPCell(new Phrase(title, fonts.smallBold()));
      cell.setBackgroundColor(new Color(0xf0, 0xf0, 0xf0));
      cell.setPadding(5);
      table.addCell(cell);
    }
  }

  private static void cell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(5);
    table.addCell(cell);
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String trim(double n) {
    return n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
  }
}
