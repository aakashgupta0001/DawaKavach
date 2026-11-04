package com.dawacheck.service;

import com.dawacheck.dto.Dto;
import com.dawacheck.repo.LlmCacheRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 7, the wording.
 *
 * Two things are being guarded. One is that both languages always arrive
 * complete, because the frontend toggles between them with no round trip and an
 * empty Hindi body would simply show blank. The other is that the safety filter
 * sits in the path, so no wording can reach a family without passing it.
 */
class ExplanationServiceTest {

  private final ExplanationService explanations =
      new ExplanationService(new SafetyFilter(), Mockito.mock(LlmCacheRepository.class));

  private Facts duplicateFacts() {
    Facts f = new Facts();
    f.salt = "Paracetamol";
    f.count = 3;
    f.distinctBrands = 3;
    f.brand = "Dolo 650";
    f.brandsEn = "Dolo 650, Combiflam and Sinarest";
    f.brandsHi = "Dolo 650, Combiflam और Sinarest";
    return f;
  }

  @Test
  void bothLanguagesArriveFilledIn() {
    Map<String, Dto.Explanation> out = explanations.explain("duplicate", duplicateFacts());

    assertThat(out).containsOnlyKeys("en", "hi");
    for (Dto.Explanation x : out.values()) {
      assertThat(x.title()).isNotBlank();
      assertThat(x.body()).isNotBlank();
      assertThat(x.questions()).hasSize(3).allSatisfy(q -> assertThat(q).isNotBlank());
      assertThat(x.closing()).isNotBlank();
      assertThat(x.blocked()).isFalse();
      assertThat(x.blockedPhrase()).isNull();
    }
  }

  @Test
  void theClosingLineIsTheSameEveryTime() {
    // It is the one sentence that must appear on every alert in the
    // application, so it is a constant and not something a template chooses.
    for (String type : List.of("duplicate", "total", "interaction", "predicted", "allergy")) {
      Map<String, Dto.Explanation> out = explanations.explain(type, everyFact());
      assertThat(out.get("en").closing())
          .as(type)
          .isEqualTo("Confirm with your doctor or pharmacist.");
      assertThat(out.get("hi").closing())
          .as(type)
          .isEqualTo("अपने डॉक्टर या दवा वाले से पुष्टि करें।");
    }
  }

  @Test
  void hindiDoesNotFallBackToEnglishProse() {
    Map<String, Dto.Explanation> out = explanations.explain("duplicate", duplicateFacts());
    // Brand names stay in Latin script because that is how the box is printed,
    // but every sentence around them must be Hindi.
    assertThat(out.get("hi").title()).contains("दवाओं में");
    assertThat(out.get("hi").body())
        .contains("सॉल्ट")
        .doesNotContain("contain")
        .doesNotContain(" and ");
    assertThat(out.get("hi").questions())
        .allSatisfy(q -> assertThat(q).containsPattern("[\\u0900-\\u097F]"));
  }

  @Test
  void anInteractionUsesTheTranslatedMechanismWhenThereIsOne() {
    Facts f = new Facts();
    f.saltA = "Aspirin";
    f.saltB = "Ibuprofen";
    f.brandA = "Ecosprin 75";
    f.brandB = "Brufen 400";
    f.mechanismEn = "Both irritate the stomach lining.";
    f.mechanismHi = "दोनों पेट की परत को नुकसान पहुँचाते हैं।";
    f.severity = "major";
    f.severityHi = "गंभीर";

    Map<String, Dto.Explanation> out = explanations.explain("interaction", f);
    assertThat(out.get("en").body()).contains("Both irritate the stomach lining.");
    assertThat(out.get("hi").body()).contains("दोनों पेट की परत");
  }

  @Test
  void anInteractionWithNoTranslationKeepsTheEnglishMechanismRatherThanDroppingIt() {
    // An imported row may have no Hindi mechanism. Showing the English sentence
    // is worse than showing it in Hindi and better than showing a gap where the
    // reason should be.
    Facts f = new Facts();
    f.saltA = "A";
    f.saltB = "B";
    f.brandA = "Brand A";
    f.brandB = "Brand B";
    f.mechanismEn = "A mechanism with no translation on record.";
    f.mechanismHi = null;
    f.severity = "moderate";

    Map<String, Dto.Explanation> out = explanations.explain("interaction", f);
    assertThat(out.get("hi").body()).contains("A mechanism with no translation on record.");
  }

