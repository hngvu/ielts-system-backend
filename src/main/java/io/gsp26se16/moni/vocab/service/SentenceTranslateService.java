package io.gsp26se16.moni.vocab.service;

import io.gsp26se16.moni.vocab.dto.SentenceTranslateResponse;

public interface SentenceTranslateService {

    SentenceTranslateResponse translate(String text);
}
