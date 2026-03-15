package io.gsp26se16.moni.vocab.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.gsp26se16.moni.vocab.entity.CuratedWord;

public interface CuratedWordRepository extends JpaRepository<CuratedWord, Integer> {

    Page<CuratedWord> findByBand(String band, Pageable pageable);

    Page<CuratedWord> findByTopic(String topic, Pageable pageable);

    Page<CuratedWord> findByBandAndTopic(String band, String topic, Pageable pageable);

    @Query("SELECT DISTINCT c.topic FROM CuratedWord c WHERE c.topic IS NOT NULL ORDER BY c.topic")
    List<String> findDistinctTopics();
}
