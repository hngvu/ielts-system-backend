package io.gsp26se16.moni.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import io.gsp26se16.moni.content.entity.Test;

@Repository
public interface TestRepository extends JpaRepository<Test, Integer> {

    @Query("SELECT t FROM Test t WHERE "
            + "(CAST(:keyword AS String) IS NULL OR CAST(:keyword AS String) = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR t.skill = :skill) AND "
            + "(:section IS NULL OR t.section = :section) AND "
            + "(t.testMode IS NULL OR t.testMode != 'FULL_TEST')")
    Page<Test> searchTests(
            @Param("keyword") String keyword,
            @Param("skill") Skill skill,
            @Param("section") Integer section,
            Pageable pageable);

    @Query("SELECT t FROM Test t WHERE t.status = :status AND "
            + "(CAST(:keyword AS String) IS NULL OR CAST(:keyword AS String) = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR t.skill = :skill) AND "
            + "(:section IS NULL OR t.section = :section)")
    Page<Test> searchByStatus(
            @Param("status") PublishStatus status,
            @Param("keyword") String keyword,
            @Param("skill") Skill skill,
            @Param("section") Integer section,
            Pageable pageable);

    @Query(
            "SELECT COUNT(q) FROM TestStructure ts JOIN ts.stimulus s JOIN s.questionGroups qg JOIN qg.questions q WHERE ts.test.id = :testId")
    int countQuestionsByTestId(@Param("testId") Integer testId);

    @Query(
            "SELECT DISTINCT qg.questionType.code FROM TestStructure ts JOIN ts.stimulus s JOIN s.questionGroups qg WHERE ts.test.id = :testId AND qg.questionType IS NOT NULL")
    List<String> findQuestionTypesByTestId(@Param("testId") Integer testId);

    @Query("SELECT COUNT(a) FROM Attempt a WHERE a.testSession.test.id = :testId")
    long countAttemptsByTestId(@Param("testId") Integer testId);

    @Query(
            value =
                    "SELECT * FROM test WHERE skill = :skill AND test_mode = 'FULL_TEST' AND status = 'PUBLISHED' AND is_placement = true ORDER BY RANDOM() LIMIT 1",
            nativeQuery = true)
    Optional<Test> findRandomPublishedPlacementTest(@Param("skill") String skill);

    @Query(
            value =
                    "SELECT * FROM test WHERE skill = :skill AND test_mode = 'FULL_TEST' AND status = 'PUBLISHED' ORDER BY RANDOM() LIMIT 1",
            nativeQuery = true)
    Optional<Test> findRandomPublishedFullTest(@Param("skill") String skill);

    @Query(
            value = "SELECT * FROM test WHERE skill = :skill AND status = 'PUBLISHED' ORDER BY RANDOM() LIMIT 1",
            nativeQuery = true)
    Optional<Test> findRandomPublishedTest(@Param("skill") String skill);

    List<Test> findByTestModeOrderByIdDesc(TestMode testMode);

    List<Test> findByTestModeAndSkill(TestMode testMode, Skill skill);

    List<Test> findByTestModeAndSkillAndTestType(TestMode testMode, Skill skill, TestType testType);

    @Query("SELECT COUNT(t) FROM Test t WHERE t.testMode = :testMode AND t.skill = :skill")
    long countByTestModeAndSkill(@Param("testMode") TestMode testMode, @Param("skill") Skill skill);

    boolean existsByTagsId(Integer tagId);
}
