package io.gsp26se16.moni.vocab.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.gsp26se16.moni.vocab.entity.VocabList;

public interface VocabListRepository extends JpaRepository<VocabList, Integer> {

    List<VocabList> findByUserId(String userId);

    Optional<VocabList> findByUserIdAndIsDefaultTrue(String userId);
}
