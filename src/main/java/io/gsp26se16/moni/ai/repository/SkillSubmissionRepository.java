package io.gsp26se16.moni.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.entity.SkillSubmission;

@Repository
public interface SkillSubmissionRepository extends JpaRepository<SkillSubmission, Long> {}
