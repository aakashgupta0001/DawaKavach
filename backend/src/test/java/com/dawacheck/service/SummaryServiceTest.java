package com.dawacheck.service;

import com.dawacheck.IntegrationTest;
import com.dawacheck.dto.Dto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 9, the doctor summary.
 *
 * The page a family hands across a desk. The conversation it is meant to start
 * is the only place any of this gets acted on.
 */
@WithMockUser(username = "aakash@example.com", roles = {"ADMIN"})
class SummaryServiceTest extends IntegrationTest {

  @Autowired
  private SummaryService summaries;

  @Test
  void carriesTheCurrentMedicinesTheAlertsAndADisclaimer() {
    Dto.SummaryView s = summaries.summary("fm1", "en");

    assertThat(s.member().name()).isEqualTo("Ram Prasad Gupta");
    assertThat(s.entries()).hasSize(7);
    assertThat(s.alerts()).hasSize(8);
    assertThat(s.generatedAt()).isNotBlank();
    assertThat(s.disclaimer())
        .contains("information only")
        .contains("the doctor");
  }

  @Test
  void showsOnlyTheMedicinesBeingTakenNow() {
    // A stopped medicine is not what the doctor is being asked about.
    assertThat(summaries.summary("fm1", "en").entries())
        .allSatisfy(e -> assertThat(e.active()).isTrue());
  }

  @Test
  void theDisclaimerIsInTheLanguageAsked() {
    assertThat(summaries.summary("fm1", "hi").disclaimer())
        .isEqualTo("यह रिपोर्ट जानकारी के लिए है। कोई भी दवा शुरू, बंद या बदलने का फ़ैसला "
            + "डॉक्टर ही करेंगे।");
  }

  @Test
  void buildsAPdfInEnglish() {
    byte[] pdf = summaries.pdf("fm1", "en");
    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @Test
  void buildsAPdfInHindiWithADevanagariFontOrSaysWhyNot() {
    // The PDF base fonts cannot set Devanagari, so a Hindi summary needs a
    // TrueType font embedded. Writing Hindi with a font that has no Devanagari
    // glyphs does not fail, it produces a page of blanks, so the service falls
    // back to English and says so on the page rather than handing over
    // something unreadable.
    byte[] pdf = summaries.pdf("fm1", "hi");
    assertThat(pdf).isNotEmpty();

    String raw = new String(pdf, StandardCharsets.ISO_8859_1);
    boolean embeddedADevanagariFont = raw.contains("Nirmala")
        || raw.contains("Mangal")
        || raw.contains("NotoSansDevanagari")
        || raw.contains("Lohit")
        || raw.contains("Devanagari");
    boolean explainedTheFallback = raw.contains("no Devanagari font")
        || raw.contains("This copy is in English");

    assertThat(embeddedADevanagariFont || explainedTheFallback)
        .as("a Hindi PDF either embeds a Devanagari font or explains the English fallback")
        .isTrue();
  }

  @Test
  void aPdfForAMemberWithNoMedicinesStillBuilds() {
    // An empty list is a legitimate state, and a crash here would take out the
    // one feature a family came to the page for.
    byte[] pdf = summaries.pdf("fm4", "en");
    assertThat(pdf).isNotEmpty();
  }

  @Test
  void everyAlertInTheSummaryHasWordingInBothLanguages() {
    // The PDF picks one language and prints it, so a missing translation would
    // come out as a blank block on a page a doctor is reading.
    for (Dto.AlertView a : summaries.summary("fm1", "en").alerts()) {
      assertThat(a.explanation()).containsOnlyKeys("en", "hi");
      assertThat(a.explanation().get("hi").title()).isNotBlank();
      assertThat(a.explanation().get("hi").body()).isNotBlank();
    }
  }
}
