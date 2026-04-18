package io.gsp26se16.moni.vocab.service;

import java.util.List;

import org.springframework.data.domain.Page;

import io.gsp26se16.moni.vocab.dto.BandSummary;
import io.gsp26se16.moni.vocab.dto.CuratedWordResponse;
import io.gsp26se16.moni.vocab.dto.TopicSummary;

public interface CuratedWordService {

    Page<CuratedWordResponse> browse(
            String band, String topic, String pos, String search, int page, int size, String userId);

    List<TopicSummary> getTopics();

    List<BandSummary> getBands();
}
