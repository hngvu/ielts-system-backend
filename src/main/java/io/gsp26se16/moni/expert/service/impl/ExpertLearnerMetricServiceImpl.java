package io.gsp26se16.moni.expert.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.expert.service.ExpertLearnerMetricService;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Mirror logic update metric từ AI flow (ConversationEngine, WritingTask{1,2}ServiceImpl)
 * nhưng input là điểm expert đã chấm trực tiếp (không qua LLM assessment Map).
 *
 * Cùng mapping criteria → tag code:
 * - Speaking: FC ← fluency, LR ← vocab, GRA ← grammar, PR ← pronunciation
 * - Writing: W_TA ← taskResponse, W_CC ← coherence, W_LR ← lexical, W_GRA ← grammar
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpertLearnerMetricServiceImpl implements ExpertLearnerMetricService {

    LearnerMetricRepository learnerMetricRepository;
    TagRepository tagRepository;
    TestStructureRepository testStructureRepository;
    WritingSubmissionRepository writingSubmissionRepository;
    WeeklyPlanService weeklyPlanService;

    @Override
    @Transactional
    public void updateSpeakingFromExpert(
            Users user,
            Integer testId,
            Double fluency,
            Double vocabulary,
            Double grammar,
            Double pronunciation,
            double finalBand) {
        if (user == null) return;
        try {
            Map<String, Double> criterionBands = new LinkedHashMap<>();
            putIfPresent(criterionBands, "FC", fluency);
            putIfPresent(criterionBands, "LR", vocabulary);
            putIfPresent(criterionBands, "GRA", grammar);
            putIfPresent(criterionBands, "PR", pronunciation);

            for (Map.Entry<String, Double> entry : criterionBands.entrySet()) {
                Tag tag = tagRepository.findByCode(entry.getKey()).orElse(null);
                if (tag == null) continue;
                double s = entry.getValue() / 9.0;
                boolean isCorrect = s >= 0.6;
                updateMetricBKT(user, tag, Skill.SPEAKING, isCorrect, s);
            }

            // Topic tags từ test structures (giống AI flow)
            if (testId != null) {
                List<TestStructure> structures = testStructureRepository.findByTestId(testId);
                if (structures != null) {
                    double finalS = finalBand / 9.0;
                    boolean finalIsCorrect = finalS >= 0.6;
                    for (TestStructure structure : structures) {
                        if (structure.getStimulus() != null
                                && structure.getStimulus().getTags() != null) {
                            for (Tag tag : structure.getStimulus().getTags()) {
                                if (tag.getType() == TagType.TOPIC) {
                                    updateMetricBKT(user, tag, Skill.SPEAKING, finalIsCorrect, finalS);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("updateSpeakingFromExpert failed: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void updateWritingFromExpert(
            Users user,
            Long writingSubmissionId,
            Double taskResponse,
            Double coherence,
            Double lexicalResource,
            Double grammaticalRange,
            double finalBand) {
        if (user == null) return;
        try {
            Map<String, Double> criterionBands = new LinkedHashMap<>();
            putIfPresent(criterionBands, "W_TA", taskResponse);
            putIfPresent(criterionBands, "W_CC", coherence);
            putIfPresent(criterionBands, "W_LR", lexicalResource);
            putIfPresent(criterionBands, "W_GRA", grammaticalRange);

            for (Map.Entry<String, Double> entry : criterionBands.entrySet()) {
                Tag tag = tagRepository.findByCode(entry.getKey()).orElse(null);
                if (tag == null) continue;
                double mastery = entry.getValue() / 9.0;
                updateCriterionMetric(user, tag, Skill.WRITING, mastery);
            }

            // Stimulus tags + auto-complete slot
            Integer testId = null;
            if (writingSubmissionId != null) {
                WritingSubmission submission = writingSubmissionRepository
                        .findById(writingSubmissionId)
                        .orElse(null);
                if (submission != null) {
                    testId = submission.getTestId();
                    if (submission.getStimulus() != null
                            && submission.getStimulus().getTags() != null) {
                        double finalS = finalBand / 9.0;
                        boolean finalIsCorrect = finalS >= 0.6;
                        for (Tag tag : submission.getStimulus().getTags()) {
                            if (tag.getType() == TagType.WRITING_TYPE || tag.getType() == TagType.TOPIC) {
                                updateMetricBKT(user, tag, Skill.WRITING, finalIsCorrect, finalS);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("updateWritingFromExpert failed: {}", e.getMessage(), e);
        }
    }

    private void putIfPresent(Map<String, Double> map, String key, Double value) {
        if (value != null) map.put(key, value);
    }

    /**
     * BKT update — copy logic từ ConversationEngine.updateMetricBKT.
     * Skill param vì cùng tag (vd LR) tồn tại cho cả Speaking và Writing.
     */
    private void updateMetricBKT(Users user, Tag tag, Skill skill, boolean isCorrect, double scoreNormalized) {
        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, skill)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(skill);
                    m.setMasteryLevel(0.3);
                    m.setConfidenceScore(0.0);
                    m.setAttemptCount(0);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        double pL = metric.getMasteryLevel();
        double pGuess = metric.getPGuess();
        double pSlip = metric.getPSlip();
        double pTransit = metric.getPTransit();

        double pLnew;
        if (isCorrect) {
            double pCorrectGivenL = 1.0 - pSlip;
            double pCorrectGivenNotL = pGuess;
            double pCorrect = (pL * pCorrectGivenL) + ((1.0 - pL) * pCorrectGivenNotL);
            pLnew = (pL * pCorrectGivenL) / pCorrect;
        } else {
            double pIncorrectGivenL = pSlip;
            double pIncorrectGivenNotL = 1.0 - pGuess;
            double pIncorrect = (pL * pIncorrectGivenL) + ((1.0 - pL) * pIncorrectGivenNotL);
            pLnew = (pL * pIncorrectGivenL) / pIncorrect;
        }

        double pLfinal = pLnew + ((1.0 - pLnew) * pTransit);
        pLfinal = Math.max(0.0, Math.min(1.0, pLfinal));

        metric.setMasteryLevel(pLfinal);
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
        double calculatedConfidence = 1.0 - (1.0 / (metric.getAttemptCount() + 1.0));
        metric.setConfidenceScore(calculatedConfidence);
        metric.setUpdatedAt(LocalDateTime.now());

        learnerMetricRepository.save(metric);
        log.debug(
                "[Expert-BKT] tag={}, skill={}, attempt={}, pLfinal={}",
                tag.getName(),
                skill,
                metric.getAttemptCount(),
                pLfinal);
    }

    /**
     * Criterion update (Writing) — band trực tiếp làm mastery (0-1).
     * Copy logic từ WritingTask1ServiceImpl.updateCriterionMetric.
     */
    private void updateCriterionMetric(Users user, Tag tag, Skill skill, double mastery) {
        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, skill)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(skill);
                    m.setMasteryLevel(0.0);
                    m.setConfidenceScore(0.0);
                    m.setAttemptCount(0);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        metric.setMasteryLevel(Math.max(0.0, Math.min(1.0, mastery)));
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
        double conf = 1.0 - (1.0 / (metric.getAttemptCount() + 1.0));
        metric.setConfidenceScore(conf);
        metric.setUpdatedAt(LocalDateTime.now());
        learnerMetricRepository.save(metric);
    }
}
