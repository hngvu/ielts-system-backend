package io.gsp26se16.moni.vocab.service;

import java.util.List;

import io.gsp26se16.moni.vocab.dto.CreateVocabListRequest;
import io.gsp26se16.moni.vocab.dto.VocabListResponse;

public interface VocabListService {

    List<VocabListResponse> getUserLists(String userId);

    VocabListResponse createList(String userId, CreateVocabListRequest request);

    VocabListResponse updateList(String userId, Integer listId, CreateVocabListRequest request);

    void deleteList(String userId, Integer listId);
}
