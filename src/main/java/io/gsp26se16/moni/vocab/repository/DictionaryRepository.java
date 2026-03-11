package io.gsp26se16.moni.vocab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.vocab.entity.Dictionary;

@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, Integer> {
    Optional<Dictionary> findFirstByWordIgnoreCase(String word);
}
