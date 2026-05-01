package io.gsp26se16.moni.roadmap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.roadmap.entity.MetricAiSummaryCache;

@Repository
public interface MetricAiSummaryCacheRepository extends JpaRepository<MetricAiSummaryCache, String> {}
