package io.gsp26se16.moni.vocab.service;

import java.util.Map;

public interface CuratedWordEnricher {

    Map<String, Object> getStatus();

    void startEnrichment();
}
