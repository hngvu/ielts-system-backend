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
            + "(:band IS NULL OR EXISTS (SELECT t FROM c.tags t WHERE t.name = :band AND t.type = 'DIFFICULTY')) AND "
            + "(:topic IS NULL OR EXISTS (SELECT t FROM c.tags t WHERE t.name = :topic AND t.type = 'TOPIC')) AND "
            + "(:pos IS NULL OR LOWER(c.pos) = LOWER(CAST(:pos AS text))) AND "
            + "(:search IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))")
    Page<CuratedWord> findByFilters(
            @Param("band") String band,
            @Param("topic") String topic,
            @Param("pos") String pos,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT t.name AS band, MIN(t.name) AS cefrLevel, COUNT(c) AS wordCount "
            + "FROM CuratedWord c JOIN c.tags t ON t.type = 'DIFFICULTY' "
            + "GROUP BY t.name ORDER BY t.name")
    List<Object[]> countByBand();

    @Query("SELECT t.name, COUNT(c) FROM CuratedWord c JOIN c.tags t ON t.type = 'TOPIC' "
            + "GROUP BY t.name ORDER BY t.name")
    List<Object[]> countByTopic();

    @Query("SELECT c FROM CuratedWord c WHERE c.meaning IS NULL OR c.definition IS NULL ORDER BY c.id")
    List<CuratedWord> findUnenriched();

    long countByMeaningIsNotNull();

    @Query("SELECT COUNT(c) FROM CuratedWord c JOIN c.tags t WHERE t.type = 'DIFFICULTY' AND t.name IN :bands")
    long countByBandIn(@Param("bands") java.util.Collection<String> bands);

    @Query("SELECT c.word FROM CuratedWord c JOIN c.tags t WHERE t.type = 'DIFFICULTY' AND t.name IN :bands")
    java.util.List<String> findWordByBandIn(@Param("bands") java.util.Collection<String> bands);

    @Query("SELECT c FROM CuratedWord c WHERE "
            + "EXISTS (SELECT t FROM c.tags t WHERE t.name IN :bands AND t.type = 'DIFFICULTY')")
    Page<CuratedWord> findByBands(@Param("bands") java.util.Collection<String> bands, Pageable pageable);

    @Query("SELECT c FROM CuratedWord c WHERE "
            + "(:band IS NULL OR EXISTS (SELECT t FROM c.tags t WHERE t.name = :band AND t.type = 'DIFFICULTY')) AND "
            + "(:topic IS NULL OR EXISTS (SELECT t FROM c.tags t WHERE t.name = :topic AND t.type = 'TOPIC')) AND "
            + "NOT EXISTS (SELECT v FROM Vocab v WHERE v.word = c.word AND v.user.id = :userId)")
    Page<CuratedWord> findUnlearnedByFilters(
            @Param("band") String band,
            @Param("topic") String topic,
            @Param("userId") String userId,
            Pageable pageable);

    @Query("SELECT c FROM CuratedWord c WHERE "
            + "EXISTS (SELECT t FROM c.tags t WHERE t.name IN :bands AND t.type = 'DIFFICULTY') AND "
            + "NOT EXISTS (SELECT v FROM Vocab v WHERE v.word = c.word AND v.user.id = :userId)")
    Page<CuratedWord> findUnlearnedByBands(
            @Param("bands") java.util.Collection<String> bands, @Param("userId") String userId, Pageable pageable);
}
