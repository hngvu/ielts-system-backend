package io.gsp26se16.moni.vocab.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;

public interface VocabRepository extends JpaRepository<Vocab, Integer> {

    Page<Vocab> findByUserIdAndStatusNot(String userId, VocabStatus status, Pageable pageable);

    Page<Vocab> findByUserIdAndVocabListId(String userId, Integer listId, Pageable pageable);

    List<Vocab> findByUserIdAndNextReviewAtBeforeAndStatusNot(String userId, LocalDateTime now, VocabStatus status);

    Optional<Vocab> findByUserIdAndDictionaryId(String userId, Integer dictionaryId);

    long countByUserIdAndStatus(String userId, VocabStatus status);

    boolean existsByUserIdAndWord(String userId, String word);
}
