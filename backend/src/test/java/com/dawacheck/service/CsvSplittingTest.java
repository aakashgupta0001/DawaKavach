package com.dawacheck.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Splitting a CSV line.
 *
 * The reason this is not {@code line.split(",")}: a DDInter mechanism sentence
 * contains commas, and tearing one into several columns would shift every
 * column after it, so a severity would be read out of the middle of a sentence
 * and imported as fact.
 */
class CsvSplittingTest {

  @Test
  void splitsPlainColumns() {
    assertThat(AdminService.splitCsv("Dolo 650,Micro Labs,Tablet,15 tablets,31.5"))
        .containsExactly("Dolo 650", "Micro Labs", "Tablet", "15 tablets", "31.5");
  }

  @Test
  void keepsAQuotedCommaInsideItsColumn() {
    assertThat(AdminService.splitCsv(
        "Aspirin,Ibuprofen,major,\"Both irritate the stomach, and bleeding risk rises.\""))
        .containsExactly("Aspirin", "Ibuprofen", "major",
            "Both irritate the stomach, and bleeding risk rises.");
  }

  @Test
  void readsAnEscapedQuote() {
    assertThat(AdminService.splitCsv("a,\"he said \"\"take it\"\" once\",c"))
        .containsExactly("a", "he said \"take it\" once", "c");
  }

  @Test
  void keepsEmptyColumns() {
    // An empty composition column is what marks a row as unusable, so it has to
    // survive as an empty cell rather than disappearing and shifting the rest.
    assertThat(AdminService.splitCsv("Broken Row,NoMaker,Tablet,10 tablets,10.0,"))
        .hasSize(6)
        .last().asString().isEmpty();
    assertThat(AdminService.splitCsv("a,,c")).containsExactly("a", "", "c");
  }

  @Test
  void handlesASingleColumn() {
    assertThat(AdminService.splitCsv("name")).containsExactly("name");
  }
}
