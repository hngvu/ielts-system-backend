package io.gsp26se16.moni.practice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.practice.entity.TestSession;

@Repository
public interface TestSessionRepository extends JpaRepository<TestSession, Integer> {}
