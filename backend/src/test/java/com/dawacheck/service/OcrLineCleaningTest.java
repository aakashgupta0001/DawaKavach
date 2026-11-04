package com.dawacheck.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cleaning step between Tesseract and the brand matcher.
 *
 * A bill line is mostly not the medicine name. What survives this method is
 * what the matcher is scored against, so leaving a batch number in would push
 * the right brand down the list of suggestions.
 */
class OcrLineCleaningTest {

  @Test
  void stripsEverythingButTheBrandFromAPrintedBillLine() {
    String cleaned = KnowledgeBase.cleanOcrLine(
        "DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50");
    assertThat(cleaned).isEqualTo("DOLO-650");
  }

  @Test
  void stripsBatchNumbersAndExpiryDates() {
    assertThat(KnowledgeBase.cleanOcrLine("COMBIFLAM B.No CF9087 EXP 11/27"))
        .isEqualTo("COMBIFLAM");
    assertThat(KnowledgeBase.cleanOcrLine("ZIFI 200 BNo ZF11 EXP 3-2028"))
        .isEqualTo("ZIFI 200");
  }

  @Test
  void stripsPricesAndRupeeAmounts() {
    assertThat(KnowledgeBase.cleanOcrLine("TELMA-40 148.00")).isEqualTo("TELMA-40");
    assertThat(KnowledgeBase.cleanOcrLine("TELMA-40 Rs. 148")).isEqualTo("TELMA-40");
    assertThat(KnowledgeBase.cleanOcrLine("TELMA-40 ₹148.00")).isEqualTo("TELMA-40");
  }

  @Test
  void stripsPackSizesAndDosageWords() {
    assertThat(KnowledgeBase.cleanOcrLine("SHELCAL 500 TABLETS 15N")).isEqualTo("SHELCAL 500");
    assertThat(KnowledgeBase.cleanOcrLine("OMEZ 20 CAPSULE")).isEqualTo("OMEZ 20");
  }

  @Test
  void keepsTheStrengthThatIsPartOfTheBrandName() {
    // "Dolo 650" and "Dolo 500" are different products, and the number is how
    // they are told apart, so the digits must not be stripped with the noise.
    assertThat(KnowledgeBase.cleanOcrLine("DOLO 650")).contains("650");
    assertThat(KnowledgeBase.cleanOcrLine("ALPRAX 0.25 TAB")).contains("0.25");
  }

  @Test
  void survivesEmptyAndNullInput() {
    assertThat(KnowledgeBase.cleanOcrLine(null)).isEmpty();
    assertThat(KnowledgeBase.cleanOcrLine("")).isEmpty();
    assertThat(KnowledgeBase.cleanOcrLine("   ***   ")).isEmpty();
  }
}
