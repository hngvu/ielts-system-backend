package io.gsp26se16.moni.vocab.service;

import org.springframework.data.domain.Page;

import io.gsp26se16.moni.vocab.dto.ManualSaveVocabRequest;
import io.gsp26se16.moni.vocab.dto.SaveVocabRequest;
import io.gsp26se16.moni.vocab.dto.VocabDetailResponse;
import io.gsp26se16.moni.vocab.dto.VocabResponse;

public interface VocabService {

    Page<VocabResponse> getMyWords(String userId, int page, int size, Integer listId, String search);

    VocabResponse saveWord(String userId, SaveVocabRequest request);

    VocabResponse saveWordManual(String userId, ManualSaveVocabRequest request);

    void deleteWord(String userId, Integer vocabId);

    void moveWord(String userId, Integer vocabId, Integer targetListId);

    VocabDetailResponse getVocabDetail(String userId, Integer vocabId);
}
