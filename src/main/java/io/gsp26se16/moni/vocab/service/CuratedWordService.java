package io.gsp26se16.moni.vocab.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.vocab.dto.BandSummary;
import io.gsp26se16.moni.vocab.dto.CuratedWordResponse;
import io.gsp26se16.moni.vocab.dto.TopicSummary;
import io.gsp26se16.moni.vocab.entity.CuratedWord;
import io.gsp26se16.moni.vocab.repository.CuratedWordRepository;
import io.gsp26se16.moni.vocab.repository.VocabRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CuratedWordService {

    private final CuratedWordRepository curatedWordRepository;
    private final VocabRepository vocabRepository;

    @Transactional(readOnly = true)
    public Page<CuratedWordResponse> browse(
            String band, String topic, String pos, String search, int page, int size, String userId) {

        String bandParam = (band != null && !band.isBlank()) ? band : null;
        String topicParam = (topic != null && !topic.isBlank()) ? topic : null;
        String posParam = (pos != null && !pos.isBlank()) ? pos : null;
        String searchParam = (search != null && !search.isBlank()) ? search : null;

        var pageable = PageRequest.of(page, size, Sort.by("word").ascending());
        Page<CuratedWord> result =
                curatedWordRepository.findByFilters(bandParam, topicParam, posParam, searchParam, pageable);

        return result.map(c -> toResponse(c, userId));
    }

    public List<TopicSummary> getTopics() {
        return curatedWordRepository.countByTopic().stream()
                .map(row -> TopicSummary.builder()
                        .topic((String) row[0])
                        .wordCount(((Number) row[1]).intValue())
                        .build())
                .toList();
    }

    public List<BandSummary> getBands() {
        return curatedWordRepository.countByBand().stream()
                .map(row -> BandSummary.builder()
                        .band((String) row[0])
                        .cefrLevel((String) row[1])
                        .wordCount(((Number) row[2]).intValue())
                        .build())
                .toList();
    }

    private CuratedWordResponse toResponse(CuratedWord c, String userId) {
        boolean saved = userId != null && vocabRepository.existsByUserIdAndWord(userId, c.getWord());
        String difficultyStr = c.getTags().stream()
                .filter(t -> t.getType() == TagType.DIFFICULTY)
                .map(Tag::getName)
                .findFirst()
                .orElse(null);
        String topicStr = c.getTags().stream()
                .filter(t -> t.getType() == TagType.TOPIC)
                .map(Tag::getName)
                .findFirst()
                .orElse(null);

        return CuratedWordResponse.builder()
                .id(c.getId())
                .word(c.getWord())
                .phonetic(c.getPhonetic())
                .pos(c.getPos())
                .definition(c.getDefinition())
                .example(c.getExample())
                .meaning(c.getMeaning())
                .band(difficultyStr)
                .cefrLevel(difficultyStr)
                .topic(topicStr)
                .saved(saved)
                .build();
    }
}
