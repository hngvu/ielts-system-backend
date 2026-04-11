package io.gsp26se16.moni.content.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.tag.entity.Tag;

@Repository
public interface StimulusRepository extends JpaRepository<Stimulus, Integer> {
    @Query("SELECT s FROM Stimulus s WHERE "
            + "(CAST(:keyword AS String) IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR s.skill = :skill)")
    Page<Stimulus> searchStimuli(@Param("keyword") String keyword, @Param("skill") Skill skill, Pageable pageable);

    @Query("SELECT DISTINCT q.questionGroup.stimulus FROM Question q " + "JOIN q.tags qt "
            + "WHERE q.questionGroup.stimulus.skill = :skill AND qt IN :weakTags")
    List<Stimulus> findSmartStimuli(@Param("skill") Skill skill, @Param("weakTags") List<Tag> weakTags);

    List<Stimulus> findBySkill(Skill skill);

    boolean existsByTagsId(Integer tagId);

    @Query("SELECT count(q) FROM Question q WHERE q.questionGroup.stimulus.id = :stimulusId")
    int countQuestionsByStimulusId(@Param("stimulusId") Integer stimulusId);
}
