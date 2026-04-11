package io.gsp26se16.moni.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.content.entity.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Integer> {
    boolean existsByTagsId(Integer tagId);
}
