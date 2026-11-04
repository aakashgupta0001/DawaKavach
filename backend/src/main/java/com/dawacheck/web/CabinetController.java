package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.CabinetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 4, changing one medicine already in the cabinet.
 *
 * Marking a medicine stopped is a PUT with {@code active: false} rather than a
 * delete: the family may be waiting to ask the doctor about it, and a course
 * that ended is still part of why an alert fired last week.
 */
@RestController
@RequestMapping("/api/cabinet")
public class CabinetController {

  private final CabinetService cabinet;

  public CabinetController(CabinetService cabinet) {
    this.cabinet = cabinet;
  }

  @PutMapping("/{entryId}")
  public Dto.CabinetView update(@PathVariable String entryId,
      @RequestBody Dto.CabinetRequest patch) {
    return cabinet.update(entryId, patch);
  }

  @DeleteMapping("/{entryId}")
  public ResponseEntity<Void> delete(@PathVariable String entryId) {
    cabinet.delete(entryId);
    return ResponseEntity.noContent().build();
  }
}
