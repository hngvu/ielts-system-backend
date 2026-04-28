package io.gsp26se16.moni.ai.writing.service;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.gsp26se16.moni.ai.writing.request.WritingRequest;

public interface WritingTask1Service {

    Map<String, Object> score(WritingRequest request) throws JsonProcessingException;

    /**
     * AI scoring only — no submission, no evaluation record, no metric update.
     * Used by placement test grading.
     */
    Map<String, Object> scoreOnly(WritingRequest request) throws JsonProcessingException;
}
