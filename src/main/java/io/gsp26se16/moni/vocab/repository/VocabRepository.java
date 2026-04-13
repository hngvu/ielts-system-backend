package io.gsp26se16.moni.vocab.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.enumeration.VocabSourceType;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;

public interface VocabRepository extends JpaRepository<Vocab, Integer> {

    Page<Vocab> findByUserIdAndStatusNot(String userId, VocabStatus status, Pageable pageable);

    Page<Vocab> findByUserIdAndVocabListId(String userId, Integer listId, Pageable pageable);

    Page<Vocab> findByUserIdAndVocabListIdAndStatusNot(
            String userId, Integer listId, VocabStatus status, Pageable pageable);

    @Query(
            "SELECT v FROM Vocab v WHERE v.user.id = :userId AND v.status != :status AND LOWER(v.word) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Vocab> findByUserIdAndStatusNotAndWordContaining(
            @Param("userId") String userId,
            @Param("status") VocabStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query(
            "SELECT v FROM Vocab v WHERE v.user.id = :userId AND v.vocabList.id = :listId AND v.status != :status AND LOWER(v.word) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Vocab> findByUserIdAndVocabListIdAndStatusNotAndWordContaining(
            @Param("userId") String userId,
            @Param("listId") Integer listId,
            @Param("status") VocabStatus status,
            @Param("search") String search,
            Pageable pageable);

    List<Vocab> findByUserIdAndNextReviewAtBeforeAndStatusNot(String userId, LocalDateTime now, VocabStatus status);

    Optional<Vocab> findByUserIdAndDictionaryId(String userId, Integer dictionaryId);

    Optional<Vocab> findByUserIdAndWord(String userId, String word);

    long countByUserIdAndStatus(String userId, VocabStatus status);

    long countByUserIdAndStatusAndSourceTypeNot(String userId, VocabStatus status, VocabSourceType sourceType);

    long countByUserIdAndVocabListIdAndStatusNot(String userId, Integer listId, VocabStatus status);

    boolean existsByUserIdAndWord(String userId, String word);

    long countByUserIdAndSourceType(String userId, VocabSourceType sourceType);

    List<Vocab> findByUserIdAndVocabListId(String userId, Integer listId);
}
