package com.dawacheck.repo;

import com.dawacheck.domain.SaltSynonym;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaltSynonymRepository extends JpaRepository<SaltSynonym, Long> {
  List<SaltSynonym> findBySaltId(Long saltId);
}
