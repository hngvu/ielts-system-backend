package io.gsp26se16.moni.vocab.service;

import io.gsp26se16.moni.vocab.dto.VocabLookupResponse;

public interface VocabLookupService {

    VocabLookupResponse lookupWord(String word);
}
