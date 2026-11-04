package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 2 from the admin side.
 *
 * The public datasets are not clean, and what matters most here is what the
 * importer refuses: a brand mapped to the wrong salt is worse than a brand that
 * is missing, because a missing one makes the scanner ask the user while a wrong
 * one makes every later check wrong without anyone noticing.
 */
@WithMockUser(username = "aakash@example.com", roles = {"ADMIN"})
@Transactional
class AdminServiceTest extends IntegrationTest {

  @Autowired
  private AdminService admin;

  @Autowired
  private KnowledgeBase kb;

  /**
   * Importing changes the knowledge base every other test reads, so each test
   * here rolls back.
   *
   * Rolling back the database is not enough on its own: the knowledge base
   * keeps its rows in memory, and a snapshot built during the test would hold
   * on to salts and brands that the rollback has since removed. One of those
   * imports adds the warfarin and clopidogrel interaction, which would turn the
   * engine's prediction test into a dataset lookup and fail it for reasons
   * nobody would think to look for here. So the snapshot is dropped afterwards
   * and rebuilt from what actually committed.
   */
  @AfterEach
  void rebuildTheKnowledgeBaseFromWhatCommitted() {
    kb.reload();
  }

  private static MockMultipartFile csv(String name, String... lines) {
    return new MockMultipartFile("file", name, "text/csv",
        String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
  }

  /* ---------------- overview ---------------- */

  @Test
  void reportsWhatIsActuallyLoaded() {
    // The counts come from the tables rather than from an import log, so the
    // admin page always describes the knowledge base as it is.
    Dto.AdminOverview overview = admin.overview();
    assertThat(overview.counts().salts()).isEqualTo(24);
    assertThat(overview.counts().medicines()).isEqualTo(26);
    assertThat(overview.counts().interactions()).isEqualTo(17);
    assertThat(overview.counts().generics()).isEqualTo(14);
    assertThat(overview.datasets()).hasSize(4);
  }

  @Test
  void theModelCardSaysItIsNotATrainedModel() {
    Dto.ModelCard card = admin.overview().model();
    assertThat(card.trainedModel()).isFalse();
    assertThat(card.serving()).contains("heuristic");
    assertThat(card.threshold()).isEqualTo(RiskPredictionService.THRESHOLD);
  }

  @Test
  void theMappingQueueGroupsRepeatedLinesAndCountsThem() {
    // A line twelve families have scanned is a brand missing from the
    // catalogue, and importing it fixes all twelve at once, so the count is
    // what decides the order of work.
    assertThat(admin.mappingQueue())
        .isNotEmpty()
        .isSortedAccordingTo((a, b) -> Long.compare(b.count(), a.count()));
    assertThat(admin.mappingQueue().get(0).count()).isEqualTo(12);
    assertThat(admin.mappingQueue()).allSatisfy(row -> {
      assertThat(row.id()).startsWith("q");
      assertThat(row.detected()).isNotBlank();
      assertThat(row.note()).isNotBlank();
    });
  }

  /* ---------------- import ---------------- */

  @Test
  void refusesAnythingThatIsNotACsv() {
    assertThatThrownBy(() -> admin.importCsv(
        csv("brands.xlsx", "brand", "Dolo 650"), "medicines"))
        .hasMessageContaining("Only .csv");
    assertThatThrownBy(() -> admin.importCsv(null, "medicines"))
        .hasMessageContaining("Choose a CSV");
  }

  @Test
  void refusesAFileWithNothingInIt() {
    // A file of no bytes is the same thing to the user as no file at all, and
    // the message says the thing they can act on.
    assertThatThrownBy(() -> admin.importCsv(csv("empty.csv"), "medicines"))
        .hasMessageContaining("Choose a CSV file");
  }

  @Test
  void refusesATargetTableItDoesNotKnow() {
    // The target decides which table the rows land in. Guessing would be the
    // one mistake there is no recovering from.
    assertThatThrownBy(() -> admin.importCsv(
        csv("x.csv", "brand,composition", "A,Paracetamol 500 mg"), "nonsense"))
        .hasMessageContaining("Unknown import target");
  }

  @Test
  void importsSaltsThenBrandsAndMakesThemSearchableAtOnce() {
    long saltsBefore = admin.overview().counts().salts();

    Dto.ImportResult salts = admin.importCsv(csv("salts.csv",
        "name,atc,classes,daily_ceiling_mg",
        "Ranitidine,A02BA02,h2-blocker,300",
        "Cetirizine,R06AE07,antihistamine,"), "salts");
    assertThat(salts.rowsImported()).isEqualTo(2);
    assertThat(salts.rowsSkipped()).isZero();

    Dto.ImportResult brands = admin.importCsv(csv("brands.csv",
        "brand,maker,form,pack,price,composition",
        "Zinetac 150,GSK,Tablet,30 tablets,38.5,Ranitidine 150 mg",
        "Okacet 10,Cipla,Tablet,10 tablets,28.0,Cetirizine 10 mg"), "medicines");
    assertThat(brands.rowsImported()).isEqualTo(2);

    // The knowledge base is reloaded by the import, so a scan straight
    // afterwards finds the new brand without a restart.
    assertThat(kb.search("ZINETAC 15O TAB", 1))
        .isNotEmpty()
        .first()
        .satisfies(m -> assertThat(m.medicine().brand).isEqualTo("Zinetac 150"));

    assertThat(admin.overview().counts().salts()).isEqualTo(saltsBefore + 2);
  }

  @Test
  void parsesAMultiSaltComposition() {
    admin.importCsv(csv("combo.csv",
        "brand,maker,form,pack,price,composition",
        "Flexon,Aristo,Tablet,20 tablets,42.0,Ibuprofen 400 mg + Paracetamol 325 mg"),
        "medicines");

    assertThat(kb.allMedicines())
        .filteredOn(m -> m.brand.equals("Flexon"))
        .singleElement()
        .satisfies(m -> assertThat(m.composition)
            .extracting(ms -> ms.salt.name + " " + ms.strength)
            .containsExactlyInAnyOrder("Ibuprofen 400 mg", "Paracetamol 325 mg"));
  }

  @Test
  void skipsAndCountsARowWithNoComposition() {
    // Counted and reported rather than imported as a brand with no salts, which
    // would be invisible to every check in the application.
    Dto.ImportResult result = admin.importCsv(csv("partial.csv",
        "brand,maker,form,pack,price,composition",
        "Good Brand,Maker,Tablet,10 tablets,10.0,Paracetamol 500 mg",
        "Broken Row,Maker,Tablet,10 tablets,10.0,"), "medicines");

    assertThat(result.rowsRead()).isEqualTo(2);
    assertThat(result.rowsImported()).isEqualTo(1);
    assertThat(result.rowsSkipped()).isEqualTo(1);
    assertThat(result.warnings()).isNotEmpty();
  }

  @Test
  void refusesToInventASaltForAnUnrecognisedName() {
    // A typo would otherwise become a new salt that nothing interacts with and
    // that no duplicate check would ever match.
    Dto.ImportResult result = admin.importCsv(csv("typo.csv",
        "brand,maker,form,pack,price,composition",
        "Typo Brand,Maker,Tablet,10 tablets,10.0,Paracetmol 500 mg"), "medicines");

    assertThat(result.rowsImported()).isZero();
    assertThat(result.rowsSkipped()).isEqualTo(1);
    assertThat(result.warnings().get(0)).contains("unknown salt");
  }

  @Test
  void resolvesASaltByItsSynonym() {
    // "Acetaminophen" is the same salt under another name, and a dataset that
    // uses it must not produce a second paracetamol.
    Dto.ImportResult result = admin.importCsv(csv("synonym.csv",
        "brand,maker,form,pack,price,composition",
        "Synonym Brand,Maker,Tablet,10 tablets,10.0,Acetaminophen 500 mg"), "medicines");

    assertThat(result.rowsImported()).isEqualTo(1);
    assertThat(kb.allMedicines())
        .filteredOn(m -> m.brand.equals("Synonym Brand"))
        .singleElement()
        .satisfies(m -> assertThat(m.composition.get(0).salt.name).isEqualTo("Paracetamol"));
  }

  @Test
  void importsAnInteractionInOneCanonicalDirection() {
    admin.importCsv(csv("ddi.csv",
        "salt_a,salt_b,severity,mechanism,mechanism_hi",
        "Warfarin,Clopidogrel,major,\"Both thin the blood, so bleeding risk rises.\","
            + "दोनों खून पतला करते हैं।"), "interactions");

    long warfarin = saltId("Warfarin");
    long clopidogrel = saltId("Clopidogrel");
    assertThat(kb.interaction(warfarin, clopidogrel)).isNotNull();
    assertThat(kb.interaction(clopidogrel, warfarin)).isNotNull();
    assertThat(kb.interaction(warfarin, clopidogrel).mechanism)
        .as("the quoted comma stayed inside its column")
        .isEqualTo("Both thin the blood, so bleeding risk rises.");
  }

  @Test
  void refusesAnInteractionWithASeverityItDoesNotUnderstand() {
    // The severity decides the colour a family sees, so an unrecognised one
    // cannot be allowed to default to anything.
    Dto.ImportResult result = admin.importCsv(csv("bad-severity.csv",
        "salt_a,salt_b,severity,mechanism",
        "Warfarin,Metformin,catastrophic,Something."), "interactions");

    assertThat(result.rowsImported()).isZero();
    assertThat(result.warnings().get(0)).contains("major, moderate or minor");
  }

  @Test
  void refusesAnInteractionOfASaltWithItself() {
    Dto.ImportResult result = admin.importCsv(csv("self.csv",
        "salt_a,salt_b,severity,mechanism",
        "Warfarin,Warfarin,major,Something."), "interactions");
    assertThat(result.rowsImported()).isZero();
  }

  @Test
  void importsGenericsWithTheirMatchKeys() {
    Dto.ImportResult result = admin.importCsv(csv("generics.csv",
        "name,composition,form,pack,mrp",
        "Paracetamol Tablets IP 1000 mg,Paracetamol 1000 mg,Tablet,10 tablets,12.0"),
        "generics");
    assertThat(result.rowsImported()).isEqualTo(1);
  }

  @Test
  void importsSynonymsAndDoesNotDuplicateOneThatIsAlreadyThere() {
    Dto.ImportResult first = admin.importCsv(csv("syn.csv",
        "salt,synonym",
        "Paracetamol,Para"), "synonyms");
    assertThat(first.rowsImported()).isEqualTo(1);

    Dto.ImportResult again = admin.importCsv(csv("syn.csv",
        "salt,synonym",
        "Paracetamol,Para"), "synonyms");
    assertThat(again.rowsImported()).isZero();
  }

  @Test
  void readsHeadersInAnyOrderAndIgnoresTheirCase() {
    // A public dataset will not have the columns in the order this application
    // happens to document.
    Dto.ImportResult result = admin.importCsv(csv("reordered.csv",
        "Composition,BRAND,pack,Maker,price,form",
        "Paracetamol 650 mg,Reordered Brand,10 tablets,Maker,20.0,Tablet"), "medicines");

    assertThat(result.rowsImported()).isEqualTo(1);
    assertThat(kb.allMedicines())
        .filteredOn(m -> m.brand.equals("Reordered Brand"))
        .singleElement()
        .satisfies(m -> assertThat(m.pack).isEqualTo("10 tablets"));
  }

  @Test
  void reimportingABrandUpdatesItRatherThanAddingASecondCopy() {
    admin.importCsv(csv("v1.csv",
        "brand,maker,form,pack,price,composition",
        "Reimported Brand,Maker,Tablet,10 tablets,20.0,Paracetamol 500 mg"), "medicines");
    long after = kb.allMedicines().stream()
        .filter(m -> m.brand.equals("Reimported Brand")).count();

    admin.importCsv(csv("v2.csv",
        "brand,maker,form,pack,price,composition",
        "Reimported Brand,Maker,Tablet,15 tablets,25.0,Paracetamol 500 mg"), "medicines");

    assertThat(after).isEqualTo(1);
    assertThat(kb.allMedicines()).filteredOn(m -> m.brand.equals("Reimported Brand"))
        .singleElement()
        .satisfies(m -> assertThat(m.pack).isEqualTo("15 tablets"));
  }

  private long saltId(String name) {
    return kb.allSalts().stream()
        .filter(s -> s.name.equalsIgnoreCase(name))
        .findFirst().orElseThrow().id;
  }
}
