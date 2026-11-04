package com.dawacheck.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the strength printed on a box into a number the daily-total check can
 * add up: "650 mg" and "0.5 g" both become 650 and 500 mg.
 *
 * International units are a different kind of quantity and cannot be converted
 * to milligrams, so they keep their own unit and are never mixed into a
 * milligram total.
 */
public final class Strengths {

  private static final Pattern PATTERN =
      Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(mg|g|mcg|iu)?", Pattern.CASE_INSENSITIVE);

  public record Amount(double value, String unit) {
  }

  private Strengths() {
  }

  public static Amount parse(String text) {
    if (text == null) {
      return new Amount(0, "mg");
    }
    Matcher m = PATTERN.matcher(text);
    if (!m.find()) {
      return new Amount(0, "mg");
    }
    double value = Double.parseDouble(m.group(1));
    String unit = m.group(2) == null ? "mg" : m.group(2).toLowerCase();
    return switch (unit) {
      case "g" -> new Amount(value * 1000, "mg");
      case "mcg" -> new Amount(value / 1000, "mg");
      case "iu" -> new Amount(value, "IU");
      default -> new Amount(value, "mg");
    };
  }

  /** First number in a pack description: "15 tablets" gives 15. */
  public static int packCount(String pack) {
    if (pack == null) {
      return 1;
    }
    Matcher m = Pattern.compile("(\\d+)").matcher(pack);
    return m.find() ? Integer.parseInt(m.group(1)) : 1;
  }

  public static double round2(double n) {
    return Math.round(n * 100.0) / 100.0;
  }
}
