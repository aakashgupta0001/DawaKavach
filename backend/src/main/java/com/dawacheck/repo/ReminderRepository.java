package com.dawacheck.repo;

import com.dawacheck.domain.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
  List<Reminder> findByEntryMemberIdOrderByIdAsc(Long memberId);

  Optional<Reminder> findByIdAndEntryMemberOwnerId(Long id, Long ownerId);

  List<Reminder> findByEnabledTrue();

  void deleteByEntryId(Long entryId);
}
