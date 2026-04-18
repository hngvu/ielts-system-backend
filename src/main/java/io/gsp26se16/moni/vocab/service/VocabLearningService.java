package io.gsp26se16.moni.vocab.service;

import java.util.List;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.vocab.dto.*;

public interface VocabLearningService {

    List<VocabResponse> getDueReview(String credentialId, int limit);

    void submitReview(String credentialId, Integer vocabId, int quality);

    ReviewStatsResponse getReviewStats(String credentialId);

    List<VocabResponse> generateRoadmapVocabList(Users user, List<String> targetLevels, String topic, int count);

    void submitRoadmapVocabList(Users user, List<Integer> notLearnedIds, List<Integer> learnedIds);

    void processQuizResults(Users user, List<String> correctWords, List<String> incorrectWords);

    void markWordsAsMastered(Users user, List<String> correctWords);

    QuizResponse generateRoadmapQuiz(Users user);

    QuizResponse generateQuiz(String credentialId, int count, String source, String type, String band, String topic);

    WordMatchResponse getWordMatch(String credentialId, int count, String source, String band, String topic);
}
