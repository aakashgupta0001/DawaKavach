package com.dawacheck.dto;

/**
 * The database uses plain auto-increment primary keys, as section 12 specifies.
 * The API speaks prefixed string ids instead: {@code fm1}, {@code m12},
 * {@code s7}, {@code c4}.
 *
 * The prefix is not decoration. The frontend puts ids straight into URLs and
 * sends them back in request bodies, and a bare {@code 7} tells you nothing
 * about what it points at: a member, a medicine and a cabinet entry all have a
 * row 7. With the prefix, a request that sends a medicine id where an entry id
 * belongs fails here rather than silently reading the wrong table.
 */
public final class Ids {

  public static final String MEMBER = "fm";
  public static final String MEDICINE = "m";
  public static final String SALT = "s";
  public static final String ENTRY = "c";
  public static final String SCAN = "scan";
  public static final String SCAN_ITEM = "si";
  public static final String REMINDER = "r";
  public static final String GENERIC = "g";
  public static final String USER = "u";
  public static final String QUEUE = "q";
  public static final String DATASET = "d";

  private Ids() {
  }

  /** {@code encode("fm", 1L)} gives {@code "fm1"}; a null id gives null. */
  public static String encode(String prefix, Long id) {
    return id == null ? null : prefix + id;
  }

  /**
   * Reads the numeric part of a prefixed id, rejecting a value carrying some
   * other prefix.
   *
   * @throws IllegalArgumentException when the value is blank, carries the wrong
   *     prefix, or has no number after it
   */
  public static long decode(String prefix, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A " + describe(prefix) + " id is required.");
    }
    String raw = value.trim();
    if (!raw.startsWith(prefix)) {
      throw new IllegalArgumentException(
          "Expected a " + describe(prefix) + " id like " + prefix + "1, but got " + raw + ".");
    }
    String digits = raw.substring(prefix.length());
    // "m12" must not also match the "medicine" prefix test for "ms12".
    if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
      throw new IllegalArgumentException(
          "Expected a " + describe(prefix) + " id like " + prefix + "1, but got " + raw + ".");
    }
    return Long.parseLong(digits);
  }

  /** Same as {@link #decode} but returns null instead of throwing on a blank value. */
  public static Long decodeOrNull(String prefix, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return decode(prefix, value);
  }

  private static String describe(String prefix) {
    return switch (prefix) {
      case MEMBER -> "family member";
      case MEDICINE -> "medicine";
      case SALT -> "salt";
      case ENTRY -> "cabinet entry";
      case SCAN -> "scan";
      case SCAN_ITEM -> "scan line";
      case REMINDER -> "reminder";
      case GENERIC -> "generic product";
      default -> "record";
    };
  }
}
