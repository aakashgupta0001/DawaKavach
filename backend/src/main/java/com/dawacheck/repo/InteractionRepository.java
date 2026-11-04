package com.dawacheck.repo;

import com.dawacheck.domain.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {
  Optional<Interaction> findBySaltAIdAndSaltBId(Long saltAId, Long saltBId);
}
