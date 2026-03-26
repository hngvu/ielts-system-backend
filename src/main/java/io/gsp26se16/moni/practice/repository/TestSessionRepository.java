package io.gsp26se16.moni.practice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.TestSessionStatus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.practice.entity.TestSession;

@Repository
public interface TestSessionRepository extends JpaRepository<TestSession, Integer> {
    Optional<TestSession> findByUserAndTestAndStatus(Users user, Test test, TestSessionStatus status);

    List<TestSession> findAllByStatus(TestSessionStatus status);
}
