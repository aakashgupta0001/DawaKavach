package com.dawacheck.service;

import com.dawacheck.domain.GenericProduct;
import com.dawacheck.domain.Medicine;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.GenericProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Module 8, the generic alternatives finder.
 *
 * A Jan Aushadhi product matches a brand when it carries the same salts. It
 * matches exactly when the strengths are the same too, and those two cases are
 * reported separately: same salts at a different strength is not a swap the
 * family can make on their own.
 *
 * The saving is worked out per tablet rather than per pack, because the packs
 * are different sizes and comparing pack prices would flatter whichever one
 * happens to be smaller.
 */
@Service
public class GenericService {

  private final GenericProductRepository generics;
  private final KnowledgeBase kb;
  private final ViewMapper view;

  public GenericService(GenericProductRepository generics, KnowledgeBase kb, ViewMapper view) {
    this.generics = generics;
    this.kb = kb;
    this.view = view;
  }

  @Transactional(readOnly = true)
  public List<Dto.GenericView> forMedicine(String medicineApiId) {
    Medicine med = kb.requireMedicine(Ids.decode(Ids.MEDICINE, medicineApiId));
    String wantSalts = KnowledgeBase.saltIdKey(med);
    String wantExact = KnowledgeBase.saltStrengthKey(med);

    double perUnitBrand = (med.price == null ? 0 : med.price) / Strengths.packCount(med.pack);

    List<Dto.GenericView> out = new ArrayList<>();
    for (GenericProduct g : generics.findBySaltIds(wantSalts)) {
      String match = wantExact.equals(g.saltKey) ? "exact" : "same-salts";
      out.add(view.generic(g, match, perUnitBrand));
    }

    // Exact strength first, then the biggest saving within each group.
    out.sort(Comparator
        .comparing((Dto.GenericView g) -> "exact".equals(g.match()) ? 0 : 1)
        .thenComparing(Comparator.comparingInt(Dto.GenericView::savePct).reversed()));
    return out;
  }
}
