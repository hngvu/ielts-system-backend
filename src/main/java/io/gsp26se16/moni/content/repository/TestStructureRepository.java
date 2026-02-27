package io.gsp26se16.moni.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.content.entity.TestStructure;

@Repository
public interface TestStructureRepository extends JpaRepository<TestStructure, Integer> {
    // Tìm cấu trúc của một đề thi cụ thể
    List<TestStructure> findByTestId(Integer testId);
}
