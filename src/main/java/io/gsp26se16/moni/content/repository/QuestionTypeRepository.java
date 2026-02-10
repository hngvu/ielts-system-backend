package io.gsp26se16.moni.content.repository;

import io.gsp26se16.moni.content.entity.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionTypeRepository extends JpaRepository<QuestionType, Integer> {
    Optional<QuestionType> findByCode(String code);
}
