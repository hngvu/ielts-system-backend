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

    @Query("SELECT DISTINCT c.band FROM CuratedWord c WHERE c.band IS NOT NULL ORDER BY c.band")
    List<String> findDistinctBands();

    @Query(
            "SELECT c FROM CuratedWord c WHERE (:band IS NULL OR c.band = :band) AND (:topic IS NULL OR c.topic = :topic) AND (:cefrLevel IS NULL OR c.cefrLevel = :cefrLevel)")
    Page<CuratedWord> findByFilters(
            @org.springframework.data.repository.query.Param("band") String band,
            @org.springframework.data.repository.query.Param("topic") String topic,
            @org.springframework.data.repository.query.Param("cefrLevel") String cefrLevel,
            Pageable pageable);
}
