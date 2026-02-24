package io.gsp26se16.moni.content.repository;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestRepository extends JpaRepository<Test, Integer> {

    @Query("SELECT t FROM Test t WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:skill IS NULL OR t.skill = :skill)")
    Page<Test> searchTests(@Param("keyword") String keyword,
                           @Param("skill") Skill skill,
                           Pageable pageable);
}
