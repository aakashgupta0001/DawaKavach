package com.dawacheck.web;

import com.dawacheck.dto.Dto;
import com.dawacheck.service.ScanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Module 3, the scanner endpoints.
 *
 * {@code POST /api/scans} takes the photo and answers with the lines it read and
 * what each might be. {@code POST /api/scans/{id}/confirm} takes the user's
 * answers and is the only path by which a scanned medicine enters the cabinet.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

  private final ScanService scans;

  public ScanController(ScanService scans) {
    this.scans = scans;
  }

  /**
   * @param image the photo; may be left out when {@code sampleIndex} is given
   * @param sampleIndex one of the sample bills, offered when this installation
   *     has no OCR engine configured
   */
  @PostMapping
  public Dto.ScanView create(
      @RequestParam("memberId") String memberId,
      @RequestParam(value = "image", required = false) MultipartFile image,
      @RequestParam(value = "fixtureIndex", required = false) Integer sampleIndex) {
    return scans.create(memberId, image, sampleIndex);
  }

  /** The sample bills, or an empty list when the photo can really be read. */
  @GetMapping("/samples")
  public List<Dto.OcrFixtureRow> samples() {
    return scans.samples();
  }

  @GetMapping("/{id}")
  public Dto.ScanView get(@PathVariable String id) {
    return scans.get(id);
  }

  @PostMapping("/{id}/confirm")
  public Dto.ConfirmResult confirm(@PathVariable String id,
      @RequestBody Dto.ConfirmRequest req) {
    return scans.confirm(id, req);
  }
}
