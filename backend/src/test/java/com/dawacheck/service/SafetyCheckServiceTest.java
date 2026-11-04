package com.dawacheck.service;

import com.dawacheck.Fixtures;
import com.dawacheck.IntegrationTest;
import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The safety check engine, module 5.
 *
 * Each case says which boxes are on the shelf and which warnings should come
 * back. Nothing is written to the database: {@code check} is a function of a
 * member and a medicine list, which is what makes it testable to this level of
 * detail.
 */
class SafetyCheckServiceTest extends IntegrationTest {

  @Autowired
  private SafetyCheckService safety;

  @Autowired
  private KnowledgeBase kb;

  private Fixtures fx;

  @BeforeEach
  void setUp() {
    fx = new Fixtures(kb);
  }

  private List<Dto.AlertView> check(FamilyMember member, CabinetEntry... entries) {
    return safety.check(member, Fixtures.list(entries));
  }

  private static List<String> titles(List<Dto.AlertView> alerts, String type) {
    return alerts.stream()
        .filter(a -> a.type().equals(type))
        .map(a -> a.explanation().get("en").title())
        .toList();
  }

  private static Dto.AlertView only(List<Dto.AlertView> alerts, String type) {
    List<Dto.AlertView> matching = alerts.stream().filter(a -> a.type().equals(type)).toList();
    assertThat(matching).as("alerts of type " + type).hasSize(1);
    return matching.get(0);
  }

  /* ================================================================= */

  @Nested
  @DisplayName("the case from section 5")
  class TheWorkedExample {

    /**
     * Dadaji, 72. Seven medicines from three doctors, three of which contain
     * paracetamol. This is the case the application exists for, so it is
     * asserted in full: if this test ever goes green while reporting something
     * else, the application has stopped doing its job.
     */
    @Test
    void findsEverythingWrongWithDadajisList() {
      FamilyMember dadaji = fx.member("Ram Prasad Gupta", 72);
      List<Dto.AlertView> alerts = check(dadaji,
          fx.taking(dadaji, "Telma 40", 1, true),
          fx.taking(dadaji, "Ecosprin 75", 1, true),
          fx.taking(dadaji, "Pan 40", 1, true),
          fx.taking(dadaji, "Combiflam", 2),
          fx.taking(dadaji, "Dolo 650", 2),
          fx.taking(dadaji, "Sinarest", 3),
          fx.taking(dadaji, "Alprax 0.25", 1, true));

      assertThat(alerts).hasSize(8);
      assertThat(alerts).filteredOn(a -> a.colour().equals("red")).hasSize(3);
      assertThat(alerts).filteredOn(a -> a.colour().equals("yellow")).hasSize(5);

      // Paracetamol is in three of the seven boxes.
      Dto.AlertView duplicate = only(alerts, "DUPLICATE_SALT");
      assertThat(duplicate.saltNames()).containsExactly("Paracetamol");
      assertThat(duplicate.medicines())
          .containsExactlyInAnyOrder("Combiflam", "Dolo 650", "Sinarest");

      // 325x2 + 650x2 + 500x3 = 3450, against a 3000 mg ceiling.
      Dto.AlertView total = only(alerts, "SALT_DAILY_TOTAL");
      assertThat(total.dailyTotal().value()).isEqualTo(3450.0);
      assertThat(total.dailyTotal().ceiling()).isEqualTo(3000.0);
      assertThat(total.dailyTotal().unit()).isEqualTo("mg");
      assertThat(total.colour()).isEqualTo("red");

      assertThat(titles(alerts, "INTERACTION_KNOWN"))
          .containsExactlyInAnyOrder(
              "Ibuprofen with Aspirin",
              "Ibuprofen with Telmisartan",
              "Chlorpheniramine with Alprazolam");

      assertThat(alerts).filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .extracting(Dto.AlertView::ruleKey)
          .containsExactlyInAnyOrder("stopp-benzo", "stopp-ppi", "stopp-nsaid");
    }

