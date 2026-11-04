package com.dawacheck.service;

import com.dawacheck.domain.AppUser;
import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.AlertRecordRepository;
import com.dawacheck.repo.CabinetEntryRepository;
import com.dawacheck.repo.FamilyMemberRepository;
import com.dawacheck.repo.ReminderRepository;
import com.dawacheck.repo.ScanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Module 1, the family side: who is in the family and what each of them is taking. */
@Service
public class MemberService {

  private final FamilyMemberRepository members;
  private final CabinetEntryRepository entries;
  private final AlertRecordRepository alerts;
  private final ReminderRepository reminders;
  private final ScanRepository scans;
  private final SafetyCheckService safety;
  private final ViewMapper view;
  private final CurrentUser currentUser;
  private final KnowledgeBase kb;

  public MemberService(FamilyMemberRepository members, CabinetEntryRepository entries,
      AlertRecordRepository alerts, ReminderRepository reminders, ScanRepository scans,
      SafetyCheckService safety, ViewMapper view, CurrentUser currentUser, KnowledgeBase kb) {
    this.members = members;
    this.entries = entries;
    this.alerts = alerts;
    this.reminders = reminders;
    this.scans = scans;
    this.safety = safety;
    this.view = view;
    this.currentUser = currentUser;
    this.kb = kb;
  }

  /** The member row, checked against the signed-in account. */
  @Transactional(readOnly = true)
  public FamilyMember require(String apiId) {
    long id = Ids.decode(Ids.MEMBER, apiId);
    return members.findByIdAndOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new NotFoundException("Family member not found."));
  }

  @Transactional(readOnly = true)
  public List<Dto.MemberView> list() {
    List<Dto.MemberView> out = new ArrayList<>();
    for (FamilyMember m : members.findByOwnerIdOrderByIdAsc(currentUser.requireId())) {
      out.add(withCounts(m));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public Dto.MemberView get(String apiId) {
    return withCounts(require(apiId));
  }

  /**
   * The alert counts shown on the dashboard badges are computed from the cabinet
   * rather than read from the alerts table, so a medicine added a second ago is
   * already reflected. For four members this is four quick checks over data
   * already in memory.
   */
  @Transactional(readOnly = true)
  public Dto.MemberView withCounts(FamilyMember m) {
    List<CabinetEntry> all = entries.findByMemberIdOrderByIdAsc(m.id);
    Dto.AlertCounts counts = safety.counts(safety.check(m, all));
    long active = all.stream().filter(e -> e.active).count();
    return view.member(m, active, counts);
  }

  @Transactional
  public Dto.MemberView add(Dto.MemberRequest req) {
    AppUser owner = currentUser.require();
    FamilyMember m = new FamilyMember();
    m.owner = owner;
    m.name = req.name().trim();
    m.age = req.age();
    m.relation = blankTo(req.relation(), "Family member");
    m.nickname = req.nickname() == null ? "" : req.nickname().trim();
    m.allergySaltIds = validatedAllergies(req.allergies());
    members.save(m);
    return withCounts(m);
  }

  @Transactional
  public Dto.MemberView update(String apiId, Dto.MemberPatch patch) {
    FamilyMember m = require(apiId);
    if (patch.name() != null && !patch.name().isBlank()) {
      m.name = patch.name().trim();
    }
    if (patch.age() != null) {
      if (patch.age() < 0 || patch.age() > 120) {
        throw new BadRequestException("Enter an age between 0 and 120.");
      }
      m.age = patch.age();
    }
    if (patch.relation() != null) {
      m.relation = blankTo(patch.relation(), "Family member");
    }
    if (patch.nickname() != null) {
      m.nickname = patch.nickname().trim();
    }
    if (patch.allergies() != null) {
      m.allergySaltIds = validatedAllergies(patch.allergies());
    }
    members.save(m);
    return withCounts(m);
  }

  /**
   * Deleting a member takes everything of theirs with them: their medicines,
   * their reminders, their alerts, and any scan they ever uploaded.
   *
   * The DPDP Act 2023 principle the documentation sets out is that a family can
   * remove their data, and leaving a cabinet row or a prescription photo
   * pointing at a deleted member would be keeping it in all but name. The scans
   * matter twice over, because an uploaded bill is a photograph of a
   * prescription.
   */
  @Transactional
  public void delete(String apiId) {
    FamilyMember m = require(apiId);
    alerts.deleteByMemberId(m.id);
    for (CabinetEntry entry : entries.findByMemberIdOrderByIdAsc(m.id)) {
      reminders.deleteByEntryId(entry.id);
    }
    entries.deleteAll(entries.findByMemberIdOrderByIdAsc(m.id));
    scans.deleteAll(scans.findByMemberId(m.id));
    members.delete(m);
  }

  /** Rejects a salt id that is not in the knowledge base rather than storing it. */
  private String validatedAllergies(List<String> apiSaltIds) {
    if (apiSaltIds == null || apiSaltIds.isEmpty()) {
      return "";
    }
    List<String> raw = new ArrayList<>();
    for (String apiId : apiSaltIds) {
      long saltId = Ids.decode(Ids.SALT, apiId);
      kb.requireSalt(saltId);
      raw.add(String.valueOf(saltId));
    }
    return String.join(",", raw);
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
