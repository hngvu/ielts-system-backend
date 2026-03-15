package io.gsp26se16.moni.vocab.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.*;
import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.entity.VocabReview;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;
import io.gsp26se16.moni.vocab.repository.CuratedWordRepository;
import io.gsp26se16.moni.vocab.repository.VocabRepository;
import io.gsp26se16.moni.vocab.repository.VocabReviewRepository;
import io.gsp26se16.moni.vocab.util.SM2Calculator;
import io.gsp26se16.moni.vocab.util.SM2Calculator.SM2Result;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VocabLearningService {

    private final VocabRepository vocabRepository;
    private final VocabReviewRepository vocabReviewRepository;
    private final CuratedWordRepository curatedWordRepository;
    private final VocabAuthHelper authHelper;

    public List<VocabResponse> getDueReview(String credentialId, int limit) {
        Users user = authHelper.getUser(credentialId);
        List<Vocab> due = vocabRepository.findByUserIdAndNextReviewAtBeforeAndStatusNot(
                user.getId(), LocalDateTime.now(), VocabStatus.ARCHIVED);
        return due.stream().limit(limit).map(this::toResponse).toList();
    }

    @Transactional
    public void submitReview(String credentialId, Integer vocabId, int quality) {
        Users user = authHelper.getUser(credentialId);
        Vocab vocab = vocabRepository.findById(vocabId).orElseThrow(() -> new AppException(ErrorCode.VOCAB_NOT_FOUND));
        if (!vocab.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        SM2Result result =
                SM2Calculator.calculate(vocab.getEaseFactor(), vocab.getRepetitions(), vocab.getInterval(), quality);
        vocab.setEaseFactor(result.easeFactor());
        vocab.setRepetitions(result.repetitions());
        vocab.setInterval(result.interval());
        vocab.setNextReviewAt(result.nextReviewAt());

        if (result.easeFactor() >= 2.5 && result.repetitions() >= 5) {
            vocab.setStatus(VocabStatus.MASTERED);
        }
        vocabRepository.save(vocab);

        VocabReview review =
                VocabReview.builder().vocab(vocab).user(user).quality(quality).build();
        vocabReviewRepository.save(review);
    }

    public ReviewStatsResponse getReviewStats(String credentialId) {
        Users user = authHelper.getUser(credentialId);
        String userId = user.getId();
        int totalSaved = (int) vocabRepository.countByUserIdAndStatus(userId, VocabStatus.ACTIVE)
                + (int) vocabRepository.countByUserIdAndStatus(userId, VocabStatus.MASTERED);
        int dueToday = vocabRepository
                .findByUserIdAndNextReviewAtBeforeAndStatusNot(userId, LocalDateTime.now(), VocabStatus.ARCHIVED)
                .size();
        int masteredCount = (int) vocabRepository.countByUserIdAndStatus(userId, VocabStatus.MASTERED);
        return ReviewStatsResponse.builder()
                .totalSaved(totalSaved)
                .dueToday(dueToday)
                .masteredCount(masteredCount)
                .reviewedToday(0)
                .build();
    }

    public QuizResponse generateQuiz(
            String credentialId, int count, String source, String type, String band, String topic) {
        List<WordEntry> pool = buildPool(credentialId, source, band, topic);
        if (pool.size() < 4) {
            return QuizResponse.builder().questions(List.of()).source(source).build();
        }

        List<WordEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int questionCount = Math.min(count, shuffled.size());
        Random rand = new Random();
        List<QuizQuestion> questions = new ArrayList<>();

        for (int i = 0; i < questionCount; i++) {
            WordEntry correct = shuffled.get(i);
            String qType =
                    (type == null || "random".equals(type)) ? (rand.nextBoolean() ? "def2word" : "word2def") : type;

            List<WordEntry> distractors = new ArrayList<>(pool);
            distractors.remove(correct);
            Collections.shuffle(distractors);
            List<WordEntry> distractors3 = distractors.subList(0, Math.min(3, distractors.size()));

            List<String> optionTexts = new ArrayList<>();
            String prompt;

            if ("def2word".equals(qType)) {
                prompt = correct.definition() != null ? correct.definition() : correct.meaning();
                optionTexts.add(correct.word());
                distractors3.forEach(d -> optionTexts.add(d.word()));
            } else if ("fillblank".equals(qType) && correct.example() != null) {
                prompt = correct.example().replaceAll("(?i)" + java.util.regex.Pattern.quote(correct.word()), "______");
                optionTexts.add(correct.word());
                distractors3.forEach(d -> optionTexts.add(d.word()));
            } else { // word2def
                prompt = correct.word();
                optionTexts.add(correct.definition() != null ? correct.definition() : correct.meaning());
                distractors3.forEach(d -> optionTexts.add(d.definition() != null ? d.definition() : d.meaning()));
            }

            List<Integer> indices = new ArrayList<>();
            for (int j = 0; j < optionTexts.size(); j++) indices.add(j);
            Collections.shuffle(indices);
            List<String> shuffledOptions =
                    indices.stream().map(optionTexts::get).toList();
            int correctIdx = indices.indexOf(0);

            questions.add(QuizQuestion.builder()
                    .id(i + 1)
                    .type(qType)
                    .prompt(prompt)
                    .options(shuffledOptions)
                    .correctIndex(correctIdx)
                    .word(correct.word())
                    .explanation(correct.meaning())
                    .build());
        }

        return QuizResponse.builder().questions(questions).source(source).build();
    }

    public WordMatchResponse getWordMatch(String credentialId, int count, String source, String band, String topic) {
        List<WordMatchPair> pairs;
        if ("saved".equals(source)) {
            pairs = vocabRepository
                    .findByUserIdAndStatusNot(
                            authHelper.getUser(credentialId).getId(), VocabStatus.ARCHIVED, Pageable.ofSize(count))
                    .getContent()
                    .stream()
                    .map(v -> WordMatchPair.builder()
                            .word(v.getWord())
                            .definition(v.getDefinition())
                            .meaning(v.getMeaning())
                            .build())
                    .toList();
        } else {
            pairs =
                    curatedWordRepository
                            .findByFilters(band, topic, null, null, Pageable.ofSize(count))
                            .getContent()
                            .stream()
                            .map(c -> WordMatchPair.builder()
                                    .word(c.getWord())
                                    .definition(c.getDefinition())
                                    .meaning(c.getMeaning())
                                    .build())
                            .toList();
        }
        return WordMatchResponse.builder().pairs(pairs).build();
    }

    // --- Helpers ---

    private List<WordEntry> buildPool(String credentialId, String source, String band, String topic) {
        if ("saved".equals(source)) {
            return vocabRepository
                    .findByUserIdAndStatusNot(
                            authHelper.getUser(credentialId).getId(), VocabStatus.ARCHIVED, Pageable.ofSize(200))
                    .getContent()
                    .stream()
                    .map(v -> new WordEntry(v.getWord(), v.getDefinition(), v.getMeaning(), v.getExample()))
                    .toList();
        }
        return curatedWordRepository.findByFilters(band, topic, null, null, Pageable.ofSize(200)).getContent().stream()
                .map(c -> new WordEntry(c.getWord(), c.getDefinition(), c.getMeaning(), c.getExample()))
                .toList();
    }

    private VocabResponse toResponse(Vocab v) {
        return VocabResponse.builder()
                .id(v.getId())
                .word(v.getWord())
                .phonetic(v.getPhonetic())
                .pos(v.getPos())
                .definition(v.getDefinition())
                .example(v.getExample())
                .meaning(v.getMeaning())
                .audioUrl(v.getAudioUrl())
                .status(v.getStatus())
                .collectionName(v.getVocabList() != null ? v.getVocabList().getTitle() : null)
                .nextReviewAt(v.getNextReviewAt())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private record WordEntry(String word, String definition, String meaning, String example) {}
}
