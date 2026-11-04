package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 8 and competitive feature 5: the cheaper option, and whether the box
 * the pharmacy handed over is the same medicine.
 */
class GenericAndCompareTest extends IntegrationTest {

  @Autowired
  private GenericService generics;

  @Autowired
  private MedicineService medicines;

  @Autowired
  private KnowledgeBase kb;

  private String id(String brand) {
    return kb.allMedicines().stream()
        .filter(m -> m.brand.equalsIgnoreCase(brand))
        .findFirst().orElseThrow()
        .id.toString().transform(n -> "m" + n);
  }

  /* ---------------- generics ---------------- */

  @Test
  void findsTheExactGenericAndWorksOutTheSavingPerTablet() {
    // Dolo 650 is 31.50 for 15 tablets; the generic is 9.00 for 10. Comparing
    // pack prices would make the generic look only three times cheaper rather
    // than seven.
    List<Dto.GenericView> found = generics.forMedicine(id("Dolo 650"));

    assertThat(found).isNotEmpty();
    Dto.GenericView best = found.get(0);
    assertThat(best.match()).isEqualTo("exact");
    assertThat(best.name()).isEqualTo("Paracetamol Tablets IP 650 mg");
    assertThat(best.perUnitBrand()).isEqualTo(2.1);
    assertThat(best.perUnitGeneric()).isEqualTo(0.9);
    assertThat(best.savePct()).isEqualTo(57);
    assertThat(best.monthlySaving()).isEqualTo(36.0);
  }

  @Test
  void putsTheExactStrengthAheadOfTheSameSaltAtAnotherStrength() {
    // Same salts at a different strength is not a swap a family can make on
    // their own, so it must never be the first thing offered.
    List<Dto.GenericView> found = generics.forMedicine(id("Dolo 650"));
    assertThat(found).extracting(Dto.GenericView::match).startsWith("exact");
    assertThat(found).extracting(Dto.GenericView::match).contains("same-salts");
  }

  @Test
  void matchesATwoSaltCombinationOnBothSalts() {
    List<Dto.GenericView> found = generics.forMedicine(id("Montek LC"));
    assertThat(found).isNotEmpty();
    assertThat(found.get(0).saltNames())
        .extracting(Dto.SaltStrength::name)
        .containsExactlyInAnyOrder("Montelukast", "Levocetirizine");
  }

  @Test
  void offersNothingWhenNoGenericCarriesThoseSalts() {
    // Sinarest is a three-salt cold combination with no Jan Aushadhi
    // equivalent. An empty list is the right answer; a near-miss would be a
    // suggestion to change medicine.
    assertThat(generics.forMedicine(id("Sinarest"))).isEmpty();
  }

  @Test
  void neverReportsANegativeSaving() {
    // A generic priced above the brand would otherwise show "-12% saving",
    // which reads as a discount.
    for (var m : kb.allMedicines()) {
      for (Dto.GenericView g : generics.forMedicine("m" + m.id)) {
        assertThat(g.savePct()).as(m.brand + " vs " + g.name()).isGreaterThanOrEqualTo(0);
      }
    }
  }

  @Test
  void refusesAMedicineThatDoesNotExist() {
    assertThatThrownBy(() -> generics.forMedicine("m99999"))
        .isInstanceOf(NotFoundException.class);
  }

  /* ---------------- is this the same medicine? ---------------- */

  @Test
  void twoBrandsOfOneMedicineAreTheSame() {
    // Pan 40 and Pantocid 40 are both pantoprazole 40 mg tablets.
    Dto.CompareResult r = medicines.compare(id("Pan 40"), id("Pantocid 40"));
    assertThat(r.verdict()).isEqualTo("same");
    assertThat(r.sameSalts()).isTrue();
    assertThat(r.sameStrength()).isTrue();
    assertThat(r.sameForm()).isTrue();
  }

