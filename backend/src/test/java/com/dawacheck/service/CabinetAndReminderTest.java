package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Module 4 and the reminder half of module 9. */
@WithMockUser(username = "aakash@example.com", roles = {"ADMIN"})
class CabinetAndReminderTest extends IntegrationTest {

  @Autowired
  private MemberService members;

  @Autowired
  private CabinetService cabinet;

  @Autowired
  private ReminderService reminders;

  @Autowired
  private KnowledgeBase kb;

  private String memberId;

  @BeforeEach
  void addATestMember() {
    memberId = members.add(new Dto.MemberRequest(
        "Cabinet Test Member", 50, "Uncle", "", List.of())).id();
  }

  @AfterEach
  void removeTheTestMember() {
    members.delete(memberId);
  }

  private String medicineId(String brand) {
    return "m" + kb.allMedicines().stream()
        .filter(m -> m.brand.equalsIgnoreCase(brand))
        .findFirst().orElseThrow().id;
  }

  private Dto.CabinetView add(String brand, int doses) {
    return cabinet.add(memberId, new Dto.CabinetRequest(
        medicineId(brand), "Morning", doses, "Dr. Test", null, null, false, null));
  }

  /* ---------------- adding ---------------- */

  @Test
  void addsAMedicineWithItsSchedule() {
    Dto.CabinetView e = add("Dolo 650", 2);
    assertThat(e.medicine().brand()).isEqualTo("Dolo 650");
    assertThat(e.dosesPerDay()).isEqualTo(2);
    assertThat(e.active()).isTrue();
    assertThat(e.startDate()).isNotNull();
    assertThat(cabinet.list(memberId)).hasSize(1);
  }

  @Test
  void fillsInSensibleDefaultsRatherThanRefusingASparseForm() {
    Dto.CabinetView e = cabinet.add(memberId, new Dto.CabinetRequest(
        medicineId("Dolo 650"), null, null, null, null, null, null, null));
    assertThat(e.schedule()).isEqualTo("As advised");
    assertThat(e.dosesPerDay()).isEqualTo(1);
    assertThat(e.prescribedBy()).isEqualTo("Not recorded");
  }

  @Test
  void refusesAnEntryWithNoMedicine() {
    assertThatThrownBy(() -> cabinet.add(memberId, new Dto.CabinetRequest(
        null, "Morning", 1, "Dr. Test", null, null, false, null)))
        .hasMessageContaining("Choose a medicine");
  }

  @Test
  void refusesAnImpossibleNumberOfDoses() {
    // Forty times a day is a typo, and it would quietly multiply every daily
    // total the engine works out.
    assertThatThrownBy(() -> add("Dolo 650", 40))
        .hasMessageContaining("between 1 and 12");
    assertThatThrownBy(() -> add("Dolo 650", 0))
        .hasMessageContaining("between 1 and 12");
  }

  @Test
  void refusesAnUnreadableDate() {
    assertThatThrownBy(() -> cabinet.add(memberId, new Dto.CabinetRequest(
        medicineId("Dolo 650"), "Morning", 1, "Dr. Test", "14-03-2025", null, false, null)))
        .hasMessageContaining("YYYY-MM-DD");
  }

  @Test
  void refusesAMedicineThatIsNotInTheCatalogue() {
    assertThatThrownBy(() -> cabinet.add(memberId, new Dto.CabinetRequest(
        "m99999", "Morning", 1, "Dr. Test", null, null, false, null)))
        .isInstanceOf(NotFoundException.class);
  }

  /* ---------------- changing ---------------- */

  @Test
  void markingAMedicineStoppedKeepsTheRowAndTakesItOutOfTheChecks() {
    // A family may be waiting to ask the doctor about it, and a course that
    // ended is still part of why an alert fired last week. So it is a flag,
    // not a delete.
    add("Dolo 650", 2);
    Dto.CabinetView calpol = add("Calpol 500", 2);
    assertThat(members.get(memberId).alertCounts().total()).isPositive();

    Dto.CabinetView stopped = cabinet.update(calpol.id(), new Dto.CabinetRequest(
        null, null, null, null, null, null, null, false));

    assertThat(stopped.active()).isFalse();
    assertThat(cabinet.list(memberId)).hasSize(2);
    assertThat(members.get(memberId).alertCounts().total()).isZero();
    assertThat(members.get(memberId).medicineCount()).isEqualTo(1);
  }

  @Test
  void aPartialUpdateLeavesTheRestAlone() {
    Dto.CabinetView e = add("Dolo 650", 2);
    Dto.CabinetView updated = cabinet.update(e.id(), new Dto.CabinetRequest(
        null, "Night only", null, null, null, null, null, null));

    assertThat(updated.schedule()).isEqualTo("Night only");
    assertThat(updated.dosesPerDay()).isEqualTo(2);
    assertThat(updated.prescribedBy()).isEqualTo("Dr. Test");
    assertThat(updated.medicine().brand()).isEqualTo("Dolo 650");
  }

  @Test
  void anEndDateCanBeClearedAgain() {
    Dto.CabinetView e = cabinet.add(memberId, new Dto.CabinetRequest(
        medicineId("Dolo 650"), "Morning", 1, "Dr. Test", "2026-01-01", "2026-02-01",
        false, null));
    assertThat(e.endDate()).isEqualTo("2026-02-01");

    Dto.CabinetView cleared = cabinet.update(e.id(), new Dto.CabinetRequest(
        null, null, null, null, null, "", null, null));
    assertThat(cleared.endDate()).isNull();
  }

