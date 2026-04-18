package io.gsp26se16.moni.vocab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.DailySlot;
import io.gsp26se16.moni.vocab.entity.VocabQuizHistory;

@Repository
public interface VocabQuizHistoryRepository extends JpaRepository<VocabQuizHistory, Integer> {
    Optional<VocabQuizHistory> findTopBySlotAndUserOrderByCreatedAtDesc(DailySlot slot, Users user);

    void deleteByUser(Users user);
}
