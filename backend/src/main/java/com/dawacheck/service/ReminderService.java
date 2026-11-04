package com.dawacheck.service;

import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.Reminder;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import com.dawacheck.repo.CabinetEntryRepository;
import com.dawacheck.repo.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Module 9, the reminders half.
 *
 * A dose reminder carries a time of day and fires every day. A refill reminder
 * carries a date and fires once. The job below runs every five minutes and sends
 * what is due, keeping {@code lastSentAt} so a restart does not send the same
 * reminder twice.
 *
 * Times are read in Asia/Kolkata. A reminder set for 08:00 means eight in the
 * morning where the family lives, not wherever the server happens to be.
 */
@Service
public class ReminderService {

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  /** How late a due reminder may still be sent, in minutes. */
  private static final long GRACE_MINUTES = 10;

  private final ReminderRepository reminders;
  private final CabinetEntryRepository entries;
  private final MemberService memberService;
  private final ViewMapper view;
  private final CurrentUser currentUser;
  private final Optional<JavaMailSender> mail;

  @Value("${spring.mail.host:}")
  private String mailHost;

  @Value("${spring.mail.username:dawacheck@localhost}")
  private String mailFrom;

  public ReminderService(ReminderRepository reminders, CabinetEntryRepository entries,
      MemberService memberService, ViewMapper view, CurrentUser currentUser,
      Optional<JavaMailSender> mail) {
    this.reminders = reminders;
    this.entries = entries;
    this.memberService = memberService;
    this.view = view;
    this.currentUser = currentUser;
    this.mail = mail;
  }

  @Transactional(readOnly = true)
  public List<Dto.ReminderView> list(String memberApiId) {
    FamilyMember member = memberService.require(memberApiId);
    List<Dto.ReminderView> out = new ArrayList<>();
    for (Reminder r : reminders.findByEntryMemberIdOrderByIdAsc(member.id)) {
      out.add(view.reminder(r));
    }
    return out;
  }

  @Transactional
  public Dto.ReminderView save(Dto.ReminderRequest req) {
    Reminder r = req.id() == null || req.id().isBlank() ? new Reminder() : require(req.id());

    if (r.id == null) {
      if (req.entryId() == null || req.entryId().isBlank()) {
        throw new BadRequestException("Choose which medicine the reminder is for.");
      }
      r.entry = requireEntry(req.entryId());
    } else if (req.entryId() != null && !req.entryId().isBlank()) {
      r.entry = requireEntry(req.entryId());
    }

    if (req.type() != null && !req.type().isBlank()) {
      if (!"dose".equals(req.type()) && !"refill".equals(req.type())) {
        throw new BadRequestException("A reminder is either a dose or a refill reminder.");
      }
      r.type = req.type();
    }
    if (req.remindAt() != null && !req.remindAt().isBlank()) {
      r.remindAt = validateWhen(r.type, req.remindAt().trim());
    }
    if (r.remindAt == null || r.remindAt.isBlank()) {
      throw new BadRequestException("Set a time for the reminder.");
    }
    if (req.channel() != null && !req.channel().isBlank()) {
      r.channel = req.channel();
    }
    if (req.enabled() != null) {
      r.enabled = req.enabled();
    }
    reminders.save(r);
    return view.reminder(r);
  }

  @Transactional
  public void delete(String reminderApiId) {
    reminders.delete(require(reminderApiId));
  }

  /** "08:00" for a dose, "2026-11-08" for a refill. */
  private static String validateWhen(String type, String value) {
    try {
      if ("refill".equals(type)) {
        LocalDate.parse(value);
      } else {
        LocalTime.parse(value);
      }
      return value;
    } catch (DateTimeParseException e) {
      throw new BadRequestException("refill".equals(type)
          ? "Give the refill date as YYYY-MM-DD."
          : "Give the time as HH:MM, for example 08:00.");
    }
  }

  private Reminder require(String reminderApiId) {
    long id = Ids.decode(Ids.REMINDER, reminderApiId);
    return reminders.findByIdAndEntryMemberOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new NotFoundException("Reminder not found."));
  }

  private CabinetEntry requireEntry(String entryApiId) {
    long id = Ids.decode(Ids.ENTRY, entryApiId);
    return entries.findByIdAndMemberOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new NotFoundException("Medicine entry not found."));
  }

  /* ================= the scheduled send ================= */

  @Scheduled(fixedDelay = 5, initialDelay = 2, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
  @Transactional
  public void sendDueReminders() {
    ZonedDateTime now = ZonedDateTime.now(ZONE);
    int sent = 0;
    for (Reminder r : reminders.findByEnabledTrue()) {
      if (!r.entry.active) {
        continue; // a medicine marked stopped does not get reminders
      }
      if (!isDue(r, now)) {
        continue;
      }
      send(r, now);
      r.lastSentAt = now.toInstant();
      reminders.save(r);
      sent++;
    }
    if (sent > 0) {
      log.info("Sent {} reminder(s)", sent);
    }
  }

  private boolean isDue(Reminder r, ZonedDateTime now) {
    try {
      if ("refill".equals(r.type)) {
        LocalDate on = LocalDate.parse(r.remindAt);
        if (now.toLocalDate().isBefore(on)) {
          return false;
        }
        return r.lastSentAt == null; // a refill reminder fires once
      }
      LocalTime at = LocalTime.parse(r.remindAt);
      long minutesLate = java.time.Duration.between(at, now.toLocalTime()).toMinutes();
      if (minutesLate < 0 || minutesLate > GRACE_MINUTES) {
        return false;
      }
      // Already sent today?
      return r.lastSentAt == null
          || !r.lastSentAt.atZone(ZONE).toLocalDate().isEqual(now.toLocalDate());
    } catch (DateTimeParseException e) {
      log.warn("Reminder {} has an unreadable time: {}", r.id, r.remindAt);
      return false;
    }
  }

  /**
   * A reminder says when to take a medicine that is already prescribed. It never
   * says to start, stop or change anything, which is why the body is this short.
   */
  private void send(Reminder r, ZonedDateTime now) {
    String who = Objects.requireNonNullElse(r.entry.member.nickname, "").isBlank()
        ? r.entry.member.name : r.entry.member.nickname;
    String subject = "refill".equals(r.type)
        ? "DawaCheck: %s may be running low on %s".formatted(who, r.entry.medicine.brand)
        : "DawaCheck: %s, %s".formatted(who, r.entry.medicine.brand);
    String body = "refill".equals(r.type)
        ? ("%s was started on %s. It may be time to get more.%n%nDawaCheck does not change any "
            + "medicine. Confirm with your doctor or pharmacist.")
            .formatted(r.entry.medicine.brand,
                r.entry.startDate == null ? "an earlier date" : r.entry.startDate)
        : ("%s for %s: %s.%n%nDawaCheck does not change any medicine. Confirm with your doctor "
            + "or pharmacist.").formatted(r.entry.medicine.brand, who, r.entry.schedule);

    String to = r.entry.member.owner.email;
    if (mail.isEmpty() || mailHost == null || mailHost.isBlank()) {
      // No SMTP configured. Logging it is honest; pretending it was emailed is not.
      log.info("Reminder due at {} for {} (no mail server configured, not sent): {}",
          r.remindAt, to, subject);
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailFrom);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      mail.get().send(message);
    } catch (RuntimeException e) {
      log.warn("Could not send the reminder for entry {}: {}", r.entry.id, e.getMessage());
    }
  }
}
