package com.dawacheck.service;

import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.CabinetEntryRepository;
import com.dawacheck.repo.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Module 4, the family medicine cabinet. */
@Service
public class CabinetService {

  private final CabinetEntryRepository entries;
  private final ReminderRepository reminders;
  private final MemberService memberService;
  private final KnowledgeBase kb;
  private final ViewMapper view;
  private final CurrentUser currentUser;

  public CabinetService(CabinetEntryRepository entries, ReminderRepository reminders,
      MemberService memberService, KnowledgeBase kb, ViewMapper view, CurrentUser currentUser) {
    this.entries = entries;
    this.reminders = reminders;
    this.memberService = memberService;
    this.kb = kb;
    this.view = view;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public List<CabinetEntry> rows(FamilyMember member) {
    return entries.findByMemberIdOrderByIdAsc(member.id);
  }

  @Transactional(readOnly = true)
  public List<Dto.CabinetView> list(String memberApiId) {
    FamilyMember member = memberService.require(memberApiId);
    List<Dto.CabinetView> out = new ArrayList<>();
    for (CabinetEntry e : rows(member)) {
      out.add(view.entry(e));
    }
    return out;
  }

  @Transactional
  public Dto.CabinetView add(String memberApiId, Dto.CabinetRequest req) {
    FamilyMember member = memberService.require(memberApiId);
    if (req.medicineId() == null || req.medicineId().isBlank()) {
      throw new BadRequestException("Choose a medicine first.");
    }
    CabinetEntry e = new CabinetEntry();
    e.member = member;
    e.medicine = kb.requireMedicine(Ids.decode(Ids.MEDICINE, req.medicineId()));
    e.schedule = blankTo(req.schedule(), "As advised");
    e.dosesPerDay = doses(req.dosesPerDay());
    e.prescribedBy = blankTo(req.prescribedBy(), "Not recorded");
    e.startDate = date(req.startDate(), LocalDate.now());
    e.endDate = date(req.endDate(), null);
    e.active = req.active() == null || req.active();
    e.longTerm = Boolean.TRUE.equals(req.longTerm());
    entries.save(e);
    return view.entry(e);
  }

  @Transactional
  public Dto.CabinetView update(String entryApiId, Dto.CabinetRequest patch) {
    CabinetEntry e = require(entryApiId);
    if (patch.medicineId() != null && !patch.medicineId().isBlank()) {
      e.medicine = kb.requireMedicine(Ids.decode(Ids.MEDICINE, patch.medicineId()));
    }
    if (patch.schedule() != null) {
      e.schedule = blankTo(patch.schedule(), "As advised");
    }
    if (patch.dosesPerDay() != null) {
      e.dosesPerDay = doses(patch.dosesPerDay());
    }
    if (patch.prescribedBy() != null) {
      e.prescribedBy = blankTo(patch.prescribedBy(), "Not recorded");
    }
    if (patch.startDate() != null) {
      e.startDate = date(patch.startDate(), e.startDate);
    }
    if (patch.endDate() != null) {
      e.endDate = patch.endDate().isBlank() ? null : date(patch.endDate(), e.endDate);
    }
    if (patch.active() != null) {
      e.active = patch.active();
    }
    if (patch.longTerm() != null) {
      e.longTerm = patch.longTerm();
    }
    entries.save(e);
    return view.entry(e);
  }

  /**
   * Removing a medicine removes its reminders too, so nothing keeps nudging the
   * family about a medicine that is no longer on the list.
   */
  @Transactional
  public void delete(String entryApiId) {
    CabinetEntry e = require(entryApiId);
    reminders.deleteByEntryId(e.id);
    entries.delete(e);
  }

  private CabinetEntry require(String entryApiId) {
    long id = Ids.decode(Ids.ENTRY, entryApiId);
    return entries.findByIdAndMemberOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new NotFoundException("Medicine entry not found."));
  }

  private static int doses(Integer value) {
    if (value == null) {
      return 1;
    }
    if (value < 1 || value > 12) {
      throw new BadRequestException("Doses a day should be between 1 and 12.");
    }
    return value;
  }

  private static LocalDate date(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
    } catch (RuntimeException e) {
      throw new BadRequestException("That date is not in the YYYY-MM-DD form.");
    }
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
