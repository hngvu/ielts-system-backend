package io.gsp26se16.moni.vocab.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.gsp26se16.moni.vocab.entity.CuratedWord;

public interface CuratedWordRepository extends JpaRepository<CuratedWord, Integer> {

    @Query("SELECT c FROM CuratedWord c WHERE "
            + "(:band IS NULL OR c.band = :band) AND "
            + "(:topic IS NULL OR c.topic = :topic) AND "
            + "(:pos IS NULL OR LOWER(c.pos) = LOWER(CAST(:pos AS text))) AND "
            + "(:search IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))")
    Page<CuratedWord> findByFilters(
            @Param("band") String band,
            @Param("topic") String topic,
            @Param("pos") String pos,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c.band AS band, MIN(c.cefrLevel) AS cefrLevel, COUNT(c) AS wordCount "
            + "FROM CuratedWord c GROUP BY c.band ORDER BY c.band")
    List<Object[]> countByBand();

    @Query("SELECT c.topic, COUNT(c) FROM CuratedWord c "
            + "WHERE c.topic IS NOT NULL GROUP BY c.topic ORDER BY c.topic")
    List<Object[]> countByTopic();
}
