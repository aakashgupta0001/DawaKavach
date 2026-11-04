package com.dawacheck.repo;

import com.dawacheck.domain.CabinetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CabinetEntryRepository extends JpaRepository<CabinetEntry, Long> {
  List<CabinetEntry> findByMemberIdOrderByIdAsc(Long memberId);

  Optional<CabinetEntry> findByIdAndMemberOwnerId(Long id, Long ownerId);

  long countByMemberIdAndActiveTrue(Long memberId);
}
