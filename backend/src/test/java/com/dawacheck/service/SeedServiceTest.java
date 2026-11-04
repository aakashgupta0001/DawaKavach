package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The seeded demo family, and the reset behind the button on the admin page.
 *
 * Transactional, because resetting deletes and recreates every member: letting
 * that commit would renumber the rows the other tests read by id.
 */
@WithMockUser(username = "aakash@example.com", roles = {"ADMIN"})
@Transactional
class SeedServiceTest extends IntegrationTest {

  @Autowired
  private SeedService seed;

  @Autowired
  private MemberService members;

  @Autowired
  private KnowledgeBase kb;

  @AfterEach
  void rebuildTheKnowledgeBaseFromWhatCommitted() {
    kb.reload();
  }

  @Test
  void theDemoFamilyIsTheOneFromTheDocumentation() {
    List<Dto.MemberView> family = members.list();
    assertThat(family).extracting(Dto.MemberView::name)
        .containsExactly("Ram Prasad Gupta", "Savitri Devi", "Suresh Gupta", "Aakash Gupta");
    assertThat(family).extracting(Dto.MemberView::age).containsExactly(72, 68, 48, 21);

    // Dadaji is the worked example: seven medicines, three of them paracetamol,
    // and an allergy on record.
    Dto.MemberView dadaji = family.get(0);
    assertThat(dadaji.nickname()).isEqualTo("Dadaji");
    assertThat(dadaji.medicineCount()).isEqualTo(7);
    assertThat(dadaji.allergyNames()).containsExactly("Cefixime");
    assertThat(dadaji.alertCounts().red()).isEqualTo(3);
    assertThat(dadaji.alertCounts().yellow()).isEqualTo(5);
  }

  @Test
  void resettingTheDemoDataPutsTheFamilyBackWithoutFailing() {
    // The regression: within one flush Hibernate runs its inserts before its
    // deletes, so the re-seeded account was inserted while the old row was
    // still there and the unique constraint on email rejected it. The admin
    // button returned a 500 and the demo was stuck in whatever state the
    // session had left it in.
    assertThatCode(() -> seed.resetDemoData()).doesNotThrowAnyException();

    List<Dto.MemberView> family = members.list();
    assertThat(family).extracting(Dto.MemberView::name)
        .containsExactly("Ram Prasad Gupta", "Savitri Devi", "Suresh Gupta", "Aakash Gupta");
    assertThat(family.get(0).medicineCount()).isEqualTo(7);
    assertThat(family.get(0).alertCounts().total()).isEqualTo(8);
  }

  @Test
  void resettingTwiceInARowIsStillFine() {
    seed.resetDemoData();
    assertThatCode(() -> seed.resetDemoData()).doesNotThrowAnyException();
    assertThat(members.list()).hasSize(4);
  }

  @Test
  void resettingLeavesTheKnowledgeBaseAlone() {
    // Reloading a quarter of a million brands is not what someone resetting a
    // demo is asking for.
    long salts = kb.allSalts().size();
    long medicines = kb.allMedicines().size();

    seed.resetDemoData();

    assertThat(kb.allSalts()).hasSize((int) salts);
    assertThat(kb.allMedicines()).hasSize((int) medicines);
  }

  @Test
  void seedingAnAlreadySeededDatabaseChangesNothing() {
    // It runs on every start, so it has to be safe to run on a database that
    // already has everything in it.
    long saltsBefore = kb.allSalts().size();
    int familyBefore = members.list().size();

    seed.run();

    assertThat(kb.allSalts()).hasSize((int) saltsBefore);
    assertThat(members.list()).hasSize(familyBefore);
  }
}
