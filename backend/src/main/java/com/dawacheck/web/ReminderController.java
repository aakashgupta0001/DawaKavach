package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module 9, the reminder endpoints. Listing them lives on the member. */
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

  private final ReminderService reminders;

  public ReminderController(ReminderService reminders) {
    this.reminders = reminders;
  }

  @PostMapping
  public Dto.ReminderView create(@RequestBody Dto.ReminderRequest req) {
    return reminders.save(new Dto.ReminderRequest(null, req.entryId(), req.type(), req.remindAt(),
        req.channel(), req.enabled()));
  }

  @PutMapping("/{id}")
  public Dto.ReminderView update(@PathVariable String id, @RequestBody Dto.ReminderRequest req) {
    return reminders.save(new Dto.ReminderRequest(id, req.entryId(), req.type(), req.remindAt(),
        req.channel(), req.enabled()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    reminders.delete(id);
    return ResponseEntity.noContent().build();
  }
}