    @Test
    void redComesBeforeAmber() {
      // A family reads the top of the list, so the ordering is part of the
      // result and not a presentation detail.
      FamilyMember dadaji = fx.member("Ram Prasad Gupta", 72);
      List<Dto.AlertView> alerts = check(dadaji,
          fx.taking(dadaji, "Telma 40", 1, true),
          fx.taking(dadaji, "Ecosprin 75", 1, true),
          fx.taking(dadaji, "Combiflam", 2),
          fx.taking(dadaji, "Dolo 650", 2),
          fx.taking(dadaji, "Sinarest", 3));

      List<String> colours = alerts.stream().map(Dto.AlertView::colour).toList();
      assertThat(colours).isSorted(); // red, yellow, green sort in that order
      assertThat(colours.get(0)).isEqualTo("red");
    }

    @Test
    void everyAlertEndsByAskingTheFamilyToConfirm() {
      FamilyMember dadaji = fx.member("Ram Prasad Gupta", 72);
      List<Dto.AlertView> alerts = check(dadaji,
          fx.taking(dadaji, "Telma 40", 1, true),
          fx.taking(dadaji, "Ecosprin 75", 1, true),
          fx.taking(dadaji, "Combiflam", 2),
          fx.taking(dadaji, "Dolo 650", 2),
          fx.taking(dadaji, "Sinarest", 3),
          fx.taking(dadaji, "Alprax 0.25", 1, true));

      assertThat(alerts).isNotEmpty();
      for (Dto.AlertView a : alerts) {
        for (String lang : List.of("en", "hi")) {
          Dto.Explanation x = a.explanation().get(lang);
          assertThat(x).as(a.type() + " in " + lang).isNotNull();
          assertThat(x.closing()).isNotBlank();
          assertThat(x.blocked()).isFalse();
          assertThat(x.title()).isNotBlank();
          assertThat(x.body()).isNotBlank();
          assertThat(x.questions()).hasSize(3);
        }
        assertThat(a.explanation().get("en").closing())
            .isEqualTo("Confirm with your doctor or pharmacist.");
        assertThat(a.explanation().get("hi").closing())
            .isEqualTo("अपने डॉक्टर या दवा वाले से पुष्टि करें।");
      }
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("the same salt in more than one medicine")
  class DuplicateSalt {

    @Test
    void findsTheSameSaltUnderTwoDifferentBrandNames() {
      // Dolo 650 and Calpol 500 look nothing alike on a shelf.
      FamilyMember m = fx.member("Two brands", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.taking(m, "Calpol 500", 1)), "DUPLICATE_SALT");

      assertThat(a.colour()).isEqualTo("red");
      assertThat(a.saltNames()).containsExactly("Paracetamol");
      assertThat(a.explanation().get("en").body()).contains("both contain Paracetamol");
      assertThat(a.explanation().get("en").questions().get(0))
          .isEqualTo("Do I need both of these medicines together?");
    }

    @Test
    void readsCorrectlyWhenTheSameBoxIsOnTheListTwice() {
      // Two strips of the same brand, which is what happens when a repeat
      // prescription is filled twice. "Alprax and Alprax both contain" would be
      // nonsense, so this case has its own wording.
      FamilyMember m = fx.member("Same brand twice", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Alprax 0.25", 1),
          fx.taking(m, "Alprax 0.25", 1)), "DUPLICATE_SALT");

      assertThat(a.explanation().get("en").title()).isEqualTo("Alprax 0.25 is on the list 2 times");
      assertThat(a.explanation().get("en").body())
          .contains("Alprax 0.25 contains Alprazolam")
          .doesNotContain("Alprax 0.25 and Alprax 0.25");
      assertThat(a.explanation().get("hi").title()).isEqualTo("Alprax 0.25 सूची में 2 बार है");
    }

    @Test
    void saysAllRatherThanBothForThreeMedicines() {
      FamilyMember m = fx.member("Three brands", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.taking(m, "Calpol 500", 1),
          fx.taking(m, "Crocin Advance", 1)), "DUPLICATE_SALT");

      assertThat(a.explanation().get("en").body()).contains("all contain Paracetamol");
    }

    @Test
    void saysNothingWhenEverySaltAppearsOnce() {
      FamilyMember m = fx.member("No overlap", 40);
      assertThat(titles(check(m,
          fx.taking(m, "Glycomet 500", 2),
          fx.taking(m, "Shelcal 500", 1)), "DUPLICATE_SALT")).isEmpty();
    }

    @Test
    void ignoresAMedicineMarkedStopped() {
      // A course that has ended is not going into the body, so it must not
      // raise a warning. This is the check that makes "Mark stopped" mean
      // something.
      FamilyMember m = fx.member("One stopped", 40);
      assertThat(check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.stopped(m, "Calpol 500", 1))).isEmpty();
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("how much of one salt in a day")
  class DailyTotal {

