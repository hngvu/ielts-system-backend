package io.gsp26se16.moni.content.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Stimulus;

@Repository
public interface StimulusRepository extends JpaRepository<Stimulus, Integer> {
    @Query("SELECT s FROM Stimulus s WHERE "
            + "(CAST(:keyword AS String) IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR s.skill = :skill)")
    Page<Stimulus> searchStimuli(@Param("keyword") String keyword, @Param("skill") Skill skill, Pageable pageable);
}
