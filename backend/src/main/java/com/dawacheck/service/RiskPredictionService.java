package com.dawacheck.service;

import com.dawacheck.config.AppProperties;
import com.dawacheck.domain.Salt;
import com.dawacheck.dto.Dto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Module 6, the interaction risk predictor.
 *
 * The trained model of section 11 is a Morgan-fingerprint and ATC-class
 * classifier served by FastAPI at {@code POST /predict}. Point
 * {@code dawacheck.predict.url} at it and this service calls it.
 *
 * With no URL configured it falls back to the heuristic below, which scores a
 * pair on the pharmacological classes the two salts share. The heuristic is not
 * a model and is not presented as one: {@link #usingTrainedModel()} is reported
 * on the admin page and in the model card so nobody mistakes the demo for a
 * trained classifier.
 *
 * Either way a prediction only ever produces an amber alert, and only for pairs
 * the interaction dataset does not already cover. A recorded fact always wins
 * over a guess.
 */
@Service
public class RiskPredictionService {

  private static final Logger log = LoggerFactory.getLogger(RiskPredictionService.class);

  /** Below this probability nothing is shown at all. */
  public static final double THRESHOLD = 0.55;

  private final AppProperties props;
  private final RestClient http;

  /** Remote failures are logged once, not on every pair of a long medicine list. */
  private volatile boolean remoteWarned = false;

  public RiskPredictionService(AppProperties props, RestClient http) {
    this.props = props;
    this.http = http;
  }

  public boolean usingTrainedModel() {
    return props.getPredict().isRemote();
  }

  public String modelName() {
    return usingTrainedModel() ? "interaction-risk (FastAPI)" : "interaction-risk v0.3 (heuristic)";
  }

  public Dto.PredictResult predict(Salt a, Salt b) {
    if (a == null || b == null) {
      return new Dto.PredictResult(0, "minor");
    }
    if (usingTrainedModel()) {
      Dto.PredictResult remote = askService(a, b);
      if (remote != null) {
        return remote;
      }
    }
    return heuristic(a, b);
  }

  private Dto.PredictResult askService(Salt a, Salt b) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> body = http.post()
          .uri(props.getPredict().getUrl())
          .body(Map.of("saltA", a.name, "saltB", b.name, "atcA", a.atc, "atcB", b.atc))
          .retrieve()
          .body(Map.class);
      if (body == null || !(body.get("probability") instanceof Number p)) {
        return null;
      }
      double probability = p.doubleValue();
      Object severity = body.get("severity");
      return new Dto.PredictResult(round2(probability),
          severity instanceof String s ? s : grade(probability));
    } catch (RuntimeException e) {
      if (!remoteWarned) {
        remoteWarned = true;
        log.warn("Risk model at {} did not answer; falling back to the built-in heuristic: {}",
            props.getPredict().getUrl(), e.getMessage());
      }
      return null;
    }
  }

  /**
   * Readable stand-in for the trained model. Two salts that act in the same way
   * are more likely to interact, so the score is built from the classes they
   * share, with a small nudge when their ATC codes sit in the same group.
   */
  private Dto.PredictResult heuristic(Salt a, Salt b) {
    Set<String> shared = new LinkedHashSet<>(classes(a));
    shared.retainAll(classes(b));

    double p = 0.18;
    if (shared.contains("nsaid")) {
      p += 0.42;
    }
    if (shared.contains("sedating")) {
      // Enough on its own to clear the threshold. Two medicines that both cause
      // drowsiness is the combination the elderly-falls literature keeps coming
      // back to, and it would be a strange screen that stayed silent about a
      // pair purely because the dataset had not got round to listing it.
      p += 0.40;
    }
    // Bleeding risk does not need the two salts to be in the same class.
    // Warfarin is an anticoagulant and clopidogrel an antiplatelet; they thin
    // the blood by different mechanisms, and taking both together still raises
    // the chance of a bleed. Asking for an identical class name here missed
    // exactly the pairs that matter most, so the test is whether each salt
    // reduces clotting at all, not whether they do it the same way.
    if (reducesClotting(a) && reducesClotting(b)) {
      p += 0.40;
    }
    if (shared.contains("ppi")) {
      p += 0.20;
    }
    if (shared.contains("antidiabetic")) {
      p += 0.22;
    }
    if (shared.contains("antihypertensive")) {
      p += 0.16;
    }
    if (sameAtcGroup(a, b)) {
      p += 0.12;
    }
    p = Math.min(0.93, p);
    return new Dto.PredictResult(round2(p), grade(p));
  }

  private static boolean reducesClotting(Salt s) {
    Set<String> classes = classes(s);
    return classes.contains("antiplatelet") || classes.contains("anticoagulant");
  }

  private static boolean sameAtcGroup(Salt a, Salt b) {
    return a.atc != null && b.atc != null && a.atc.length() >= 3 && b.atc.length() >= 3
        && a.atc.substring(0, 3).equals(b.atc.substring(0, 3));
  }

  private static String grade(double p) {
    return p > 0.75 ? "major" : p > THRESHOLD ? "moderate" : "minor";
  }

  public static Set<String> classes(Salt s) {
    if (s.drugClasses == null || s.drugClasses.isBlank()) {
      return Set.of();
    }
    return new LinkedHashSet<>(Arrays.stream(s.drugClasses.split(","))
        .map(String::trim)
        .filter(x -> !x.isEmpty())
        .toList());
  }

  private static double round2(double n) {
    return Math.round(n * 100.0) / 100.0;
  }
}
