package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.placement.entity.PlacementResult;
import io.gsp26se16.moni.placement.repository.PlacementResultRepository;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;
import io.gsp26se16.moni.roadmap.repository.GoalRepository;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.repository.WeeklyPlanRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final PlacementResultRepository placementResultRepository;
    private final WeeklyPlanService weeklyPlanService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final io.gsp26se16.moni.roadmap.repository.InsightSnapshotRepository insightSnapshotRepository;

    // =====================================================================
    // PUBLIC API
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<GoalResponse> getActiveGoals() {
        Users learner = getCurrentUser();
        List<Goal> activeGoals = goalRepository.findAllByUserAndStatus(learner, "ACTIVE");

        return activeGoals.stream()
                .map(goal -> GoalResponse.builder()
                        .goalId(goal.getId())
                        .skill(goal.getSkill())
                        .startingBand(goal.getStartingBand())
                        .targetBand(goal.getTargetBand())
                        .deadline(goal.getDeadline())
                        .status(goal.getStatus())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void createGoalsFromPlacement(
            Users user,
            double readingBand,
            double listeningBand,
            double writingBand,
            double speakingBand,
            Double targetReading,
            Double targetListening,
            Double targetWriting,
            Double targetSpeaking,
            LocalDate examDate) {

        Skill[] skills = {Skill.READING, Skill.LISTENING, Skill.WRITING, Skill.SPEAKING};
        double[] startingBands = {readingBand, listeningBand, writingBand, speakingBand};
        Double[] targets = {targetReading, targetListening, targetWriting, targetSpeaking};

        for (int i = 0; i < skills.length; i++) {
            Skill skill = skills[i];
            double startingBand = startingBands[i];
            Double targetBand = targets[i];

            if (targetBand == null || targetBand == 0) {
                targetBand = startingBand + 1.0;
            } else if (targetBand <= startingBand) {
                targetBand = startingBand + 0.5;
            }

            final Skill currentSkill = skill;
            goalRepository
                    .findTopByUserAndSkillAndStatusOrderByIdDesc(user, currentSkill, "ACTIVE")
                    .ifPresent(oldGoal -> {
                        oldGoal.setStatus("ARCHIVED");
                        goalRepository.save(oldGoal);
                        log.info("Archived old goal {} for skill {}", oldGoal.getId(), currentSkill);
                    });

            LocalDate deadline = (examDate != null) ? examDate : LocalDate.now().plusDays(90);

            Goal newGoal = new Goal();
            newGoal.setUser(user);
            newGoal.setSkill(skill);
            newGoal.setStartingBand(startingBand);
            newGoal.setTargetBand(targetBand);
            newGoal.setDeadline(deadline);
            newGoal.setStatus("ACTIVE");
            goalRepository.save(newGoal);

            log.info(
                    "Created Goal for skill {} (starting={}, target={}, deadline={})",
                    skill,
                    startingBand,
                    targetBand,
                    deadline);
        }

        // Generate the first weekly plan for this user
        try {
            weeklyPlanService.generateWeeklyPlan(user);
            log.info("Generated first weekly plan for user {}", user.getId());
        } catch (Exception e) {
            log.warn("Failed to generate weekly plan for user {}: {}", user.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LearnerRoadmapInsightsResponse getRoadmapInsights() {
        Users learner = getCurrentUser();
        return generateInsightsForUser(learner);
    }

    @Override
    @Transactional(readOnly = true)
    public LearnerRoadmapInsightsResponse getRoadmapInsightsByWeek(Integer weekNumber) {
        Users learner = getCurrentUser();

        java.util.Optional<io.gsp26se16.moni.roadmap.entity.InsightSnapshot> snapshotOpt =
                insightSnapshotRepository.findFirstByUserAndWeekNumberOrderByCreatedAtDesc(learner, weekNumber);
        if (snapshotOpt.isPresent()) {
            io.gsp26se16.moni.roadmap.entity.InsightSnapshot snap = snapshotOpt.get();
            LearnerRoadmapInsightsResponse insights = generateInsightsForUser(learner);

            insights.setCalibratedReading(snap.getReadingCalibrated());
            insights.setCalibratedListening(snap.getListeningCalibrated());
            insights.setCalibratedWriting(snap.getWritingCalibrated());
            insights.setCalibratedSpeaking(snap.getSpeakingCalibrated());
            insights.setCalibratedOverall(snap.getOverallCalibrated());
            insights.setMasteryIndex(snap.getMasteryIndex());
            insights.setConfidenceIndex(snap.getConfidenceIndex());
            insights.setLastMetricUpdatedAt(snap.getCreatedAt());

            return insights;
        }

        return generateInsightsForUser(learner);
    }

    @Override
    @Transactional
    public void snapshotInsightsForWeek(Users user, Integer weekNumber) {
        LearnerRoadmapInsightsResponse insights = generateInsightsForUser(user);

        io.gsp26se16.moni.roadmap.entity.InsightSnapshot snapshot =
                io.gsp26se16.moni.roadmap.entity.InsightSnapshot.builder()
                        .user(user)
                        .weekNumber(weekNumber)
                        .overallCalibrated(insights.getCalibratedOverall())
                        .readingCalibrated(insights.getCalibratedReading())
                        .listeningCalibrated(insights.getCalibratedListening())
                        .writingCalibrated(insights.getCalibratedWriting())
                        .speakingCalibrated(insights.getCalibratedSpeaking())
                        .masteryIndex(insights.getMasteryIndex())
                        .confidenceIndex(insights.getConfidenceIndex())
                        .createdAt(LocalDateTime.now())
                        .build();

        insightSnapshotRepository.save(snapshot);
    }

    // =====================================================================
    // INSIGHTS LOGIC
    // =====================================================================

    private LearnerRoadmapInsightsResponse generateInsightsForUser(Users learner) {

        PlacementResult placement = placementResultRepository
                .findFirstByUserOrderByCompletedAtDesc(learner)
                .orElse(null);

        List<LearnerMetric> allMetrics = learnerMetricRepository.findByUserOrderByUpdatedAtDesc(learner);
        double masteryIndex = computeAvg(
                allMetrics.stream().map(LearnerMetric::getMasteryLevel).toList(), 0.3);
        double confidenceIndex = computeAvg(
                allMetrics.stream().map(LearnerMetric::getConfidenceScore).toList(), 0.0);
        var lastMetricUpdatedAt =
                allMetrics.isEmpty() ? null : allMetrics.get(0).getUpdatedAt();

        LocalDate examDate = learner.getExamDate();
        Integer daysToExam =
                examDate != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate) : null;
        if (daysToExam != null && daysToExam < 0) daysToExam = 0;

        WeeklyPlan activeWeeklyPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(learner, "ACTIVE")
                .orElse(null);

        double curReading = weeklyPlanService.getCurrentBandForSkill(learner, Skill.READING, activeWeeklyPlan);
        double curListening = weeklyPlanService.getCurrentBandForSkill(learner, Skill.LISTENING, activeWeeklyPlan);
        double curWriting = weeklyPlanService.getCurrentBandForSkill(learner, Skill.WRITING, activeWeeklyPlan);
        double curSpeaking = weeklyPlanService.getCurrentBandForSkill(learner, Skill.SPEAKING, activeWeeklyPlan);

        Calibration calibration = calibrateBands(
                placement, masteryIndex, confidenceIndex, curReading, curListening, curWriting, curSpeaking);
        double achievableOverallByExam = computeAchievableOverallByExam(calibration.calibratedOverall, daysToExam);

        Double targetOverall = learner.getTargetBand();
        Integer dailyStudyTime =
                calculateRecommendedDailyStudyMinutes(calibration.calibratedOverall, targetOverall, daysToExam);

        boolean isScoreOverAmbitious = targetOverall != null
                && targetOverall > 0
                && daysToExam != null
                && targetOverall > (achievableOverallByExam + 0.25);

        boolean isTimeOverAmbitious = false;
        if (targetOverall != null
                && calibration.calibratedOverall > 0
                && targetOverall > calibration.calibratedOverall) {
            double gap = targetOverall - calibration.calibratedOverall;
            int effectiveDaysForMath = (daysToExam != null && daysToExam > 0) ? daysToExam : 90;
            int rawDailyMinutes = (int) Math.ceil((gap * 150.0 * 60.0) / effectiveDaysForMath);

            if (rawDailyMinutes > 240) {
                isTimeOverAmbitious = true;
            }
        }

        boolean targetOverAmbitious = isScoreOverAmbitious || isTimeOverAmbitious;
        String targetWarning = null;

        if (isTimeOverAmbitious) {
            targetWarning =
                    "Mục tiêu của bạn đòi hỏi học hơn 4 tiếng mỗi ngày. Điều này rất dễ gây quá tải (Burnout). Hệ thống khuyên bạn nên dời ngày thi lại, hoặc tạm thời hạ mục tiêu xuống 0.5 Band.";
        } else if (isScoreOverAmbitious) {
            targetWarning =
                    "Mục tiêu hiện tại có thể hơi quá tầm so với thời gian còn lại. Hãy tăng cường tần suất luyện tập hoặc điều chỉnh lại kỳ vọng.";
        }

        // Split metrics into weak (mastery < 0.5) and strong (mastery >= 0.5)
        List<LearnerMetric> allTagMetrics = learnerMetricRepository.findByUserOrderByMasteryLevelAsc(learner);

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> weakest = allTagMetrics.stream()
                .filter(m -> m.getMasteryLevel() != null && m.getMasteryLevel() < 0.5)
                .map(this::toTagMetric)
                .toList();

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> strongest = allTagMetrics.stream()
                .filter(m -> m.getMasteryLevel() == null || m.getMasteryLevel() >= 0.5)
                .sorted((a, b) -> Double.compare(
                        b.getMasteryLevel() != null ? b.getMasteryLevel() : 0,
                        a.getMasteryLevel() != null ? a.getMasteryLevel() : 0))
                .map(this::toTagMetric)
                .toList();

        return LearnerRoadmapInsightsResponse.builder()
                .examDate(examDate)
                .daysToExam(daysToExam)
                .recommendedDailyStudyMinutes(dailyStudyTime)
                .targetOverall(learner.getTargetBand())
                .targetReading(learner.getTargetReading())
                .targetListening(learner.getTargetListening())
                .targetWriting(learner.getTargetWriting())
                .targetSpeaking(learner.getTargetSpeaking())
                .placementSelfAssessed(placement != null ? placement.getIsSelfAssessed() : null)
                .placementCompletedAt(placement != null ? placement.getCompletedAt() : null)
                .placementOverall(placement != null ? placement.getOverallBand() : null)
                .placementReading(placement != null ? placement.getReadingBand() : null)
                .placementListening(placement != null ? placement.getListeningBand() : null)
                .placementWriting(placement != null ? placement.getWritingBand() : null)
                .placementSpeaking(placement != null ? placement.getSpeakingBand() : null)
                .calibratedOverall(calibration.calibratedOverall)
                .calibratedReading(calibration.calibratedReading)
                .calibratedListening(calibration.calibratedListening)
                .calibratedWriting(calibration.calibratedWriting)
                .calibratedSpeaking(calibration.calibratedSpeaking)
                .calibrationNote(calibration.note)
                .masteryIndex(masteryIndex)
                .confidenceIndex(confidenceIndex)
                .lastMetricUpdatedAt(lastMetricUpdatedAt)
                .achievableOverallByExam(achievableOverallByExam)
                .targetOverAmbitious(targetOverAmbitious)
                .targetWarning(targetWarning)
                .weakestTags(weakest)
                .strongestTags(strongest)
                .build();
    }

    private LearnerRoadmapInsightsResponse.TagMetricResponse toTagMetric(LearnerMetric metric) {
        Tag tag = metric.getTag();
        return LearnerRoadmapInsightsResponse.TagMetricResponse.builder()
                .tagId(tag != null ? tag.getId() : null)
                .tagName(tag != null ? tag.getName() : null)
                .tagCode(tag != null ? tag.getCode() : null)
                .tagType(tag != null && tag.getType() != null ? tag.getType().name() : null)
                .skill(metric.getSkill() != null ? metric.getSkill().name() : null)
                .masteryLevel(safe01(metric.getMasteryLevel(), 0.5))
                .confidenceScore(safe01(metric.getConfidenceScore(), 0.0))
                .attemptCount(metric.getAttemptCount() != null ? metric.getAttemptCount() : 0)
                .pGuess(metric.getPGuess())
                .pSlip(metric.getPSlip())
                .pTransit(metric.getPTransit())
                .updatedAt(metric.getUpdatedAt())
                .build();
    }

    // =====================================================================
    // CALIBRATION HELPERS
    // =====================================================================

    private record Calibration(
            double calibratedOverall,
            double calibratedReading,
            double calibratedListening,
            double calibratedWriting,
            double calibratedSpeaking,
            String note) {}

    private Calibration calibrateBands(
            PlacementResult placement,
            double masteryIndex,
            double confidenceIndex,
            double curReading,
            double curListening,
            double curWriting,
            double curSpeaking) {

        double estimatedOverall = estimateOverallFromMetrics(masteryIndex, confidenceIndex);

        boolean hasFirmAssessment = curReading > 4.0 || curListening > 4.0 || curWriting > 4.0 || curSpeaking > 4.0;

        if (placement == null || placement.getOverallBand() == null) {
            double calibratedOverall = hasFirmAssessment
                    ? clampBand((curReading + curListening + curWriting + curSpeaking) / 4.0)
                    : estimatedOverall;

            return new Calibration(
                    calibratedOverall,
                    curReading,
                    curListening,
                    curWriting,
                    curSpeaking,
                    hasFirmAssessment
                            ? "Band hiện tại được tính dựa trên kết quả luyện tập và bài thi gần nhất."
                            : "Chưa có placement, band đang ước tính từ quá trình luyện tập.");
        }

        boolean isSelfAssessed = Boolean.TRUE.equals(placement.getIsSelfAssessed());
        if (!isSelfAssessed && !hasFirmAssessment) {
            return new Calibration(
                    clampBand(placement.getOverallBand()),
                    clampBand(placement.getReadingBand()),
                    clampBand(placement.getListeningBand()),
                    clampBand(placement.getWritingBand()),
                    clampBand(placement.getSpeakingBand()),
                    "Band được lấy từ kết quả placement gần nhất.");
        }

        double baseOverall = hasFirmAssessment
                ? (curReading + curListening + curWriting + curSpeaking) / 4.0
                : placement.getOverallBand();

        double calibratedOverall = clampBand(baseOverall);

        if (isSelfAssessed && !hasFirmAssessment) {
            calibratedOverall = Math.min(calibratedOverall, clampBand(estimatedOverall + 0.5));
        }

        String note = hasFirmAssessment
                ? "Band đã được cập nhật dựa trên kết quả thi tuần/tháng gần nhất."
                : (calibratedOverall < placement.getOverallBand()
                        ? "Bạn đã tự đánh giá. Hệ thống đã hiệu chỉnh lại dựa trên dữ liệu thực tế."
                        : "Band tự đánh giá gần với dữ liệu luyện tập hiện tại.");

        return new Calibration(calibratedOverall, curReading, curListening, curWriting, curSpeaking, note);
    }

    private double estimateOverallFromMetrics(double masteryIndex, double confidenceIndex) {
        double masteryComponent = 3.5 + (safe01(masteryIndex, 0.5) * 4.0);
        double confidenceBoost = (safe01(confidenceIndex, 0.0) - 0.5) * 0.5;
        return clampBand(masteryComponent + confidenceBoost);
    }

    private double computeAchievableOverallByExam(double currentOverall, Integer daysToExam) {
        if (daysToExam == null) return clampBand(currentOverall + 0.5);
        double weeks = daysToExam / 7.0;
        double maxDelta = Math.min(2.0, weeks * 0.08);
        return clampBand(currentOverall + maxDelta);
    }

    private double computeAvg(List<Double> values, double fallback) {
        if (values == null || values.isEmpty()) return fallback;
        double sum = 0;
        int count = 0;
        for (Double v : values) {
            if (v == null) continue;
            sum += v;
            count++;
        }
        return count == 0 ? fallback : (sum / count);
    }

    private double safe01(Double value, double fallback) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double clampBand(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        double v = Math.round(value * 2.0) / 2.0;
        return Math.max(0.0, Math.min(9.0, v));
    }

    private Integer calculateRecommendedDailyStudyMinutes(Double currentBand, Double targetBand, Integer daysToExam) {
        if (currentBand == null || targetBand == null || currentBand >= targetBand) {
            return 0;
        }

        double bandGap = targetBand - currentBand;
        double totalRequiredHours = bandGap * 150.0;
        double totalRequiredMinutes = totalRequiredHours * 60.0;

        int effectiveDays = (daysToExam != null && daysToExam > 0) ? daysToExam : 90;
        int dailyMinutes = (int) Math.ceil(totalRequiredMinutes / effectiveDays);

        return Math.min(Math.max(dailyMinutes, 15), 240);
    }

    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String credentialId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId");
        }

        if (credentialId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (credentials.getUser() == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        return credentials.getUser();
    }
}
