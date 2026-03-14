package io.gsp26se16.moni.practice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.practice.entity.Attempt;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Integer> {
    List<Attempt> findByUserOrderBySubmittedAtDesc(Users user);
}
