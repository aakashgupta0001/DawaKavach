package com.dawacheck.service;

import java.util.List;

/**
 * Three sample bills, for showing the scanner on a machine where Tesseract is
 * not installed and for testing the matcher against text that does not change.
 *
 * These are OCR output, not photographs: the text is what Tesseract typically
 * returns from each kind of source, noise included, with the per-line confidence
 * it typically reports. Everything after that point is the real pipeline. The
 * lines go through the same cleaning and the same Jaro-Winkler matching as a
 * live upload, so the brand guesses and their scores are computed at request
 * time and are never written down here.
 *
 * {@code rawText} is the whole page as read, which the scan screen shows when the
 * user expands the detected text. {@code lines} are the ones that survived the
 * filter in {@link ScanService} and became something to confirm, which is why
 * the store name and the bill total appear in one and not the other.
 *
 * The third sample earns its place by failing. A handwritten slip read at a
 * confidence of 0.34 should not produce a confident brand match, and the scanner
 * has to ask the user to type the name instead.
 */
public final class OcrSamples {

  public record Line(String text, double confidence) {
  }

  public record Sample(String label, String rawText, List<Line> lines) {
  }

  private static final List<Sample> SAMPLES = List.of(
      new Sample("Pharmacy bill (printed, 4 lines)",
          String.join("\n",
              "SHREE MEDICAL & GENERAL STORES",
              "GST 27AABCU9603R1ZM   Bill No 4417   24/10/2026",
              "1  DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50",
              "2  COMBIFLAM TABS    20N   B.No CF9087  EXP 11/27   44.00",
              "3  SINAREST TAB      10N   B.No SN1123  EXP 03/28   85.00",
              "4  TELMA-40 TABLET   15N   B.No TM5531  EXP 06/28  148.00",
              "TOTAL  308.50   PAID BY UPI"),
          List.of(
              new Line("DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50", 0.94),
              new Line("COMBIFLAM TABS    20N   B.No CF9087  EXP 11/27   44.00", 0.91),
              new Line("SINAREST TAB      10N   B.No SN1123  EXP 03/28   85.00", 0.88),
              new Line("TELMA-40 TABLET   15N   B.No TM5531  EXP 06/28  148.00", 0.90))),

      new Sample("Medicine box (blurred, 2 lines)",
          String.join("\n",
              "ECOSPFlN 75",
              "Aspirin Tablets IP 75 mg",
              "PAN-4O",
              "Pantoprazole Gastro-resistant Tablets"),
          List.of(
              new Line("ECOSPFlN 75  Aspirin Tablets IP 75 mg", 0.61),
              new Line("PAN-4O  Pantoprazole Gastro-resistant", 0.58))),

      new Sample("Handwritten slip (poor quality, 2 lines)",
          String.join("\n",
              "Rx",
              "Alprax .25  1 at nite",
              "Wa?f 5mg  --- alt day"),
          List.of(
              new Line("Alprax .25  1 at nite", 0.52),
              new Line("Wa?f 5mg  --- alt day", 0.34))));

  private OcrSamples() {
  }

  public static List<Sample> all() {
    return SAMPLES;
  }

  public static Sample at(int index) {
    if (index < 0 || index >= SAMPLES.size()) {
      throw new BadRequestException("There is no sample bill at that position.");
    }
    return SAMPLES.get(index);
  }
}
