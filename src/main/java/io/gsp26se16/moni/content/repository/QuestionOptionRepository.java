package io.gsp26se16.moni.content.repository;

import io.gsp26se16.moni.content.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Integer> {
}
