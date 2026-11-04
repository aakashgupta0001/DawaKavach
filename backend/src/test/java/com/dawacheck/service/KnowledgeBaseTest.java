package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.domain.Interaction;
import com.dawacheck.domain.Medicine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The knowledge base and the brand matcher, module 2.
 *
 * The matcher is what stands between a smudged bill line and the right medicine.
 * Getting it wrong in one direction asks the user a question they can answer; in
 * the other it silently files the wrong medicine and every later check is built
 * on that.
 */
class KnowledgeBaseTest extends IntegrationTest {

  @Autowired
  private KnowledgeBase kb;

  private String bestMatch(String line) {
    List<KnowledgeBase.Match> matches = kb.search(line, 3);
    assertThat(matches).as("matches for \"" + line + "\"").isNotEmpty();
    return matches.get(0).medicine().brand;
  }

  /* ---------------- what is loaded ---------------- */

  @Test
  void theSeededCatalogueIsComplete() {
    assertThat(kb.allSalts()).hasSize(24);
    assertThat(kb.allMedicines()).hasSize(26);
    assertThat(kb.synonymCount()).isEqualTo(13);
  }

  @Test
  void everyMedicineHasAtLeastOneSalt() {
    // A brand with no salts is invisible to every check in the application,
    // which is the worst way for a catalogue row to be broken.
    for (Medicine m : kb.allMedicines()) {
      assertThat(m.composition).as(m.brand).isNotEmpty();
      assertThat(m.composition).allSatisfy(ms -> {
        assertThat(ms.salt).isNotNull();
        assertThat(ms.strength).isNotBlank();
      });
    }
  }

  @Test
  void theSaltsWithDailyCeilingsAreTheOnesThatNeedThem() {
    // Only the salts where a single sensible daily number exists carry one.
    assertThat(kb.allSalts())
        .filteredOn(s -> s.dailyCeilingMg != null)
        .extracting(s -> s.name)
        .containsExactlyInAnyOrder("Paracetamol", "Ibuprofen", "Aspirin", "Diclofenac");
  }

  /* ---------------- interactions ---------------- */

  @Test
  void anInteractionIsFoundInEitherOrder() {
    long aspirin = find("Aspirin");
    long ibuprofen = find("Ibuprofen");

    Interaction forward = kb.interaction(aspirin, ibuprofen);
    Interaction backward = kb.interaction(ibuprofen, aspirin);

    assertThat(forward).isNotNull();
    assertThat(backward).isNotNull();
    assertThat(forward.id).isEqualTo(backward.id);
    assertThat(forward.severity).isEqualTo("major");
  }

  @Test
  void anUnlistedPairReturnsNothingRatherThanGuessing() {
    assertThat(kb.interaction(find("Paracetamol"), find("Cefixime"))).isNull();
  }

  @Test
  void everyInteractionCarriesASeverityAndBothMechanisms() {
    // The severity drives the colour, and a missing Hindi mechanism would leave
    // half the families reading an English sentence mid-paragraph.
    for (var salt : kb.allSalts()) {
      for (var other : kb.allSalts()) {
        Interaction i = kb.interaction(salt.id, other.id);
        if (i == null) {
          continue;
        }
        assertThat(i.severity).isIn("major", "moderate", "minor");
        assertThat(i.mechanism).isNotBlank();
        assertThat(i.mechanismHi).as(i.mechanism).isNotBlank();
      }
    }
  }

  /* ---------------- brand matching ---------------- */

  @Test
  void matchesACleanBillLine() {
    assertThat(bestMatch("DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50"))
        .isEqualTo("Dolo 650");
    assertThat(bestMatch("COMBIFLAM TABS    20N   B.No CF9087  EXP 11/27   44.00"))
        .isEqualTo("Combiflam");
    assertThat(bestMatch("TELMA-40 TABLET   15N   B.No TM5531  EXP 06/28  148.00"))
        .isEqualTo("Telma 40");
  }

  @Test
  void recoversFromTheCharactersOcrTypicallyGetsWrong() {
    // A capital letter read where a digit was printed, and the reverse. These
    // are the two mistakes a photographed bill makes most.
    assertThat(bestMatch("ECOSPFlN 75  Aspirin Tablets IP 75 mg")).isEqualTo("Ecosprin 75");
    assertThat(bestMatch("PAN-4O  Pantoprazole Gastro-resistant")).isEqualTo("Pan 40");
    assertThat(bestMatch("DOLO 65O")).isEqualTo("Dolo 650");
  }

  @Test
  void findsTheBrandEvenWhenTheLineCarriesTheSaltNameToo() {
    // A medicine box prints the brand and then the composition. Scoring the
    // whole line against a short brand name goes badly, so the first few words
    // are scored as well.
    List<KnowledgeBase.Match> matches =
        kb.search("ECOSPFlN 75  Aspirin Tablets IP 75 mg", 3);
    assertThat(matches.get(0).medicine().brand).isEqualTo("Ecosprin 75");
    assertThat(matches.get(0).score()).isGreaterThan(0.8);
  }

