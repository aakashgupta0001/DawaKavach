package com.dawacheck.service;

import java.util.List;

/**
 * The values an explanation template interpolates.
 *
 * Only some fields matter for any one alert type, which is why this is a plain
 * bag of fields rather than six separate records: the template table in
 * {@link ExplanationService} reads it the same way the browser version read a
 * JavaScript object, and the two stay easy to compare.
 */
public class Facts {

  /** Duplicate salt, daily total, allergy. */
  public String salt;
  public int count;
  public String brandsEn = "";
  public String brandsHi = "";
  public String brand;

  /**
   * How many different brand names the duplicate covers.
   *
   * One means the same medicine is on the list more than once, which reads
   * nothing like two different boxes sharing a salt and is explained separately.
   */
  public int distinctBrands;

  /** Daily total. */
  public double total;
  public String unit = "mg";
  public double ceiling;

  /** Interaction, known or predicted. */
  public String saltA;
  public String saltB;
  public String brandA;
  public String brandB;
  public String mechanismEn = "";
  public String mechanismHi = "";
  public String severity = "";
  public String severityHi = "";
  public int pct;

  /** Elderly rules, which arrive with their wording already decided. */
  public String titleEn;
  public String titleHi;
  public String bodyEn;
  public String bodyHi;
  public List<String> questionsEn = List.of();
  public List<String> questionsHi = List.of();
}
