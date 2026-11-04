package com.dawacheck.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter that enforces the one rule the whole application is built around.
 *
 * These are the tests to be most careful about weakening: if the filter stops
 * catching a phrase, nothing else in the system will notice, and the result is
 * an application telling a 72-year-old to stop taking his heart medicine.
 */
class SafetyFilterTest {

  private final SafetyFilter filter = new SafetyFilter();

  @Test
  void passesOrdinaryExplanationText() {
    SafetyFilter.Verdict v = filter.check(
        "Dolo 650 and Combiflam both contain Paracetamol. Confirm with your doctor.");
    assertThat(v.safe()).isTrue();
    assertThat(v.phrase()).isNull();
  }

  @Test
  void blocksAdviceToStopAMedicine() {
    SafetyFilter.Verdict v = filter.check("You should stop taking one of these.");
    assertThat(v.safe()).isFalse();
    assertThat(v.phrase()).isEqualTo("stop taking");
  }

  @Test
  void blocksAdviceToChangeADose() {
    assertThat(filter.check("Consider reducing the dose of ibuprofen.").safe()).isTrue();
    // The banned phrase is "reduce the dose", and the gerund is not the same
    // string. This test records that gap rather than pretending it is closed:
    // the filter matches fixed phrases, so it is a floor and not a guarantee.
    assertThat(filter.check("Reduce the dose of ibuprofen.").safe()).isFalse();
    assertThat(filter.check("Please double the dose tonight.").safe()).isFalse();
  }

  @Test
  void blocksHindiAdviceToo() {
    // Half the explanations are Hindi, so a filter that only read English would
    // leave half the output unchecked.
    assertThat(filter.check("यह दवा बंद कर दें।").safe()).isFalse();
    assertThat(filter.check("खुराक बढ़ा दीजिए।").safe()).isFalse();
    assertThat(filter.check("अपने डॉक्टर या दवा वाले से पुष्टि करें।").safe()).isTrue();
  }

  @Test
  void ignoresCase() {
    assertThat(filter.check("STOP TAKING this one").safe()).isFalse();
    assertThat(filter.check("Do Not Take them together").safe()).isFalse();
  }

  @Test
  void treatsNullAsSafe() {
    assertThat(filter.check(null).safe()).isTrue();
  }

  @Test
  void listsThePhrasesItBlocks() {
    assertThat(filter.bannedPhrases())
        .contains("stop taking", "discontinue", "बंद कर दें")
        .hasSizeGreaterThanOrEqualTo(11);
  }
}
