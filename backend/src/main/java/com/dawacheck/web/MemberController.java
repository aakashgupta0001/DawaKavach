package com.dawacheck.web;

import com.dawacheck.domain.FamilyMember;
import com.dawacheck.dto.Dto;
import com.dawacheck.service.CabinetService;
import com.dawacheck.service.MemberService;
import com.dawacheck.service.ReminderService;
import com.dawacheck.service.SafetyCheckService;
import com.dawacheck.service.SummaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything hanging off one family member: the member, the cabinet, the alerts,
 * the reminders and the summary.
 *
 * All of it is scoped to the signed-in account inside {@link MemberService}, so
 * asking for a member id that belongs to another family gives a 404 rather than
 * someone else's medicine list.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService members;
  private final CabinetService cabinet;
  private final SafetyCheckService safety;
  private final ReminderService reminders;
  private final SummaryService summaries;

  public MemberController(MemberService members, CabinetService cabinet, SafetyCheckService safety,
      ReminderService reminders, SummaryService summaries) {
    this.members = members;
    this.cabinet = cabinet;
    this.safety = safety;
    this.reminders = reminders;
    this.summaries = summaries;
  }

  @GetMapping
  public List<Dto.MemberView> list() {
    return members.list();
  }

  @PostMapping
  public Dto.MemberView add(@Valid @RequestBody Dto.MemberRequest req) {
    return members.add(req);
  }

  @GetMapping("/{id}")
  public Dto.MemberView get(@PathVariable String id) {
    return members.get(id);
  }

  @PutMapping("/{id}")
  public Dto.MemberView update(@PathVariable String id, @RequestBody Dto.MemberPatch patch) {
    return members.update(id, patch);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    members.delete(id);
    return ResponseEntity.noContent().build();
  }

  /* ---------------- module 4 ---------------- */

  @GetMapping("/{id}/cabinet")
  public List<Dto.CabinetView> cabinet(@PathVariable String id) {
    return cabinet.list(id);
  }

  @PostMapping("/{id}/cabinet")
  public Dto.CabinetView addMedicine(@PathVariable String id,
      @RequestBody Dto.CabinetRequest req) {
    return cabinet.add(id, req);
  }

  /* ---------------- modules 5, 6 and 7 ---------------- */

  /**
   * The safety check for one member.
   *
   * Recomputed on every call rather than read back from the alerts table,
   * because an alert is only true of the medicine list as it stands: a medicine
   * marked stopped a moment ago must not still raise a warning.
   */
  @GetMapping("/{id}/alerts")
  public List<Dto.AlertView> alerts(@PathVariable String id) {
    FamilyMember member = members.require(id);
    return safety.checkAndRecord(member, cabinet.rows(member));
  }

  /* ---------------- module 9 ---------------- */

  @GetMapping("/{id}/reminders")
  public List<Dto.ReminderView> reminders(@PathVariable String id) {
    return reminders.list(id);
  }

  @GetMapping("/{id}/summary")
  public Dto.SummaryView summary(@PathVariable String id,
      @RequestParam(defaultValue = "en") String lang) {
    return summaries.summary(id, lang);
  }

  @GetMapping("/{id}/summary.pdf")
  public ResponseEntity<byte[]> summaryPdf(@PathVariable String id,
      @RequestParam(defaultValue = "en") String lang) {
    byte[] pdf = summaries.pdf(id, lang);
    String name = "dawacheck-summary-" + id + ".pdf";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        // inline, so the browser opens it in a tab and the family can read it
        // before deciding whether to save or print it.
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
        .body(pdf);
  }
}
