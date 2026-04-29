package io.gsp26se16.moni.expert.service;

import io.gsp26se16.moni.authentication.entity.Users;

/**
 * Cập nhật LearnerMetric khi expert chấm điểm Speaking/Writing.
 * Mirror logic update metric từ AI flow để roadmap tuần kế cập nhật đúng.
 */
public interface ExpertLearnerMetricService {

    /**
     * Update metric Speaking từ điểm expert chấm + auto-complete weekly plan slot.
     * @param testId nullable — null thì chỉ update metric criteria, không animate slot
     */
    void updateSpeakingFromExpert(
            Users user,
            Integer testId,
            Double fluency,
            Double vocabulary,
            Double grammar,
            Double pronunciation,
            double finalBand);

    /**
     * Update metric Writing từ điểm expert chấm + auto-complete weekly plan slot.
     * @param writingSubmissionId nullable — dùng để load stimulus tags
     */
    void updateWritingFromExpert(
            Users user,
            Long writingSubmissionId,
            Double taskResponse,
            Double coherence,
            Double lexicalResource,
            Double grammaticalRange,
            double finalBand);
}
