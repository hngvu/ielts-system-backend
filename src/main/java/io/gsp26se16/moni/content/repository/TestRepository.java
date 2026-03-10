package io.gsp26se16.moni.content.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Test;

@Repository
public interface TestRepository extends JpaRepository<Test, Integer> {

    @Query("SELECT t FROM Test t WHERE "
            + "(CAST(:keyword AS String) IS NULL OR CAST(:keyword AS String) = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR t.skill = :skill)")
    Page<Test> searchTests(@Param("keyword") String keyword, @Param("skill") Skill skill, Pageable pageable);

    @Query("SELECT t FROM Test t WHERE t.status = :status AND "
            + "(CAST(:keyword AS String) IS NULL OR CAST(:keyword AS String) = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))) AND "
            + "(:skill IS NULL OR t.skill = :skill)")
    Page<Test> searchByStatus(
            @Param("status") PublishStatus status,
            @Param("keyword") String keyword,
            @Param("skill") Skill skill,
            Pageable pageable);
}
