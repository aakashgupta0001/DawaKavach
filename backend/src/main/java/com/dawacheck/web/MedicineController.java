package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.GenericService;
import com.dawacheck.service.MedicineService;
import com.dawacheck.service.RiskPredictionService;
import com.dawacheck.service.KnowledgeBase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module 2 and module 8 as the pages call them, plus the model behind module 6. */
@RestController
@RequestMapping("/api")
public class MedicineController {

  private final MedicineService medicines;
  private final GenericService generics;
  private final RiskPredictionService risk;
  private final KnowledgeBase kb;

  public MedicineController(MedicineService medicines, GenericService generics,
      RiskPredictionService risk, KnowledgeBase kb) {
    this.medicines = medicines;
    this.generics = generics;
    this.risk = risk;
    this.kb = kb;
  }

  @GetMapping("/medicines/search")
  public List<Dto.MedicineView> search(@RequestParam("q") String query) {
    return medicines.search(query);
  }

  @GetMapping("/salts")
  public List<Dto.SaltView> salts() {
    return medicines.salts();
  }

  /** Before {@code /medicines/{id}}, so "compare" is not read as an id. */
  @GetMapping("/medicines/compare")
  public Dto.CompareResult compare(@RequestParam("a") String a, @RequestParam("b") String b) {
    return medicines.compare(a, b);
  }

  @GetMapping("/medicines/{id}")
  public Dto.MedicineView get(@PathVariable String id) {
    return medicines.get(id);
  }

  @GetMapping("/medicines/{id}/generics")
  public List<Dto.GenericView> generics(@PathVariable String id) {
    return generics.forMedicine(id);
  }

  /**
   * Scores one pair of salts.
   *
   * The frontend uses this to explain an amber prediction on demand. It is the
   * same call the safety check makes internally, exposed so the number shown
   * next to a prediction can be checked rather than taken on trust.
   */
  @PostMapping("/predict")
  public Dto.PredictResult predict(@RequestBody Dto.PredictRequest req) {
    return risk.predict(
        kb.requireSalt(com.dawacheck.dto.Ids.decode(com.dawacheck.dto.Ids.SALT, req.saltA())),
        kb.requireSalt(com.dawacheck.dto.Ids.decode(com.dawacheck.dto.Ids.SALT, req.saltB())));
  }
}
