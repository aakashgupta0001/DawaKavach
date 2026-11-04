package com.dawacheck.web;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import com.dawacheck.service.CabinetService;
import com.dawacheck.service.MemberService;
import com.dawacheck.service.ScanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Module 3 end to end: read the bill, let the user confirm each line, then
 * check the list that results.
 *
 * Deliberately not wrapped in a test transaction. The bug these tests were
 * written after was invisible inside a single long-lived persistence context:
 * the member behind a scan came back as a lazy proxy, reading its id gave null,
 * and the safety check that followed a confirmation ran over an empty medicine
 * list and reported nothing wrong. A test that shared one transaction across
 * the whole flow would have kept the real member in the context and gone green.
 * So each test writes for real and clears up after itself.
 */
@WithMockUser(username = "aakash@example.com", roles = {"ADMIN"})
class ScanFlowTest extends IntegrationTest {

  @Autowired
  private ScanService scans;

  @Autowired
  private MemberService members;

  @Autowired
  private CabinetService cabinet;

  private String memberId;

  @BeforeEach
  void addATestMember() {
    // A member of this test's own, so nothing it does disturbs the demo family
    // that the other tests read.
    memberId = members.add(new Dto.MemberRequest(
        "Scan Test Member", 70, "Grandfather", "Nanaji", List.of())).id();
  }

  @AfterEach
  void removeTheTestMember() {
    members.delete(memberId);
  }

  private static List<Dto.ConfirmItem> confirmAll(Dto.ScanView scan) {
    List<Dto.ConfirmItem> items = new ArrayList<>();
    for (Dto.ScanItemView item : scan.items()) {
      items.add(new Dto.ConfirmItem(item.id(), item.suggestions().get(0).id(),
          "Morning and night", 2, "Dr. Test", false));
    }
    return items;
  }

  /* ---------------- reading ---------------- */

  @Test
  void offersTheSampleBillsWhenNoOcrEngineIsInstalled() {
    // Section 14 forbids showing invented text as though it had been read off a
    // photo, so with no engine the scanner says so and offers samples instead.
    List<Dto.OcrFixtureRow> samples = scans.samples();
    assertThat(samples).hasSize(3);
    assertThat(samples).extracting(Dto.OcrFixtureRow::label)
        .anySatisfy(label -> assertThat(label).contains("Pharmacy bill"));
    assertThat(samples).allSatisfy(s -> assertThat(s.lineCount()).isPositive());
  }

  @Test
  void refusesAnUploadWhenItCannotReadThePhoto() {
    // Rather than filing a blank scan, or pretending.
    MockMultipartFile photo = new MockMultipartFile(
        "image", "bill.jpg", "image/jpeg", new byte[] {1, 2, 3});
    assertThatThrownBy(() -> scans.create(memberId, photo, null))
        .hasMessageContaining("not switched on");
  }

  @Test
  void refusesAScanWithNeitherAPhotoNorASample() {
    assertThatThrownBy(() -> scans.create(memberId, null, null))
        .hasMessageContaining("Choose a photo");
  }

