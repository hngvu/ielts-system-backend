package io.gsp26se16.moni.vocab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.vocab.entity.Dictionary;

@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, Integer> {
    Optional<Dictionary> findFirstByWordIgnoreCase(String word);

    @Modifying
    @Query(
            value =
                    "INSERT INTO dictionary (word, phonetic, pos, meaning, explanation, collocation, audio_url, examples, definition, example) "
                            + "VALUES (:word, :phonetic, :pos, :meaning, :explanation, :collocation, :audioUrl, :examples, :definition, :example) "
                            + "ON CONFLICT (word) DO UPDATE SET "
                            + "phonetic = COALESCE(EXCLUDED.phonetic, dictionary.phonetic), "
                            + "pos = COALESCE(EXCLUDED.pos, dictionary.pos), "
                            + "meaning = COALESCE(EXCLUDED.meaning, dictionary.meaning), "
                            + "explanation = COALESCE(EXCLUDED.explanation, dictionary.explanation), "
                            + "collocation = COALESCE(EXCLUDED.collocation, dictionary.collocation), "
                            + "audio_url = COALESCE(EXCLUDED.audio_url, dictionary.audio_url), "
                            + "examples = COALESCE(EXCLUDED.examples, dictionary.examples)",
            nativeQuery = true)
    void upsert(
            @Param("word") String word,
            @Param("phonetic") String phonetic,
            @Param("pos") String pos,
            @Param("meaning") String meaning,
            @Param("explanation") String explanation,
            @Param("collocation") String collocation,
            @Param("audioUrl") String audioUrl,
            @Param("examples") String examples,
            @Param("definition") String definition,
            @Param("example") String example);
}
