package com.dawacheck.service;

import com.dawacheck.config.AppProperties;
import com.dawacheck.domain.Salt;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blood thinners, which the heuristic used to miss.
 *
 * It asked whether the two salts had a class in common. Warfarin is an
 * anticoagulant and clopidogrel an antiplatelet, so they had nothing in common
 * by that test and scored 0.30 — well below the threshold, meaning nothing was
 * shown at all for one of the combinations most likely to put someone in
 * hospital. These tests pin the corrected behaviour.
 */
class RiskPredictionBleedingTest {

  private final RiskPredictionService risk =
      new RiskPredictionService(new AppProperties(), RestClient.create());

  private static Salt salt(String name, String atc, String classes) {
    Salt s = new Salt();
    s.id = (long) Math.abs(name.hashCode() % 1000);
    s.name = name;
    s.atc = atc;
    s.drugClasses = classes;
    return s;
  }

  private static final Salt WARFARIN = salt("Warfarin", "B01AA03", "anticoagulant");
  private static final Salt CLOPIDOGREL = salt("Clopidogrel", "B01AC04", "antiplatelet");
  private static final Salt ASPIRIN = salt("Aspirin", "B01AC06", "nsaid,antiplatelet");
  private static final Salt PARACETAMOL = salt("Paracetamol", "N02BE01", "analgesic");

  @Test
  void anAnticoagulantWithAnAntiplateletScoresAboveTheThreshold() {
    assertThat(risk.predict(WARFARIN, CLOPIDOGREL).probability())
        .isGreaterThan(RiskPredictionService.THRESHOLD);
  }

  @Test
  void twoAntiplateletsScoreAboveTheThreshold() {
    assertThat(risk.predict(ASPIRIN, CLOPIDOGREL).probability())
        .isGreaterThan(RiskPredictionService.THRESHOLD);
  }

  @Test
  void oneBloodThinnerOnItsOwnIsNotFlagged() {
    // The risk is in the combination. A single blood thinner is a prescription,
    // not a problem, and flagging it would train the family to ignore amber.
    assertThat(risk.predict(WARFARIN, PARACETAMOL).probability())
        .isLessThan(RiskPredictionService.THRESHOLD);
  }

  @Test
  void theOrderOfTheTwoSaltsDoesNotChangeTheScore() {
    assertThat(risk.predict(WARFARIN, CLOPIDOGREL).probability())
        .isEqualTo(risk.predict(CLOPIDOGREL, WARFARIN).probability());
  }
}
