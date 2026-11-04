package com.dawacheck.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading the strength off a box is where the daily total comes from, so a
 * misparse here would silently change a number a doctor is shown.
 */
class StrengthsTest {

  @Test
  void readsMilligrams() {
    assertThat(Strengths.parse("650 mg").value()).isEqualTo(650);
    assertThat(Strengths.parse("650mg").value()).isEqualTo(650);
    assertThat(Strengths.parse("0.25 mg").value()).isEqualTo(0.25);
  }

  @Test
  void convertsGramsAndMicrogramsToMilligrams() {
    // Otherwise 0.5 g would add 0.5 to a milligram total instead of 500.
    assertThat(Strengths.parse("0.5 g").value()).isEqualTo(500);
    assertThat(Strengths.parse("500 mcg").value()).isEqualTo(0.5);
    assertThat(Strengths.parse("0.5 g").unit()).isEqualTo("mg");
  }

  @Test
  void keepsInternationalUnitsSeparate() {
    // An IU is not a mass and cannot be compared with a milligram ceiling, so
    // it has to come back carrying its own unit.
    Strengths.Amount vitaminD = Strengths.parse("250 IU");
    assertThat(vitaminD.value()).isEqualTo(250);
    assertThat(vitaminD.unit()).isEqualTo("IU");
  }

  @Test
  void assumesMilligramsWhenNoUnitIsPrinted() {
    assertThat(Strengths.parse("500").value()).isEqualTo(500);
    assertThat(Strengths.parse("500").unit()).isEqualTo("mg");
  }

  @Test
  void returnsZeroRatherThanFailingOnUnreadableText() {
    // A zero contributes nothing to a total, which is the safe way to be wrong
    // here: an exception would take out the whole safety check for the family.
    assertThat(Strengths.parse("as labelled").value()).isZero();
    assertThat(Strengths.parse(null).value()).isZero();
    assertThat(Strengths.parse("").value()).isZero();
  }

  @Test
  void readsThePackSize() {
    assertThat(Strengths.packCount("15 tablets")).isEqualTo(15);
    assertThat(Strengths.packCount("20 capsules")).isEqualTo(20);
  }

  @Test
  void packSizeFallsBackToOne() {
    // Dividing a pack price by zero would be worse than treating it as a single
    // unit and showing a per-unit price that equals the pack price.
    assertThat(Strengths.packCount("strip")).isEqualTo(1);
    assertThat(Strengths.packCount(null)).isEqualTo(1);
  }
}