  @Test
  void deletingAMedicineTakesItsRemindersWithIt() {
    // Otherwise a reminder keeps nudging the family about a medicine that is no
    // longer on the list.
    Dto.CabinetView e = add("Dolo 650", 2);
    reminders.save(new Dto.ReminderRequest(null, e.id(), "dose", "08:00", "email", true));
    assertThat(reminders.list(memberId)).hasSize(1);

    cabinet.delete(e.id());

    assertThat(cabinet.list(memberId)).isEmpty();
    assertThat(reminders.list(memberId)).isEmpty();
  }

  @Test
  void anEntryFromAnotherFamilyCannotBeChanged() {
    assertThatThrownBy(() -> cabinet.update("c99999", new Dto.CabinetRequest(
        null, "Mine now", null, null, null, null, null, null)))
        .isInstanceOf(NotFoundException.class);
  }

  /* ---------------- reminders ---------------- */

  @Test
  void savesADoseReminderAndARefillReminder() {
    Dto.CabinetView e = add("Dolo 650", 2);
    Dto.ReminderView dose = reminders.save(
        new Dto.ReminderRequest(null, e.id(), "dose", "08:00", "email", true));
    Dto.ReminderView refill = reminders.save(
        new Dto.ReminderRequest(null, e.id(), "refill", "2026-11-08", "email", true));

    assertThat(dose.type()).isEqualTo("dose");
    assertThat(dose.remindAt()).isEqualTo("08:00");
    assertThat(refill.type()).isEqualTo("refill");
    assertThat(refill.entry().medicine().brand()).isEqualTo("Dolo 650");
    assertThat(reminders.list(memberId)).hasSize(2);
  }

  @Test
  void refusesATimeInTheWrongShapeForTheKindOfReminder() {
    // A dose reminder carries a time of day and a refill reminder a date.
    // Storing one where the other belongs means the scheduler silently never
    // fires it.
    Dto.CabinetView e = add("Dolo 650", 2);
    assertThatThrownBy(() -> reminders.save(
        new Dto.ReminderRequest(null, e.id(), "dose", "2026-11-08", "email", true)))
        .hasMessageContaining("HH:MM");
    assertThatThrownBy(() -> reminders.save(
        new Dto.ReminderRequest(null, e.id(), "refill", "08:00", "email", true)))
        .hasMessageContaining("YYYY-MM-DD");
  }

  @Test
  void refusesAReminderThatIsNeitherADoseNorARefill() {
    Dto.CabinetView e = add("Dolo 650", 2);
    assertThatThrownBy(() -> reminders.save(
        new Dto.ReminderRequest(null, e.id(), "shopping", "08:00", "email", true)))
        .hasMessageContaining("dose or a refill");
  }

  @Test
  void refusesAReminderWithNoMedicine() {
    assertThatThrownBy(() -> reminders.save(
        new Dto.ReminderRequest(null, null, "dose", "08:00", "email", true)))
        .hasMessageContaining("Choose which medicine");
  }

  @Test
  void aReminderCanBeTurnedOffAndOnAgain() {
    Dto.CabinetView e = add("Dolo 650", 2);
    Dto.ReminderView r = reminders.save(
        new Dto.ReminderRequest(null, e.id(), "dose", "08:00", "email", true));

    assertThat(reminders.save(new Dto.ReminderRequest(
        r.id(), null, null, null, null, false)).enabled()).isFalse();
    assertThat(reminders.save(new Dto.ReminderRequest(
        r.id(), null, null, null, null, true)).enabled()).isTrue();
  }

  @Test
  void aReminderCanBeDeleted() {
    Dto.CabinetView e = add("Dolo 650", 2);
    Dto.ReminderView r = reminders.save(
        new Dto.ReminderRequest(null, e.id(), "dose", "08:00", "email", true));
    reminders.delete(r.id());
    assertThat(reminders.list(memberId)).isEmpty();
  }

  @Test
  void theDueReminderJobRunsWithoutAMailServerConfigured() {
    // No SMTP is set in the test profile, so a due reminder is logged rather
    // than sent. The job must not throw, or one unreachable mail server would
    // stop every later reminder in the schedule.
    Dto.CabinetView e = add("Dolo 650", 2);
    reminders.save(new Dto.ReminderRequest(null, e.id(), "dose", "00:00", "email", true));
    reminders.sendDueReminders();
  }

  /* ---------------- members ---------------- */

  @Test
  void refusesAnAgeOutsideHumanRange() {
    assertThatThrownBy(() -> members.update(memberId, new Dto.MemberPatch(
        null, 150, null, null, null)))
        .hasMessageContaining("between 0 and 120");
  }

  @Test
  void recordsAnAllergyAndReportsItsName() {
    // The frontend shows the salt name, so the backend sends both the id it
    // stores and the name it resolves to.
    long cefixime = kb.allSalts().stream()
        .filter(s -> s.name.equals("Cefixime")).findFirst().orElseThrow().id;
    Dto.MemberView updated = members.update(memberId, new Dto.MemberPatch(
        null, null, null, null, List.of("s" + cefixime)));

    assertThat(updated.allergies()).containsExactly("s" + cefixime);
    assertThat(updated.allergyNames()).containsExactly("Cefixime");
  }

  @Test
  void refusesAnAllergyToASaltThatDoesNotExist() {
    // Storing it would leave a member allergic to nothing, which is worse than
    // a rejected form because nobody would ever find out.
    assertThatThrownBy(() -> members.update(memberId, new Dto.MemberPatch(
        null, null, null, null, List.of("s99999"))))
        .isInstanceOf(NotFoundException.class);
  }
}
