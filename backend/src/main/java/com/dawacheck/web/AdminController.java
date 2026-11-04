package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.AdminService;
import com.dawacheck.service.SeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Module 2 from the admin side. Behind {@code hasRole("ADMIN")}: these endpoints
 * change the knowledge base every family's safety check is built on, so an
 * ordinary account cannot reach them.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminService admin;
  private final SeedService seed;

  public AdminController(AdminService admin, SeedService seed) {
    this.admin = admin;
    this.seed = seed;
  }

  @GetMapping("/overview")
  public Dto.AdminOverview overview() {
    return admin.overview();
  }

  @PostMapping("/import")
  public Dto.ImportResult importCsv(@RequestParam("file") MultipartFile file,
      @RequestParam("target") String target) {
    return admin.importCsv(file, target);
  }

  @PostMapping("/mappings/{queueId}")
  public ResponseEntity<Void> resolve(@PathVariable String queueId,
      @RequestBody Dto.MappingResolution body) {
    admin.resolveMapping(queueId, body.medicineId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Puts the demo family back to its starting state.
   *
   * The knowledge base is left alone, because reloading a quarter of a million
   * brands is not what someone resetting a demo is asking for.
   */
  @PostMapping("/reset-demo")
  public ResponseEntity<Void> resetDemo() {
    seed.resetDemoData();
    return ResponseEntity.noContent().build();
  }
}
