package com.dawacheck.service;

import com.dawacheck.config.AppProperties;
import com.dawacheck.domain.Salt;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The heuristic standing in for the trained model.
 *
 * What is being asserted here is not that the numbers are clinically right —
 * they are a stand-in and the application says so — but that the behaviour
 * around them holds: the threshold means something, a score cannot run away
 * past certainty, and the service is honest about not being a trained model.
 */
class RiskPredictionServiceTest {

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

  @Test
  void saysItIsNotTheTrainedModelWhenNoServiceIsConfigured() {
    // This is what the admin page and the model card report. If it ever lied,
    // a demo would look like a validated classifier.
    assertThat(risk.usingTrainedModel()).isFalse();
    assertThat(risk.modelName()).contains("heuristic");
  }

  @Test
  void twoPainkillersOfTheSameFamilyScoreAboveTheThreshold() {
    Dto.PredictResult r = risk.predict(
        salt("Ibuprofen", "M01AE01", "nsaid"),
        salt("Diclofenac", "M01AB05", "nsaid"));
    assertThat(r.probability()).isGreaterThan(RiskPredictionService.THRESHOLD);
    assertThat(r.severity()).isIn("moderate", "major");
  }

  @Test
  void twoUnrelatedSaltsStayBelowTheThreshold() {
    // Below the threshold nothing is shown at all, which is the point: the model
    // must not fill the screen with amber for every pair in the cabinet.
    Dto.PredictResult r = risk.predict(
        salt("Paracetamol", "N02BE01", "analgesic,antipyretic"),
        salt("Cefixime", "J01DD08", "antibiotic"));
    assertThat(r.probability()).isLessThan(RiskPredictionService.THRESHOLD);
    assertThat(r.severity()).isEqualTo("minor");
  }

  @Test
  void twoSedatingMedicinesScoreAboveTheThreshold() {
    Dto.PredictResult r = risk.predict(
        salt("Alprazolam", "N05BA12", "benzodiazepine,sedating"),
        salt("Chlorpheniramine", "R06AB04", "antihistamine,sedating"));
    assertThat(r.probability()).isGreaterThan(RiskPredictionService.THRESHOLD);
  }

  @Test
  void neverClaimsCertainty() {
    // Every class in common at once. A model that returned 1.0 would be
    // presented to a family as a certainty, which no prediction here is.
    Dto.PredictResult r = risk.predict(
        salt("A", "B01AC01", "nsaid,sedating,antiplatelet,anticoagulant,ppi,antidiabetic,"
            + "antihypertensive"),
        salt("B", "B01AC02", "nsaid,sedating,antiplatelet,anticoagulant,ppi,antidiabetic,"
            + "antihypertensive"));
    assertThat(r.probability()).isLessThanOrEqualTo(0.93);
  }

  @Test
  void scoresAPairWithNothingInCommonAtTheFloor() {
    Dto.PredictResult r = risk.predict(salt("A", "", ""), salt("B", "", ""));
    assertThat(r.probability()).isEqualTo(0.18);
  }

  @Test
  void handlesAMissingSalt() {
    assertThat(risk.predict(null, salt("A", "", "nsaid")).probability()).isZero();
    assertThat(risk.predict(salt("A", "", "nsaid"), null).probability()).isZero();
  }

  @Test
  void readsTheClassListOffASalt() {
    assertThat(RiskPredictionService.classes(salt("A", "", "nsaid, antiplatelet")))
        .containsExactly("nsaid", "antiplatelet");
    assertThat(RiskPredictionService.classes(salt("A", "", ""))).isEmpty();
  }
}
