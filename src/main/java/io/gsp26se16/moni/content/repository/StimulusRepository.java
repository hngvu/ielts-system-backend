package io.gsp26se16.moni.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.content.entity.Stimulus;

@Repository
public interface StimulusRepository extends JpaRepository<Stimulus, Integer> {}