    @Test
    void addsUpAcrossMedicinesAndMultipliesByTheDailyDoses() {
      FamilyMember m = fx.member("Adding up", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Dolo 650", 3),      // 1950
          fx.taking(m, "Calpol 500", 3)),   // 1500
          "SALT_DAILY_TOTAL");

      assertThat(a.dailyTotal().value()).isEqualTo(3450.0);
      assertThat(a.colour()).isEqualTo("red");
      assertThat(a.explanation().get("en").body()).contains("3450 mg");
    }

    @Test
    void isAmberWhenTheTotalIsHighButUnderTheCeiling() {
      FamilyMember m = fx.member("Under the limit", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Dolo 650", 2),      // 1300
          fx.taking(m, "Calpol 500", 2)),   // 1000, total 2300 of 3000
          "SALT_DAILY_TOTAL");

      assertThat(a.dailyTotal().value()).isEqualTo(2300.0);
      assertThat(a.colour()).isEqualTo("yellow");
      assertThat(a.severity()).isEqualTo("moderate");
    }

    @Test
    void staysQuietAboutOneMedicineInsideItsLimit() {
      // Ibuprofen 400 twice a day is 800 against a 1200 ceiling, from a single
      // box. Raising that would be telling a family something is wrong when
      // nothing is, and an alert nobody needs teaches them to ignore the rest.
      FamilyMember m = fx.member("One box", 40);
      assertThat(titles(check(m, fx.taking(m, "Brufen 400", 2)), "SALT_DAILY_TOTAL")).isEmpty();
    }

    @Test
    void speaksUpAboutOneMedicineThatGoesPastTheLimitOnItsOwn() {
      // Ibuprofen 400 four times a day is 1600 against a 1200 ceiling. One box
      // is enough when the arithmetic says so.
      FamilyMember m = fx.member("One box over", 40);
      Dto.AlertView a = only(check(m, fx.taking(m, "Brufen 400", 4)), "SALT_DAILY_TOTAL");
      assertThat(a.dailyTotal().value()).isEqualTo(1600.0);
      assertThat(a.colour()).isEqualTo("red");
    }

    @Test
    void staysQuietWhenATotalIsNowhereNearTheLimit() {
      // Two paracetamol boxes taken once a day each is 1150 of 3000. The
      // duplicate warning covers it; repeating the point as arithmetic adds
      // nothing.
      FamilyMember m = fx.member("Well under", 40);
      List<Dto.AlertView> alerts = check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.taking(m, "Calpol 500", 1));
      assertThat(titles(alerts, "SALT_DAILY_TOTAL")).isEmpty();
      assertThat(titles(alerts, "DUPLICATE_SALT")).hasSize(1);
    }

    @Test
    void saysNothingForASaltWithNoCeilingOnRecord() {
      // Most salts have no sensible single number, and inventing one would be
      // worse than leaving the check to the doctor.
      FamilyMember m = fx.member("No ceiling", 40);
      assertThat(titles(check(m,
          fx.taking(m, "Telma 40", 1),
          fx.taking(m, "Telmikind 40", 1)), "SALT_DAILY_TOTAL")).isEmpty();
    }

    @Test
    void doesNotAddInternationalUnitsIntoAMilligramTotal() {
      // Shelcal carries cholecalciferol in IU. An IU is not a mass, so it can
      // never be compared against a milligram ceiling.
      FamilyMember m = fx.member("Mixed units", 40);
      List<Dto.AlertView> alerts = check(m,
          fx.taking(m, "Shelcal 500", 2),
          fx.taking(m, "Shelcal 500", 2));
      assertThat(alerts).filteredOn(a -> a.type().equals("SALT_DAILY_TOTAL"))
          .allSatisfy(a -> assertThat(a.dailyTotal().unit()).isEqualTo("mg"));
    }

    @Test
    void showsTheArithmeticInBothLanguages() {
      FamilyMember m = fx.member("Arithmetic", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Dolo 650", 2),
          fx.taking(m, "Sinarest", 3)), "SALT_DAILY_TOTAL");

      // A total with no workings is not something a family can check or a
      // doctor can question.
      assertThat(a.explanation().get("en").body())
          .contains("Dolo 650 (650 mg x 2/day)")
          .contains("Sinarest (500 mg x 3/day)");
      assertThat(a.explanation().get("hi").body())
          .contains("Dolo 650 (650 mg x 2/दिन)")
          .doesNotContain("/day");
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("pairs of salts")
  class Pairs {

    @Test
    void reportsARecordedInteractionWithItsSeverityAndMechanism() {
      FamilyMember m = fx.member("Known pair", 40);
      Dto.AlertView a = only(check(m,
          fx.taking(m, "Ecosprin 75", 1),
          fx.taking(m, "Brufen 400", 1)), "INTERACTION_KNOWN");

      assertThat(a.source()).isEqualTo("dataset");
      assertThat(a.datasetName()).isEqualTo("DDInter 2.0");
      assertThat(a.severity()).isEqualTo("major");
      assertThat(a.colour()).isEqualTo("red");
      assertThat(a.mechanism()).contains("platelet");
      assertThat(a.probability()).as("a recorded fact carries no probability").isNull();
      assertThat(a.explanation().get("hi").body())
          .as("the mechanism is translated, not left in English")
          .contains("प्लेटलेट");
    }

    @Test
    void gradesAModerateInteractionAmberAndAMinorOneGreen() {
      FamilyMember moderate = fx.member("Moderate pair", 40);
      assertThat(only(check(moderate,
          fx.taking(moderate, "Telma 40", 1),
          fx.taking(moderate, "Brufen 400", 1)), "INTERACTION_KNOWN").colour())
          .isEqualTo("yellow");

      FamilyMember minor = fx.member("Minor pair", 40);
      assertThat(only(check(minor,
          fx.taking(minor, "Glycomet 500", 2),
          fx.taking(minor, "Amaryl 2", 1)), "INTERACTION_KNOWN").colour())
          .isEqualTo("green");
    }

    @Test
    void aRecordedFactBeatsThePrediction() {
      // Aspirin and ibuprofen are both NSAIDs, so the model would flag them
      // too. The dataset is asked first, and what comes back is a fact rather
      // than a guess, with no probability attached to muddy it.
      FamilyMember m = fx.member("Both would fire", 40);
      List<Dto.AlertView> alerts = check(m,
          fx.taking(m, "Ecosprin 75", 1),
          fx.taking(m, "Brufen 400", 1));

      assertThat(alerts).filteredOn(a -> a.type().equals("INTERACTION_PREDICTED")).isEmpty();
      assertThat(only(alerts, "INTERACTION_KNOWN").source()).isEqualTo("dataset");
    }

    @Test
    void labelsAPredictionAsAPredictionAndKeepsItAmber() {
      // Warfarin and clopidogrel both thin the blood, by different mechanisms.
      // The seeded interaction rows are a sample of DDInter rather than all of
      // it, and this pair is not among them — which is precisely the gap the
      // predictor exists to cover, so it is the pair worth testing it on.
      FamilyMember m = fx.member("Predicted pair", 40);
      List<Dto.AlertView> predicted = check(m,
          fx.taking(m, "Warf 5", 1),
          fx.taking(m, "Clopilet 75", 1)).stream()
          .filter(a -> a.type().equals("INTERACTION_PREDICTED"))
          .toList();

      assertThat(predicted).hasSize(1);
      Dto.AlertView a = predicted.get(0);
      assertThat(a.source()).isEqualTo("ai");
      assertThat(a.model()).isNotBlank();
      assertThat(a.probability()).isGreaterThan(RiskPredictionService.THRESHOLD);
      assertThat(a.threshold()).isEqualTo(RiskPredictionService.THRESHOLD);
      // Red is for what a dataset or a rule establishes, never for a guess.
      assertThat(a.colour()).isEqualTo("yellow");
      assertThat(a.mechanism()).isNull();
      assertThat(a.explanation().get("en").title()).contains("AI prediction");
      assertThat(a.explanation().get("en").body())
          .contains("not listed in the interaction dataset")
          .contains("not proof of safety");
    }

    @Test
    void collapsesOnePairOfSaltsIntoOneLineHoweverManyBoxesItCameFrom() {
      // Two strips of Combiflam and two of Telma are four brand pairs carrying
      // the same ibuprofen and telmisartan question. There is one thing to ask
      // the doctor, so there is one alert.
      FamilyMember m = fx.member("Four brand pairs", 40);
      List<Dto.AlertView> alerts = check(m,
          fx.taking(m, "Combiflam", 1),
          fx.taking(m, "Combiflam", 1),
          fx.taking(m, "Telma 40", 1),
          fx.taking(m, "Telma 40", 1));

      assertThat(titles(alerts, "INTERACTION_KNOWN"))
          .containsExactly("Ibuprofen with Telmisartan");
    }

    @Test
    void namesThePairTheSameWayRoundWhicheverOrderTheBoxesAreIn() {
      FamilyMember a = fx.member("Order one", 40);
      FamilyMember b = fx.member("Order two", 40);

      String first = only(check(a,
          fx.taking(a, "Telma 40", 1),
          fx.taking(a, "Brufen 400", 1)), "INTERACTION_KNOWN")
          .explanation().get("en").title();
      String second = only(check(b,
          fx.taking(b, "Brufen 400", 1),
          fx.taking(b, "Telma 40", 1)), "INTERACTION_KNOWN")
          .explanation().get("en").title();

      assertThat(first).isEqualTo(second);
    }

    @Test
    void doesNotReportASaltAsInteractingWithItself() {
      FamilyMember m = fx.member("Same salt", 40);
      List<Dto.AlertView> alerts = check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.taking(m, "Calpol 500", 1));
      assertThat(alerts).noneMatch(a -> a.type().startsWith("INTERACTION"));
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("recorded allergies")
  class Allergies {

    @Test
    void flagsAMedicineContainingASaltTheMemberReactsTo() {
      FamilyMember m = fx.member("Allergic", 70, List.of("Cefixime"));
      Dto.AlertView a = only(check(m, fx.taking(m, "Zifi 200", 2)), "ALLERGY");

      assertThat(a.colour()).isEqualTo("red");
      assertThat(a.saltNames()).containsExactly("Cefixime");
      assertThat(a.medicines()).containsExactly("Zifi 200");
      assertThat(a.explanation().get("en").body())
          .as("the medicine is still shown rather than hidden")
          .contains("still shown here so nothing is hidden");
    }

    @Test
    void saysNothingWhenTheAllergicSaltIsNotOnTheList() {
      FamilyMember m = fx.member("Allergic but clear", 70, List.of("Cefixime"));
      assertThat(titles(check(m, fx.taking(m, "Dolo 650", 1)), "ALLERGY")).isEmpty();
    }

    @Test
    void raisesOneLinePerMedicineRatherThanOnePerSalt() {
      // Unlike an interaction, each box containing the salt is its own thing to
      // sort out with the pharmacist.
      FamilyMember m = fx.member("Allergic twice", 70, List.of("Paracetamol"));
      assertThat(titles(check(m,
          fx.taking(m, "Dolo 650", 1),
          fx.taking(m, "Calpol 500", 1)), "ALLERGY")).hasSize(2);
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("the checks that start at 65")
  class ElderlyRules {

    @Test
    void doNotApplyBelowSixtyFive() {
      FamilyMember young = fx.member("Younger", 64);
      assertThat(titles(check(young,
          fx.taking(young, "Alprax 0.25", 1, true),
          fx.taking(young, "Pan 40", 1, true),
          fx.taking(young, "Brufen 400", 1)), "ELDERLY_RULE")).isEmpty();
    }

    @Test
    void applyFromSixtyFive() {
      FamilyMember m = fx.member("Just old enough", 65);
      assertThat(check(m, fx.taking(m, "Alprax 0.25", 1, true)))
          .filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .extracting(Dto.AlertView::ruleKey)
          .containsExactly("stopp-benzo");
    }

    @Test
    void readAsAPromptToReviewRatherThanAVerdict() {
      FamilyMember m = fx.member("Benzo", 72);
      Dto.AlertView a = only(check(m, fx.taking(m, "Alprax 0.25", 1, true)), "ELDERLY_RULE");

      assertThat(a.colour()).isEqualTo("yellow");
      assertThat(a.ruleSet()).isEqualTo("STOPP v3 (subset)");
      assertThat(a.explanation().get("en").body())
          .contains("a prompt for review, not a verdict");
      assertThat(a.explanation().get("en").title()).contains("age 72");
    }

    @Test
    void acidReducersAreOnlyRaisedWhenTheyAreRunningLongTerm() {
      // A defined course is exactly what these are for, so a short one is not
      // worth mentioning.
      FamilyMember course = fx.member("Short course", 70);
      assertThat(check(course, fx.taking(course, "Pan 40", 1, false)))
          .filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .isEmpty();

      FamilyMember longTerm = fx.member("Years of it", 70);
      assertThat(check(longTerm, fx.taking(longTerm, "Pan 40", 1, true)))
          .filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .extracting(Dto.AlertView::ruleKey)
          .containsExactly("stopp-ppi");
    }

    @Test
    void theNsaidRuleSaysWhetherStomachProtectionIsOnTheList() {
      FamilyMember unprotected = fx.member("No cover", 70);
      assertThat(only(check(unprotected, fx.taking(unprotected, "Brufen 400", 1)), "ELDERLY_RULE")
          .explanation().get("en").body())
          .contains("No stomach-protecting medicine appears on this list");

      FamilyMember covered = fx.member("Covered", 70);
      List<Dto.AlertView> alerts = check(covered,
          fx.taking(covered, "Brufen 400", 1),
          fx.taking(covered, "Pan 40", 1, false));
      assertThat(alerts).filteredOn(a -> "stopp-nsaid".equals(a.ruleKey()))
          .singleElement()
          .satisfies(a -> assertThat(a.explanation().get("en").body())
              .contains("An acid-reducing medicine is already on the list"));
    }

    @Test
    void lowDoseAspirinIsNotTreatedAsAPainkiller() {
      // Ecosprin 75 is taken to protect the heart, not for pain, so the NSAID
      // painkiller rule must not fire on it alone.
      FamilyMember m = fx.member("Heart aspirin", 72);
      assertThat(check(m, fx.taking(m, "Ecosprin 75", 1, true)))
          .filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .isEmpty();
    }

    @Test
    void oneLinePerRuleRatherThanOnePerBox() {
      FamilyMember m = fx.member("Two painkillers", 72);
      assertThat(check(m,
          fx.taking(m, "Brufen 400", 1),
          fx.taking(m, "Zerodol SP", 1)))
          .filteredOn(a -> a.type().equals("ELDERLY_RULE"))
          .extracting(Dto.AlertView::ruleKey)
          .containsExactly("stopp-nsaid");
    }
  }

  /* ================================================================= */

  @Nested
  @DisplayName("counts and empty lists")
  class Counts {

    @Test
    void anEmptyMedicineListProducesNothing() {
      assertThat(check(fx.member("Nobody", 30))).isEmpty();
    }

    @Test
    void aSingleHarmlessMedicineProducesNothing() {
      FamilyMember m = fx.member("One medicine", 30);
      assertThat(check(m, fx.taking(m, "Dolo 650", 1))).isEmpty();
    }

    @Test
    void countsAddUpToTheTotal() {
      FamilyMember dadaji = fx.member("Ram Prasad Gupta", 72);
      List<Dto.AlertView> alerts = check(dadaji,
          fx.taking(dadaji, "Telma 40", 1, true),
          fx.taking(dadaji, "Ecosprin 75", 1, true),
          fx.taking(dadaji, "Pan 40", 1, true),
          fx.taking(dadaji, "Combiflam", 2),
          fx.taking(dadaji, "Dolo 650", 2),
          fx.taking(dadaji, "Sinarest", 3),
          fx.taking(dadaji, "Alprax 0.25", 1, true));

      Dto.AlertCounts counts = safety.counts(alerts);
      assertThat(counts.total()).isEqualTo(alerts.size());
      assertThat(counts.red() + counts.yellow() + counts.green()).isEqualTo(counts.total());
      assertThat(counts.red()).isEqualTo(3);
      assertThat(counts.yellow()).isEqualTo(5);
    }

    @Test
    void alertIdsAreUniqueWithinOneCheck() {
      FamilyMember dadaji = fx.member("Ram Prasad Gupta", 72);
      List<Dto.AlertView> alerts = check(dadaji,
          fx.taking(dadaji, "Telma 40", 1, true),
          fx.taking(dadaji, "Ecosprin 75", 1, true),
          fx.taking(dadaji, "Combiflam", 2),
          fx.taking(dadaji, "Dolo 650", 2),
          fx.taking(dadaji, "Sinarest", 3));

      assertThat(alerts).extracting(Dto.AlertView::id).doesNotHaveDuplicates();
    }
  }
}
