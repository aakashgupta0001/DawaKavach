package com.dawacheck.repo;

import com.dawacheck.domain.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {
  List<AlertRecord> findByMemberIdOrderByIdAsc(Long memberId);

  void deleteByMemberId(Long memberId);
}
