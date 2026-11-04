package com.dawacheck.repo;

import com.dawacheck.domain.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
  List<FamilyMember> findByOwnerIdOrderByIdAsc(Long ownerId);

  /** Scoped by owner on purpose: an account must never load another family. */
  Optional<FamilyMember> findByIdAndOwnerId(Long id, Long ownerId);
}
