package io.gsp26se16.moni.expert.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.expert.entity.ExpertProfile;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;

@Repository
public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Integer> {
    Optional<ExpertProfile> findByUser_Id(String userId);

    List<ExpertProfile> findByStatus(ExpertStatus status);

    List<ExpertProfile> findBySpecializationAndStatus(ExpertSpecialization specialization, ExpertStatus status);

    List<ExpertProfile> findBySpecializationInAndStatus(
            java.util.Collection<ExpertSpecialization> specializations, ExpertStatus status);
}