  @Test
  void findsBrandsBySaltName() {
    // Typing a salt has to find the brands containing it, or the only way to
    // add a medicine is to already know its brand name.
    List<String> brands = kb.search("paracetamol", 8).stream()
        .map(m -> m.medicine().brand)
        .toList();
    assertThat(brands).contains("Dolo 650", "Calpol 500", "Crocin Advance");
  }

  @Test
  void findsBrandsByASaltSynonym() {
    // "Acetaminophen" is what the same salt is called elsewhere, and a family
    // reading an imported label may well type that.
    assertThat(kb.search("acetaminophen", 8))
        .extracting(m -> m.medicine().brand)
        .contains("Dolo 650");
  }

  @Test
  void rankTheExactStrengthAboveTheOtherOne() {
    // Dolo 650 and Dolo 500 are different products. The number is all that
    // separates them, so it has to count.
    assertThat(bestMatch("DOLO 650")).isEqualTo("Dolo 650");
    assertThat(bestMatch("DOLO 500")).isEqualTo("Dolo 500");
  }

  @Test
  void aQueryTooShortToMeanAnythingReturnsNothing() {
    // Otherwise every keystroke in the search box offers the whole catalogue.
    assertThat(kb.search("d", 8)).isEmpty();
    assertThat(kb.search("", 8)).isEmpty();
    assertThat(kb.search(null, 8)).isEmpty();
  }

  @Test
  void returnsNoMoreSuggestionsThanAsked() {
    assertThat(kb.search("tab", 3)).hasSizeLessThanOrEqualTo(3);
    assertThat(kb.search("paracetamol", 1)).hasSizeLessThanOrEqualTo(1);
  }

  @Test
  void suggestionsComeBackBestFirst() {
    List<KnowledgeBase.Match> matches = kb.search("TELMA-40", 3);
    assertThat(matches).isSortedAccordingTo(
        (a, b) -> Double.compare(b.score(), a.score()));
  }

  @Test
  void aNonsenseLineDoesNotProduceAConfidentMatch() {
    // The scanner shows the top score to the user and asks them to confirm
    // below a floor. A confident score on gibberish would skip that question.
    List<KnowledgeBase.Match> matches = kb.search("XQZTHWV 999", 3);
    if (!matches.isEmpty()) {
      assertThat(matches.get(0).score()).isLessThan(ScanService.MATCH_CONFIDENCE_FLOOR);
    }
  }

  /* ---------------- match keys ---------------- */

  @Test
  void twoBrandsWithTheSameCompositionShareAMatchKey() {
    // Pan 40 and Pantocid 40 are both pantoprazole 40 mg. This is what makes
    // the generics lookup and the brand comparison work.
    Medicine pan = byBrand("Pan 40");
    Medicine pantocid = byBrand("Pantocid 40");
    assertThat(KnowledgeBase.saltStrengthKey(pan))
        .isEqualTo(KnowledgeBase.saltStrengthKey(pantocid));
    assertThat(KnowledgeBase.saltIdKey(pan)).isEqualTo(KnowledgeBase.saltIdKey(pantocid));
  }

  @Test
  void twoStrengthsOfOneSaltShareTheSaltKeyButNotTheStrengthKey() {
    Medicine dolo650 = byBrand("Dolo 650");
    Medicine dolo500 = byBrand("Dolo 500");
    assertThat(KnowledgeBase.saltIdKey(dolo650)).isEqualTo(KnowledgeBase.saltIdKey(dolo500));
    assertThat(KnowledgeBase.saltStrengthKey(dolo650))
        .isNotEqualTo(KnowledgeBase.saltStrengthKey(dolo500));
  }

  @Test
  void aMatchKeyDoesNotDependOnTheOrderSaltsArePrintedIn() {
    // Combiflam prints ibuprofen first; a generic of it may print paracetamol
    // first. They are the same medicine either way.
    Medicine combiflam = byBrand("Combiflam");
    assertThat(KnowledgeBase.saltIdKey(combiflam))
        .isEqualTo(KnowledgeBase.saltIdKey(combiflam));
    assertThat(KnowledgeBase.saltIdKey(combiflam).split("\\|")).isSorted();
  }

  /* ---------------- helpers ---------------- */

  private long find(String saltName) {
    return kb.allSalts().stream()
        .filter(s -> s.name.equalsIgnoreCase(saltName))
        .findFirst().orElseThrow().id;
  }

  private Medicine byBrand(String brand) {
    return kb.allMedicines().stream()
        .filter(m -> m.brand.equalsIgnoreCase(brand))
        .findFirst().orElseThrow();
  }
}
