package io.gsp26se16.moni.placement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.placement.entity.PlacementConfig;

@Repository
public interface PlacementConfigRepository extends JpaRepository<PlacementConfig, Integer> {

    Optional<PlacementConfig> findByIsActiveTrue();

    List<PlacementConfig> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("UPDATE PlacementConfig pc SET pc.isActive = false WHERE pc.isActive = true")
    void deactivateAll();
}
