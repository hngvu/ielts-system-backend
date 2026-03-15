package io.gsp26se16.moni.vocab.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.gsp26se16.moni.vocab.entity.VocabReview;

public interface VocabReviewRepository extends JpaRepository<VocabReview, Integer> {

    Page<VocabReview> findByUserIdOrderByReviewedAtDesc(String userId, Pageable pageable);
}
