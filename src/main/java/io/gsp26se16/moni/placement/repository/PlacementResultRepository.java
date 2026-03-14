package io.gsp26se16.moni.placement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.placement.entity.PlacementResult;

@Repository
public interface PlacementResultRepository extends JpaRepository<PlacementResult, Integer> {

    Optional<PlacementResult> findFirstByUserOrderByCompletedAtDesc(Users user);

    void deleteAllByUser(Users user);
}
