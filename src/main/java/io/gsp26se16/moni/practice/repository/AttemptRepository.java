package io.gsp26se16.moni.practice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.TestSession;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Integer> {
    List<Attempt> findByUserOrderBySubmittedAtDesc(Users user);

    Optional<Attempt> findByTestSession(TestSession testSession);
}