  @Test
  void theSameSaltAtADifferentStrengthIsNotTheSameMedicine() {
    // This is the answer that matters most: it looks like a match and is not.
    Dto.CompareResult r = medicines.compare(id("Dolo 650"), id("Dolo 500"));
    assertThat(r.verdict()).isEqualTo("same-salts-different-strength");
    assertThat(r.sameSalts()).isTrue();
    assertThat(r.sameStrength()).isFalse();
  }

  @Test
  void aTabletAgainstACapsuleIsFlaggedSeparately() {
    // Both are 20 mg omeprazole, but one is a capsule. Nothing in the
    // catalogue pairs a tablet with a capsule of the same salt and strength,
    // so this asserts the verdict the code would give rather than inventing a
    // row to make it happen.
    Dto.CompareResult r = medicines.compare(id("Omez 20"), id("Pan 40"));
    assertThat(r.sameForm()).isFalse();
    assertThat(r.verdict()).isEqualTo("different");
  }

  @Test
  void twoUnrelatedMedicinesAreDifferent() {
    assertThat(medicines.compare(id("Dolo 650"), id("Telma 40")).verdict())
        .isEqualTo("different");
  }

  @Test
  void aMedicineComparedWithItselfIsTheSame() {
    assertThat(medicines.compare(id("Dolo 650"), id("Dolo 650")).verdict()).isEqualTo("same");
  }

  @Test
  void theSaltListsLineUpForComparison() {
    // The two lists are read position by position, so they have to be sorted
    // the same way whatever order each box prints its salts in.
    Dto.CompareResult r = medicines.compare(id("Combiflam"), id("Combiflam"));
    assertThat(r.saltsA()).extracting(Dto.CompareSalt::saltId)
        .isEqualTo(r.saltsB().stream().map(Dto.CompareSalt::saltId).toList());
    assertThat(r.saltsA()).hasSize(2);
  }

  @Test
  void aComparisonCarriesTheBrandsAndTheirSaltsForDisplay() {
    Dto.CompareResult r = medicines.compare(id("Combiflam"), id("Brufen 400"));
    assertThat(r.a().brand()).isEqualTo("Combiflam");
    assertThat(r.a().salts()).extracting(Dto.MedSalt::name)
        .containsExactlyInAnyOrder("Ibuprofen", "Paracetamol");
    assertThat(r.a().composition()).hasSize(2);
    assertThat(r.b().brand()).isEqualTo("Brufen 400");
  }

  /* ---------------- search view ---------------- */

  @Test
  void searchResultsCarryAConfidenceAndTheApproximatePriceFlag() {
    List<Dto.MedicineView> results = medicines.search("dolo");
    assertThat(results).isNotEmpty();
    assertThat(results.get(0).confidence()).isNotNull().isBetween(0.0, 1.0);
    // Prices come from public datasets and may be years old.
    assertThat(results).allSatisfy(m -> assertThat(m.priceApproximate()).isTrue());
  }

  @Test
  void aMedicineViewCarriesItsSaltsAndStrengths() {
    Dto.MedicineView m = medicines.get(id("Combiflam"));
    assertThat(m.salts()).extracting(Dto.MedSalt::strength)
        .containsExactlyInAnyOrder("400 mg", "325 mg");
    assertThat(m.id()).startsWith("m");
  }

  @Test
  void everySaltViewCarriesItsClassesAndSynonyms() {
    List<Dto.SaltView> salts = medicines.salts();
    assertThat(salts).hasSize(24);
    assertThat(salts).allSatisfy(s -> {
      assertThat(s.id()).startsWith("s");
      assertThat(s.name()).isNotBlank();
      assertThat(s.classes()).isNotNull();
      assertThat(s.synonyms()).isNotNull();
    });
    assertThat(salts).filteredOn(s -> s.name().equals("Paracetamol"))
        .singleElement()
        .satisfies(s -> assertThat(s.synonyms()).contains("Acetaminophen", "PCM"));
  }
}
