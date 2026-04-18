package io.gsp26se16.moni.vocab.service;

import io.gsp26se16.moni.vocab.dto.VocabSearchResponse;

public interface VocabSearchService {

    VocabSearchResponse searchWord(String credentialId, String query);
}
