package com.dawacheck.repo;

import com.dawacheck.domain.Scan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {
  Optional<Scan> findByIdAndMemberOwnerId(Long id, Long ownerId);

  /**
   * Loaded rather than bulk-deleted on purpose: a scan owns its lines, and
   * {@code deleteAll} on the entities lets that cascade, where a bulk delete
   * would leave the scan_items rows behind and fail on the foreign key.
   */
  List<Scan> findByMemberId(Long memberId);

  /** Used by the cleanup job: unconfirmed uploads do not sit around for ever. */
  List<Scan> findByStatusAndCreatedAtBefore(String status, Instant before);
}