  @Test
  void aSeverityWithNoHindiOnTheFactsIsStillTranslated() {
    Facts f = new Facts();
    f.saltA = "A";
    f.saltB = "B";
    f.brandA = "Brand A";
    f.brandB = "Brand B";
    f.mechanismEn = "Something.";
    f.severity = "minor";
    f.severityHi = null;

    assertThat(explanations.explain("interaction", f).get("hi").body()).contains("हल्का");
  }

  @Test
  void blocksAndReplacesWordingThatTellsSomeoneToStop() {
    // An elderly-rule body arrives already written, which is the one path where
    // unsafe wording could get in. The filter replaces it rather than trying to
    // repair it, and the alert says it was blocked.
    Facts f = new Facts();
    f.titleEn = "A rule";
    f.titleHi = "एक नियम";
    f.bodyEn = "You should stop taking this medicine at once.";
    f.bodyHi = "यह दवा बंद कर दें।";
    f.questionsEn = List.of("One?", "Two?", "Three?");
    f.questionsHi = List.of("एक?", "दो?", "तीन?");

    Map<String, Dto.Explanation> out = explanations.explain("elderly", f);

    assertThat(out.get("en").blocked()).isTrue();
    assertThat(out.get("en").blockedPhrase()).isEqualTo("stop taking");
    assertThat(out.get("en").body()).doesNotContain("stop taking");
    assertThat(out.get("hi").blocked()).isTrue();
    assertThat(out.get("hi").body()).doesNotContain("बंद कर दें");

    // The closing line survives a block: the family still needs to be told to
    // confirm with someone.
    assertThat(out.get("en").closing()).isNotBlank();
    assertThat(out.get("hi").closing()).isNotBlank();
  }

  @Test
  void refusesAnUnknownTemplateRatherThanReturningAnEmptyAlert() {
    assertThatThrownBy(() -> explanations.explain("no-such-type", duplicateFacts()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void joinsAListTheWayEachLanguageDoes() {
    assertThat(ExplanationService.listToText(List.of("A"), "en")).isEqualTo("A");
    assertThat(ExplanationService.listToText(List.of("A", "B"), "en")).isEqualTo("A and B");
    assertThat(ExplanationService.listToText(List.of("A", "B", "C"), "en"))
        .isEqualTo("A, B and C");
    assertThat(ExplanationService.listToText(List.of("A", "B"), "hi")).isEqualTo("A और B");
    assertThat(ExplanationService.listToText(List.of(), "en")).isEmpty();
  }

  @Test
  void saysItIsNotUsingALanguageModel() {
    // The llm_cache table stays empty while this is false, which is why the
    // admin page can report a cache size of zero without that being a fault.
    assertThat(explanations.llmReady()).isFalse();
  }

  @Test
  void aCeilingReadsAsAWholeNumber() {
    Facts f = new Facts();
    f.salt = "Paracetamol";
    f.total = 3450;
    f.unit = "mg";
    f.ceiling = 3000;
    f.brandsEn = "Dolo 650 and Sinarest";
    f.brandsHi = "Dolo 650 और Sinarest";

    // "3000.0 mg a day" would look like a rounding artefact to a doctor.
    assertThat(explanations.explain("total", f).get("en").body())
        .contains("3000 mg")
        .doesNotContain("3000.0");
  }

  /** One Facts carrying enough for any template, for the across-the-board checks. */
  private Facts everyFact() {
    Facts f = duplicateFacts();
    f.total = 3450;
    f.unit = "mg";
    f.ceiling = 3000;
    f.saltA = "Aspirin";
    f.saltB = "Ibuprofen";
    f.brandA = "Ecosprin 75";
    f.brandB = "Brufen 400";
    f.mechanismEn = "A mechanism.";
    f.mechanismHi = "एक कारण।";
    f.severity = "major";
    f.severityHi = "गंभीर";
    f.pct = 72;
    f.titleEn = "Title";
    f.titleHi = "शीर्षक";
    f.bodyEn = "Body.";
    f.bodyHi = "विवरण।";
    f.questionsEn = List.of("One?", "Two?", "Three?");
    f.questionsHi = List.of("एक?", "दो?", "तीन?");
    return f;
  }
}
