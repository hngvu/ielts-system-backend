package io.gsp26se16.moni.practice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.AttemptAnswer;

@Repository
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Integer> {
    List<AttemptAnswer> findAllByAttempt(Attempt attempt);

    Optional<AttemptAnswer> findByAttemptAndQuestion(Attempt attempt, Question question);
}
