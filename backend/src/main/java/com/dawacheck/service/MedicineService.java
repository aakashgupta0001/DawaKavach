package com.dawacheck.service;

import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Module 2 as the pages use it: search, one medicine, and the brand comparison. */
@Service
public class MedicineService {

  private static final int SEARCH_LIMIT = 8;

  private final KnowledgeBase kb;
  private final ViewMapper view;

  public MedicineService(KnowledgeBase kb, ViewMapper view) {
    this.kb = kb;
    this.view = view;
  }

  @Transactional(readOnly = true)
  public List<Dto.MedicineView> search(String query) {
    List<Dto.MedicineView> out = new ArrayList<>();
    for (KnowledgeBase.Match match : kb.search(query, SEARCH_LIMIT)) {
      out.add(view.medicine(match.medicine()).withConfidence(match.score()));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public Dto.MedicineView get(String apiId) {
    return view.medicine(kb.requireMedicine(Ids.decode(Ids.MEDICINE, apiId)));
  }

  @Transactional(readOnly = true)
  public List<Dto.SaltView> salts() {
    return kb.allSalts().stream().map(view::salt).toList();
  }

  /**
   * Competitive feature 5, "is this the same medicine?".
   *
   * The pharmacy hands over a box with a name nobody recognises and the family
   * has no way to tell whether it replaces what the doctor wrote. Three things
   * decide it, and the answer says which of them differ:
   *
   * <ul>
   *   <li>the same salts, in the same strengths, in the same form: interchangeable
   *       on composition;
   *   <li>the same salts and strengths in a different form: a tablet against a
   *       capsule, which usually matters only for how it is taken;
   *   <li>the same salts at a different strength, which is a different medicine
   *       in the way that counts.
   * </ul>
   */
  @Transactional(readOnly = true)
  public Dto.CompareResult compare(String apiA, String apiB) {
    Medicine a = kb.requireMedicine(Ids.decode(Ids.MEDICINE, apiA));
    Medicine b = kb.requireMedicine(Ids.decode(Ids.MEDICINE, apiB));

    List<Dto.CompareSalt> saltsA = normalise(a);
    List<Dto.CompareSalt> saltsB = normalise(b);

    boolean sameSalts = saltsA.stream().map(Dto.CompareSalt::saltId).toList()
        .equals(saltsB.stream().map(Dto.CompareSalt::saltId).toList());
    boolean sameStrength = sameSalts && allStrengthsEqual(saltsA, saltsB);
    boolean sameForm = a.form != null && a.form.equals(b.form);

    String verdict = sameSalts && sameStrength && sameForm ? "same"
        : sameSalts && sameStrength ? "same-different-form"
            : sameSalts ? "same-salts-different-strength" : "different";

    return new Dto.CompareResult(compareMed(a), compareMed(b), saltsA, saltsB,
        sameSalts, sameStrength, sameForm, verdict);
  }

  private static boolean allStrengthsEqual(List<Dto.CompareSalt> a, List<Dto.CompareSalt> b) {
    for (int i = 0; i < a.size(); i++) {
      if (a.get(i).strength() != b.get(i).strength()) {
        return false;
      }
    }
    return true;
  }

  /** Salts sorted by id, so two medicines can be compared position by position. */
  private List<Dto.CompareSalt> normalise(Medicine m) {
    List<Dto.CompareSalt> salts = new ArrayList<>();
    for (MedicineSalt ms : m.composition) {
      Strengths.Amount amount = Strengths.parse(ms.strength);
      salts.add(new Dto.CompareSalt(ms.salt.name, Ids.encode(Ids.SALT, ms.salt.id),
          amount.value(), amount.unit()));
    }
    salts.sort(Comparator.comparing(Dto.CompareSalt::saltId));
    return salts;
  }

  private Dto.CompareMed compareMed(Medicine m) {
    List<List<String>> composition = new ArrayList<>();
    List<Dto.MedSalt> salts = new ArrayList<>();
    for (MedicineSalt ms : m.composition) {
      composition.add(List.of(Ids.encode(Ids.SALT, ms.salt.id), ms.strength));
      salts.add(new Dto.MedSalt(Ids.encode(Ids.SALT, ms.salt.id), ms.salt.name, ms.strength,
          kb.synonyms(ms.salt.id)));
    }
    return new Dto.CompareMed(Ids.encode(Ids.MEDICINE, m.id), m.brand, m.maker, m.form, m.pack,
        m.price, composition, salts);
  }
}
