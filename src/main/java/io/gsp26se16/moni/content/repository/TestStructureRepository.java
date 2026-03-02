package io.gsp26se16.moni.content.repository;

import io.gsp26se16.moni.content.entity.TestStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestStructureRepository extends JpaRepository<TestStructure, Integer> {
    // Tìm cấu trúc của một đề thi cụ thể
    List<TestStructure> findByTestId(Integer testId);
    void deleteByTestIdAndStimulusId(Integer testId, Integer stimulusId);
    boolean existsByTestIdAndStimulusId(Integer testId, Integer stimulusId);
}