  @Test
  void keepsOnlyTheLinesThatCouldBeAMedicine() {
    // The printed sample is seven lines: a store name, a GST and bill number, a
    // total, and four medicines. A family asked to confirm "SHREE MEDICAL &
    // GENERAL STORES" would rightly lose confidence in the whole thing.
    Dto.ScanView scan = scans.create(memberId, null, 0);

    assertThat(scan.rawText().lines()).hasSize(7);
    assertThat(scan.items()).hasSize(4);
    assertThat(scan.items()).extracting(Dto.ScanItemView::cleanedText)
        .containsExactly("DOLO-650", "COMBIFLAM", "SINAREST", "TELMA-40");
    assertThat(scan.status()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(scan.ocrAvailable()).isFalse();
  }

  @Test
  void suggestsTheRightBrandForEachLineWithAConfidence() {
    Dto.ScanView scan = scans.create(memberId, null, 0);

    assertThat(scan.items()).extracting(i -> i.suggestions().get(0).brand())
        .containsExactly("Dolo 650", "Combiflam", "Sinarest", "Telma 40");
    assertThat(scan.items()).allSatisfy(i -> {
      assertThat(i.suggestions()).hasSizeLessThanOrEqualTo(3);
      assertThat(i.suggestions().get(0).confidence()).isGreaterThan(0.8);
      assertThat(i.confirmed()).isFalse();
      assertThat(i.matchedMedicineId()).isNull();
    });
  }

  @Test
  void asksTheUserToCheckALineReadAtLowConfidence() {
    // The handwritten sample's second line is read at 34%. It happens to match
    // a brand well, but a confident match on characters nobody trusts is not
    // evidence, so the line is still flagged.
    Dto.ScanView scan = scans.create(memberId, null, 2);

    assertThat(scan.items()).hasSize(2);
    Dto.ScanItemView poor = scan.items().get(1);
    assertThat(poor.ocrConfidence()).isLessThan(ScanService.OCR_CONFIDENCE_FLOOR);
    assertThat(poor.suggestions().get(0).confidence())
        .as("it matches a brand well despite being barely legible")
        .isGreaterThan(ScanService.MATCH_CONFIDENCE_FLOOR);
    assertThat(poor.lowConfidence()).isTrue();
  }

  @Test
  void readsThroughTheCharactersOcrGetsWrong() {
    Dto.ScanView scan = scans.create(memberId, null, 1);
    assertThat(scan.items()).extracting(i -> i.suggestions().get(0).brand())
        .containsExactly("Ecosprin 75", "Pan 40");
  }

  /* ---------------- confirming ---------------- */

  @Test
  void nothingReachesTheCabinetUntilTheUserConfirms() {
    // The requirement from section 14, and the reason the scanner has no path
    // that writes straight to the medicine list.
    scans.create(memberId, null, 0);
    assertThat(cabinet.list(memberId)).isEmpty();
  }

  @Test
  void confirmingSavesTheMedicinesAndReturnsTheAlertsInTheSameResponse() {
    // The regression this file exists for. The alerts have to be computed over
    // the list including what was just saved; returning an empty list here is
    // what the bug did, and from the outside it looked like a clean bill of
    // health on a list with three paracetamol medicines in it.
    Dto.ScanView scan = scans.create(memberId, null, 0);
    Dto.ConfirmResult result = scans.confirm(scan.id(),
        new Dto.ConfirmRequest(confirmAll(scan), false));

    assertThat(result.added()).extracting(e -> e.medicine().brand())
        .containsExactly("Dolo 650", "Combiflam", "Sinarest", "Telma 40");

    assertThat(result.alerts())
        .as("the safety check must see the medicines just saved")
        .isNotEmpty();
    assertThat(result.alerts()).extracting(Dto.AlertView::type)
        .contains("DUPLICATE_SALT", "SALT_DAILY_TOTAL", "INTERACTION_KNOWN");

    // And the same answer on a fresh request, from a new transaction.
    assertThat(members.get(memberId).alertCounts().total())
        .isEqualTo(result.alerts().size());
  }

  @Test
  void theSavedMedicinesCarryTheScheduleTheUserChose() {
    Dto.ScanView scan = scans.create(memberId, null, 0);
    Dto.ConfirmResult result = scans.confirm(scan.id(),
        new Dto.ConfirmRequest(confirmAll(scan), false));

    assertThat(result.added()).allSatisfy(e -> {
      assertThat(e.schedule()).isEqualTo("Morning and night");
      assertThat(e.dosesPerDay()).isEqualTo(2);
      assertThat(e.prescribedBy()).isEqualTo("Dr. Test");
      assertThat(e.active()).isTrue();
      assertThat(e.memberId()).isEqualTo(memberId);
      assertThat(e.fromScanId()).isEqualTo(scan.id());
    });
  }

  @Test
  void aSkippedLineIsNotSaved() {
    Dto.ScanView scan = scans.create(memberId, null, 0);
    List<Dto.ConfirmItem> items = new ArrayList<>();
    for (int i = 0; i < scan.items().size(); i++) {
      Dto.ScanItemView item = scan.items().get(i);
      items.add(new Dto.ConfirmItem(item.id(), item.suggestions().get(0).id(),
          "As advised", 1, "Dr. Test", i % 2 == 1));
    }

    Dto.ConfirmResult result = scans.confirm(scan.id(),
        new Dto.ConfirmRequest(items, false));
    assertThat(result.added()).extracting(e -> e.medicine().brand())
        .containsExactly("Dolo 650", "Sinarest");
  }

  @Test
  void aLineWithNoMedicineChosenIsNotSavedHoweverGoodTheGuessWas() {
    // An unanswered line is not a confirmation. The matcher being 97% sure is
    // not the user agreeing.
    Dto.ScanView scan = scans.create(memberId, null, 0);
    List<Dto.ConfirmItem> items = scan.items().stream()
        .map(i -> new Dto.ConfirmItem(i.id(), null, "As advised", 1, "Dr. Test", false))
        .toList();

    assertThat(scans.confirm(scan.id(), new Dto.ConfirmRequest(items, false)).added()).isEmpty();
    assertThat(cabinet.list(memberId)).isEmpty();
  }

  @Test
  void anEmptyConfirmationSavesNothing() {
    Dto.ScanView scan = scans.create(memberId, null, 0);
    assertThat(scans.confirm(scan.id(), new Dto.ConfirmRequest(List.of(), false)).added())
        .isEmpty();
  }

  @Test
  void aNullItemListIsTreatedAsSavingNothing() {
    Dto.ScanView scan = scans.create(memberId, null, 0);
    assertThat(scans.confirm(scan.id(), new Dto.ConfirmRequest(null, false)).added()).isEmpty();
  }

  @Test
  void aLineFromAnotherScanIsRefused() {
    Dto.ScanView first = scans.create(memberId, null, 0);
    Dto.ScanView second = scans.create(memberId, null, 1);

    List<Dto.ConfirmItem> wrong = List.of(new Dto.ConfirmItem(
        second.items().get(0).id(),
        second.items().get(0).suggestions().get(0).id(),
        "As advised", 1, "Dr. Test", false));

    assertThatThrownBy(() -> scans.confirm(first.id(), new Dto.ConfirmRequest(wrong, false)))
        .hasMessageContaining("does not belong to this scan");
  }

  @Test
  void aScanCannotBeConfirmedTwice() {
    // Otherwise a double-tapped Save button files every medicine twice, which
    // then shows up as a duplicate-salt warning the family cannot explain.
    Dto.ScanView scan = scans.create(memberId, null, 0);
    scans.confirm(scan.id(), new Dto.ConfirmRequest(confirmAll(scan), false));

    assertThatThrownBy(() -> scans.confirm(scan.id(),
        new Dto.ConfirmRequest(confirmAll(scan), false)))
        .hasMessageContaining("already been saved");
    assertThat(cabinet.list(memberId)).hasSize(4);
  }

  @Test
  void theScanRecordsWhichLinesWereConfirmed() {
    Dto.ScanView scan = scans.create(memberId, null, 0);
    scans.confirm(scan.id(), new Dto.ConfirmRequest(confirmAll(scan), false));

    Dto.ScanView after = scans.get(scan.id());
    assertThat(after.status()).isEqualTo("CONFIRMED");
    assertThat(after.items()).allSatisfy(i -> {
      assertThat(i.confirmed()).isTrue();
      assertThat(i.matchedMedicineId()).isNotNull();
    });
  }

  /* ---------------- the photo ---------------- */

  @Test
  void theScanSaysWhetherThePhotoWasKept() {
    // Section 14: the photo goes once it has been confirmed, unless the user
    // asked to keep it. There is no photo in these tests because a sample bill
    // was used, but the flag the frontend reports to the family is still set.
    Dto.ScanView scan = scans.create(memberId, null, 0);
    scans.confirm(scan.id(), new Dto.ConfirmRequest(confirmAll(scan), false));
    assertThat(scans.get(scan.id()).imageRetained()).isFalse();

    Dto.ScanView kept = scans.create(memberId, null, 1);
    scans.confirm(kept.id(), new Dto.ConfirmRequest(confirmAll(kept), true));
    assertThat(scans.get(kept.id()).imageRetained()).isTrue();
  }

  @Test
  void theTextThatWasReadSurvivesConfirmation() {
    // The photo goes, but what was read off it is the record of where the
    // confirmed medicines came from.
    Dto.ScanView scan = scans.create(memberId, null, 0);
    scans.confirm(scan.id(), new Dto.ConfirmRequest(confirmAll(scan), false));
    assertThat(scans.get(scan.id()).rawText()).contains("DOLO-650");
  }

  /* ---------------- scope ---------------- */

  @Test
  void aScanCannotBeReadWithTheWrongKindOfId() {
    assertThatThrownBy(() -> scans.get("m1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anUnknownScanIsReportedAsGone() {
    assertThatThrownBy(() -> scans.get("scan99999"))
        .hasMessageContaining("not found or already cleared");
  }

  /* ---------------- deleting a member ---------------- */

  @Test
  void deletingAMemberRemovesTheirScansToo() {
    // A scan is a photograph of a prescription, so a member who is deleted must
    // not leave one behind. This also used to fail outright on the foreign key.
    String doomed = members.add(new Dto.MemberRequest(
        "Temporary Member", 40, "Cousin", "", List.of())).id();
    Dto.ScanView scan = scans.create(doomed, null, 0);
    scans.confirm(scan.id(), new Dto.ConfirmRequest(confirmAll(scan), true));

    members.delete(doomed);

    assertThatThrownBy(() -> scans.get(scan.id()))
        .hasMessageContaining("not found");
    assertThat(members.list()).extracting(Dto.MemberView::name)
        .doesNotContain("Temporary Member");
  }
}
