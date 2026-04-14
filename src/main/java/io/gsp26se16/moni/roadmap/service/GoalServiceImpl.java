package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.placement.entity.PlacementResult;
import io.gsp26se16.moni.placement.repository.PlacementResultRepository;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.request.TaskStatusUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.dto.response.RoadmapDetailResponse;
import io.gsp26se16.moni.roadmap.dto.response.TaskResponse;
import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.entity.Roadmap;
import io.gsp26se16.moni.roadmap.entity.Task;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;
import io.gsp26se16.moni.roadmap.repository.GoalRepository;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.repository.RoadmapRepository;
import io.gsp26se16.moni.roadmap.repository.TaskRepository;
import io.gsp26se16.moni.roadmap.repository.WeeklyPlanRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final RoadmapRepository roadmapRepository;
    private final TaskRepository taskRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;
    private final PlacementResultRepository placementResultRepository;
    private final TestRepository testRepository;
    private final WeeklyPlanService weeklyPlanService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final io.gsp26se16.moni.roadmap.repository.InsightSnapshotRepository insightSnapshotRepository;

    @Override
    @Transactional
    public GoalCreateResponse createGoal(GoalCreateRequest request) {
        Users learner = getCurrentUser();

        if (request.getTargetBand() <= request.getStartingBand()) {
            throw new AppException(ErrorCode.INVALID_IELTS_BAND);
        }

        goalRepository
                .findTopByUserAndSkillAndStatusOrderByIdDesc(learner, request.getSkill(), "ACTIVE")
                .ifPresent(oldGoal -> {
                    oldGoal.setStatus("ARCHIVED");
                    goalRepository.save(oldGoal);
                });

        Goal newGoal = new Goal();
        newGoal.setUser(learner);
        newGoal.setSkill(request.getSkill());
        newGoal.setStartingBand(request.getStartingBand());
        newGoal.setTargetBand(request.getTargetBand());
        newGoal.setDeadline(request.getDeadline());
        newGoal.setStatus("ACTIVE");
        Goal savedGoal = goalRepository.save(newGoal);

        Roadmap roadmap = new Roadmap();
        roadmap.setGoal(savedGoal);
        roadmap.setVersion(1);
        roadmap.setStatus("ACTIVE");
        roadmap.setPriority(1);
        roadmap.setCreatedAt(LocalDateTime.now());
        Roadmap savedRoadmap = roadmapRepository.save(roadmap);

        Task placementTask = new Task();
        placementTask.setRoadmap(savedRoadmap);
        placementTask.setOrder(1);
        placementTask.setTaskType("PLACEMENT_TEST");
        placementTask.setStatus("TODO");
        taskRepository.save(placementTask);

        generateSmartTasksForRoadmap(savedRoadmap, learner);

        return GoalCreateResponse.builder()
                .goalId(savedGoal.getId())
                .skill(savedGoal.getSkill())
                .startingBand(savedGoal.getStartingBand())
                .targetBand(savedGoal.getTargetBand())
                .deadline(savedGoal.getDeadline())
                .status(savedGoal.getStatus())
                .roadmapId(savedRoadmap.getId())
                .roadmapVersion(savedRoadmap.getVersion())
                .message("Đã thiết lập mục tiêu " + request.getSkill() + " và sinh Lộ trình thành công!")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoalResponse> getActiveGoals() {
        Users learner = getCurrentUser();
        List<Goal> activeGoals = goalRepository.findAllByUserAndStatus(learner, "ACTIVE");

        return activeGoals.stream()
                .map(goal -> {
                    Roadmap activeRoadmap = roadmapRepository
                            .findByGoalAndStatus(goal, "ACTIVE")
                            .orElse(null);

                    return GoalResponse.builder()
                            .goalId(goal.getId())
                            .skill(goal.getSkill())
                            .startingBand(goal.getStartingBand())
                            .targetBand(goal.getTargetBand())
                            .deadline(goal.getDeadline())
                            .status(goal.getStatus())
                            .activeRoadmapId(activeRoadmap != null ? activeRoadmap.getId() : null)
                            .activeRoadmapVersion(activeRoadmap != null ? activeRoadmap.getVersion() : null)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public GoalCreateResponse updateGoal(Integer goalId, GoalUpdateRequest request) {
        Users learner = getCurrentUser();

        Goal goal = goalRepository.findById(goalId).orElseThrow(() -> new AppException(ErrorCode.GOAL_NOT_FOUND));

        if (!goal.getUser().getId().equals(learner.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(goal.getStatus())) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        if (request.getTargetBand() <= goal.getStartingBand()) {
            throw new AppException(ErrorCode.INVALID_IELTS_BAND);
        }

        goal.setTargetBand(request.getTargetBand());
        goal.setDeadline(request.getDeadline());
        Goal savedGoal = goalRepository.save(goal);

        Roadmap oldRoadmap = roadmapRepository
                .findByGoalAndStatus(savedGoal, "ACTIVE")
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVE_ROADMAP_NOT_FOUND));
        oldRoadmap.setStatus("ARCHIVED");
        roadmapRepository.save(oldRoadmap);

        Roadmap newRoadmap = new Roadmap();
        newRoadmap.setGoal(savedGoal);
        newRoadmap.setVersion(oldRoadmap.getVersion() + 1);
        newRoadmap.setStatus("ACTIVE");
        newRoadmap.setPriority(1);
        newRoadmap.setCreatedAt(LocalDateTime.now());
        Roadmap savedRoadmap = roadmapRepository.save(newRoadmap);

        generateSmartTasksForRoadmap(savedRoadmap, learner);

        return buildResponse(
                savedGoal,
                savedRoadmap,
                "Đã cập nhật Mục tiêu và sinh Lộ trình version " + savedRoadmap.getVersion() + " thành công!");
    }

    @Override
    @Transactional
    public void updateTaskStatus(Integer taskId, TaskStatusUpdateRequest request) {
        Users learner = getCurrentUser();

        Task task = taskRepository.findById(taskId).orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));

        if (!task.getRoadmap().getGoal().getUser().getId().equals(learner.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        task.setStatus(request.getStatus().toUpperCase());
        taskRepository.save(task);

        if ("DONE".equals(task.getStatus())) {
            Roadmap currentRoadmap = task.getRoadmap();

            if ("PRACTICE_STIMULUS".equals(task.getTaskType())) {
                List<Task> practiceTasksInRoadmap =
                        taskRepository.findAllByRoadmapAndTaskType(currentRoadmap, "PRACTICE_STIMULUS");
                boolean allPracticeDone = practiceTasksInRoadmap.stream().allMatch(t -> "DONE".equals(t.getStatus()));

                if (allPracticeDone) {
                    log.info(
                            "Tất cả PRACTICE_STIMULUS đã DONE → Khởi tạo đề và Mở khóa MINI_TEST trong roadmap {}",
                            currentRoadmap.getId());

                    List<Task> lockedMiniTests =
                            taskRepository.findAllByRoadmapAndTaskType(currentRoadmap, "MINI_TEST");

                    lockedMiniTests.forEach(miniTest -> {
                        if ("LOCKED".equals(miniTest.getStatus())) {

                            generateAndAssignPersonalizedMiniTest(miniTest, currentRoadmap, learner);
                        }
                    });
                }
            }

            long remainingTasks = taskRepository.countByRoadmapIdAndStatusNot(currentRoadmap.getId(), "DONE");

            if (remainingTasks > 0) {
                generateNextRoadmapWhenNearing100Percent(currentRoadmap);
            }

            // If 100% done → activate queued roadmap or create new
            if (remainingTasks == 0) {
                currentRoadmap.setStatus("ARCHIVED");
                roadmapRepository.save(currentRoadmap);

                // [NEW] Check for QUEUED roadmap first (from predictive generation)
                Roadmap queuedRoadmap = roadmapRepository
                        .findByGoalAndStatus(currentRoadmap.getGoal(), "QUEUED")
                        .orElse(null);

                if (queuedRoadmap != null) {
                    // Activate already-prepared roadmap
                    queuedRoadmap.setStatus("ACTIVE");
                    roadmapRepository.save(queuedRoadmap);
                    log.info("[Predictive Roadmap] Roadmap v{} activated (was QUEUED)", queuedRoadmap.getVersion());
                } else {
                    // Fallback: create new roadmap if not already prepared
                    Roadmap newRoadmap = new Roadmap();
                    newRoadmap.setGoal(currentRoadmap.getGoal());
                    newRoadmap.setVersion(currentRoadmap.getVersion() + 1);
                    newRoadmap.setStatus("ACTIVE");
                    newRoadmap.setPriority(1);
                    newRoadmap.setCreatedAt(LocalDateTime.now());
                    Roadmap savedNewRoadmap = roadmapRepository.save(newRoadmap);

                    generateSmartTasksForRoadmap(savedNewRoadmap, learner);
                    log.info(
                            "[Roadmap] Fallback: created Roadmap v{} (QUEUED was not found)",
                            savedNewRoadmap.getVersion());
                }
            }
        }
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

            // Tính targetBand hợp lệ
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
            Goal savedGoal = goalRepository.save(newGoal);

            // Tạo Roadmap V1
            Roadmap roadmap = new Roadmap();
            roadmap.setGoal(savedGoal);
            roadmap.setVersion(1);
            roadmap.setStatus("ACTIVE");
            roadmap.setPriority(1);
            roadmap.setCreatedAt(LocalDateTime.now());
            Roadmap savedRoadmap = roadmapRepository.save(roadmap);

            generateSmartTasksForRoadmap(savedRoadmap, user);

            log.info(
                    "Created Goal+Roadmap for skill {} (starting={}, target={}, deadline={})",
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
    public List<RoadmapDetailResponse> getRoadmapDetails() {
        Users learner = getCurrentUser();
        List<Goal> activeGoals = goalRepository.findAllByUserAndStatus(learner, "ACTIVE");

        return activeGoals.stream()
                .map(goal -> {
                    Roadmap activeRoadmap = roadmapRepository
                            .findByGoalAndStatus(goal, "ACTIVE")
                            .orElse(null);

                    List<TaskResponse> taskResponses = new ArrayList<>();
                    double progress = 0.0;

                    if (activeRoadmap != null) {
                        List<Task> tasks = taskRepository.findAllByRoadmapOrderByOrderAsc(activeRoadmap);
                        int totalTasks = tasks.size();
                        long doneTasks = tasks.stream()
                                .filter(t -> "DONE".equals(t.getStatus()))
                                .count();

                        progress = totalTasks > 0 ? (double) doneTasks / totalTasks * 100.0 : 0.0;

                        taskResponses = tasks.stream()
                                .map(task -> {
                                    Stimulus stimulus = task.getStimulus();
                                    Integer stimulusId = null;
                                    String stimulusTitle = null;
                                    Integer questionCount = null;

                                    if (stimulus != null) {
                                        stimulusId = stimulus.getId();
                                        stimulusTitle = stimulus.getTitle();
                                        questionCount = stimulus.getQuestionGroups().stream()
                                                .mapToInt(
                                                        qg -> qg.getQuestions().size())
                                                .sum();
                                    }

                                    Integer testId = task.getTest() != null
                                            ? task.getTest().getId()
                                            : null;
                                    if (testId == null && stimulusId != null) {
                                        List<TestStructure> structures =
                                                testStructureRepository.findByStimulusId(stimulusId);
                                        if (!structures.isEmpty()) {
                                            testId = structures.get(0).getTest().getId();
                                        }
                                    }

                                    return TaskResponse.builder()
                                            .id(task.getId())
                                            .order(task.getOrder())
                                            .taskType(task.getTaskType())
                                            .status(task.getStatus())
                                            .testId(testId)
                                            .stimulusId(stimulusId)
                                            .stimulusTitle(stimulusTitle)
                                            .questionCount(questionCount)
                                            .build();
                                })
                                .toList();
                    }

                    return RoadmapDetailResponse.builder()
                            .goalId(goal.getId())
                            .skill(goal.getSkill().name())
                            .startingBand(goal.getStartingBand())
                            .targetBand(goal.getTargetBand())
                            .deadline(
                                    goal.getDeadline() != null
                                            ? goal.getDeadline().toString()
                                            : null)
                            .roadmapId(activeRoadmap != null ? activeRoadmap.getId() : null)
                            .roadmapVersion(activeRoadmap != null ? activeRoadmap.getVersion() : null)
                            .tasks(taskResponses)
                            .progress(progress)
                            .build();
                })
                .toList();
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

    private LearnerRoadmapInsightsResponse generateInsightsForUser(Users learner) {

        PlacementResult placement = placementResultRepository
                .findFirstByUserOrderByCompletedAtDesc(learner)
                .orElse(null);

        List<LearnerMetric> allMetrics = learnerMetricRepository.findByUserOrderByUpdatedAtDesc(learner);
        double masteryIndex = computeAvg(
                allMetrics.stream().map(LearnerMetric::getMasteryLevel).toList(), 0.5);
        double confidenceIndex = computeAvg(
                allMetrics.stream().map(LearnerMetric::getConfidenceScore).toList(), 0.0);
        var lastMetricUpdatedAt =
                allMetrics.isEmpty() ? null : allMetrics.get(0).getUpdatedAt();

        LocalDate examDate = learner.getExamDate();
        Integer daysToExam =
                examDate != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate) : null;
        if (daysToExam != null && daysToExam < 0) daysToExam = 0;

        // [NEW] Get current bands from WeeklyPlanService (priority: Monthly > Weekly > Placement > Goal)
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
        // ============================================================
        // [NEW] LOGIC TÍNH TOÁN THỜI GIAN VÀ CẢNH BÁO AI
        // ============================================================
        Integer dailyStudyTime =
                calculateRecommendedDailyStudyMinutes(calibration.calibratedOverall, targetOverall, daysToExam);

        // 1. Kiểm tra quá sức về mặt ĐIỂM SỐ (Logic cũ)
        boolean isScoreOverAmbitious = targetOverall != null
                && targetOverall > 0
                && daysToExam != null
                && targetOverall > (achievableOverallByExam + 0.25);

        // 2. Kiểm tra quá sức về mặt THỜI GIAN (Logic mới)
        boolean isTimeOverAmbitious = false;
        if (targetOverall != null
                && calibration.calibratedOverall > 0
                && targetOverall > calibration.calibratedOverall) {
            double gap = targetOverall - calibration.calibratedOverall;
            int effectiveDaysForMath = (daysToExam != null && daysToExam > 0) ? daysToExam : 90;
            int rawDailyMinutes = (int) Math.ceil((gap * 150.0 * 60.0) / effectiveDaysForMath);

            // Nếu thực tế phải cày hơn 4 tiếng/ngày -> Báo động
            if (rawDailyMinutes > 240) {
                isTimeOverAmbitious = true;
            }
        }

        // Cập nhật trạng thái OverAmbitious tổng hợp
        boolean targetOverAmbitious = isScoreOverAmbitious || isTimeOverAmbitious;
        String targetWarning = null;

        if (isTimeOverAmbitious) {
            targetWarning =
                    "Mục tiêu của bạn đòi hỏi học hơn 4 tiếng mỗi ngày. Điều này rất dễ gây quá tải (Burnout). Hệ thống khuyên bạn nên dời ngày thi lại, hoặc tạm thời hạ mục tiêu xuống 0.5 Band.";
        } else if (isScoreOverAmbitious) {
            targetWarning =
                    "Mục tiêu hiện tại có thể hơi quá tầm so với thời gian còn lại. Hãy tăng cường tần suất luyện tập hoặc điều chỉnh lại kỳ vọng.";
        }
        // ============================================================

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> weakest =
                learnerMetricRepository.findTop8ByUserOrderByMasteryLevelAsc(learner).stream()
                        .map(this::toTagMetric)
                        .toList();

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> strongest =
                learnerMetricRepository.findTop5ByUserOrderByMasteryLevelDesc(learner).stream()
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
                .masteryLevel(safe01(metric.getMasteryLevel(), 0.5))
                .confidenceScore(safe01(metric.getConfidenceScore(), 0.0))
                .attemptCount(metric.getAttemptCount() != null ? metric.getAttemptCount() : 0)
                .pGuess(metric.getPGuess())
                .pSlip(metric.getPSlip())
                .pTransit(metric.getPTransit())
                .updatedAt(metric.getUpdatedAt())
                .build();
    }

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

        // If we have firm assessment data (higher than fallback 4.0), we trust it more
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

        // Calibration logic: If self-assessed OR we have new assessment data, we blend it
        double baseOverall = hasFirmAssessment
                ? (curReading + curListening + curWriting + curSpeaking) / 4.0
                : placement.getOverallBand();

        double calibratedOverall = clampBand(baseOverall);

        // If self-assessed, still do a sanity check against metrics
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

    private double nonNullOr(Double value, double fallback) {
        return value != null ? value : fallback;
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

    private void generateSmartTasksForRoadmap(Roadmap roadmap, Users learner) {
        Skill currentSkill = roadmap.getGoal().getSkill();
        List<Stimulus> selectedStimuli = new ArrayList<>();

        List<LearnerMetric> weakMetrics = learnerMetricRepository.findByUser(learner).stream()
                .sorted(Comparator.comparingDouble(this::calculateWeakAreaScore))
                .limit(5)
                .collect(Collectors.toList());

        int dynamicTaskCount = calculateOptimalTaskCount(learner, roadmap.getGoal());

        if (!weakMetrics.isEmpty()) {
            List<Tag> weakTags = weakMetrics.stream().map(LearnerMetric::getTag).toList();
            List<Stimulus> smartStimuli = stimulusRepository.findSmartStimuli(currentSkill, weakTags);

            double optimalDifficulty = calculateOptimalTaskDifficulty(weakMetrics);
            selectedStimuli = smartStimuli.stream()
                    .sorted(Comparator.comparingDouble(
                            s -> Math.abs(estimateStimulusDifficulty(s) - optimalDifficulty)))
                    .limit(dynamicTaskCount)
                    .collect(Collectors.toList());

            log.debug(
                    "[Difficulty Alignment] optimal={}, selected={} stimuli, expected count={}",
                    optimalDifficulty,
                    selectedStimuli.size(),
                    dynamicTaskCount);
        }

        if (selectedStimuli.isEmpty()) {
            List<Stimulus> fallbackStimuli = stimulusRepository.findBySkill(currentSkill);
            Collections.shuffle(fallbackStimuli);
            selectedStimuli = fallbackStimuli.stream().limit(dynamicTaskCount).toList();

            if (selectedStimuli.isEmpty()) {
                log.warn("Không có bài tập cho kỹ năng {} — bỏ qua sinh task", currentSkill);
                return;
            }
        }

        int order = 1;
        for (Stimulus stimulus : selectedStimuli) {
            Task practiceTask = new Task();
            practiceTask.setRoadmap(roadmap);
            practiceTask.setStimulus(stimulus);
            practiceTask.setOrder(order++);
            practiceTask.setTaskType("PRACTICE_STIMULUS");
            practiceTask.setStatus("TODO");
            taskRepository.save(practiceTask);
        }

        createLockedMiniTestPlaceholder(roadmap, order);

        log.info(
                "[generateSmartTasks] roadmap={}, skill={}, tasks={}",
                roadmap.getId(),
                currentSkill,
                selectedStimuli.size() + 1);
    }

    private Double calculateOptimalTaskDifficulty(List<LearnerMetric> weakMetrics) {
        double avgMastery = weakMetrics.stream()
                .mapToDouble(LearnerMetric::getMasteryLevel)
                .average()
                .orElse(0.5);

        double optimalDifficulty = avgMastery + 0.15;
        return Math.min(0.95, optimalDifficulty);
    }

    private double estimateStimulusDifficulty(Stimulus stimulus) {
        if (stimulus.getQuestionGroups() == null || stimulus.getQuestionGroups().isEmpty()) {
            return 0.5;
        }

        return stimulus.getQuestionGroups().stream()
                .flatMap(qg -> qg.getQuestions().stream())
                .mapToDouble(q -> {
                    if (q.getTags() == null || q.getTags().isEmpty()) return 0.5;

                    return q.getTags().stream()
                            .mapToDouble(tag -> {
                                String name = tag.getName().toUpperCase();
                                if (name.contains("8") || name.contains("BAND_8")) return 0.8;
                                if (name.contains("7") || name.contains("BAND_7")) return 0.7;
                                if (name.contains("6") || name.contains("BAND_6")) return 0.6;
                                if (name.contains("5") || name.contains("BAND_5")) return 0.5;
                                return 0.5;
                            })
                            .average()
                            .orElse(0.5);
                })
                .average()
                .orElse(0.5);
    }

    private Double calculateWeakAreaScore(LearnerMetric metric) {
        double mastery = metric.getMasteryLevel();
        double confidence = metric.getConfidenceScore();
        double uncertainty = 1.0 - confidence;

        return mastery + (uncertainty * 0.5);
    }

    public void generateNextRoadmapWhenNearing100Percent(Roadmap currentRoadmap) {

        boolean hasQueuedRoadmap = roadmapRepository
                .findByGoalAndStatus(currentRoadmap.getGoal(), "QUEUED")
                .isPresent();

        if (hasQueuedRoadmap) {
            return;
        }

        long totalTasks = taskRepository.countByRoadmapId(currentRoadmap.getId());
        if (totalTasks == 0) return;

        long doneTasks = taskRepository.countByRoadmapIdAndStatus(currentRoadmap.getId(), "DONE");
        double progressPercent = (double) doneTasks / totalTasks;

        if (progressPercent >= 0.8) {
            Roadmap nextRoadmap = new Roadmap();
            nextRoadmap.setGoal(currentRoadmap.getGoal());
            nextRoadmap.setVersion(currentRoadmap.getVersion() + 1);
            nextRoadmap.setStatus("QUEUED");
            nextRoadmap.setCreatedAt(LocalDateTime.now());
            Roadmap savedNextRoadmap = roadmapRepository.save(nextRoadmap);

            generateSmartTasksForRoadmap(
                    savedNextRoadmap, currentRoadmap.getGoal().getUser());

            log.info(
                    "[Predictive Roadmap] Đã chuẩn bị sẵn Roadmap v{} (Tiến độ v{} đạt {}%)",
                    savedNextRoadmap.getVersion(), currentRoadmap.getVersion(), (int) (progressPercent * 100));
        }
    }

    private GoalCreateResponse buildResponse(Goal goal, Roadmap roadmap, String message) {
        return GoalCreateResponse.builder()
                .goalId(goal.getId())
                .skill(goal.getSkill())
                .startingBand(goal.getStartingBand())
                .targetBand(goal.getTargetBand())
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .roadmapId(roadmap.getId())
                .roadmapVersion(roadmap.getVersion())
                .message(message)
                .build();
    }

    private void generateAndAssignPersonalizedMiniTest(Task miniTestTask, Roadmap roadmap, Users learner) {
        Skill skill = roadmap.getGoal().getSkill();

        log.info("Bắt đầu sinh MINI_TEST cá nhân hóa cho User {}, Kỹ năng {}", learner.getId(), skill);

        List<Tag> weakTags = learnerMetricRepository.findByUser(learner).stream()
                .sorted(Comparator.comparingDouble(this::calculateWeakAreaScore))
                .map(LearnerMetric::getTag)
                .limit(3)
                .toList();

        List<Stimulus> selectedStimuli = new ArrayList<>();
        if (!weakTags.isEmpty()) {
            selectedStimuli = stimulusRepository.findSmartStimuli(skill, weakTags).stream()
                    .limit(2)
                    .toList();
        }

        if (selectedStimuli.isEmpty()) {
            List<Stimulus> fallbackStimuli = stimulusRepository.findBySkill(skill);
            Collections.shuffle(fallbackStimuli);
            selectedStimuli = fallbackStimuli.stream().limit(2).toList();
        }

        if (selectedStimuli.isEmpty()) {
            log.warn("Không có Stimulus nào trong DB để tạo Mini Test cho skill {}", skill);
            // Vẫn mở khóa task nhưng để trống bài test
            miniTestTask.setStatus("TODO");
            taskRepository.save(miniTestTask);
            return;
        }

        Test dynamicTest = new Test();
        dynamicTest.setTitle("Mini Test: Khắc phục điểm yếu - " + LocalDate.now());
        dynamicTest.setDescription("Bài kiểm tra được AI tự động tổng hợp dựa trên lộ trình học tập của bạn.");
        dynamicTest.setSkill(skill);
        dynamicTest.setTestMode(TestMode.PRACTICE);
        dynamicTest.setStatus(PublishStatus.PUBLISHED);
        dynamicTest.setDuration(30);
        Test savedTest = testRepository.save(dynamicTest);

        int sectionOrder = 1;
        for (Stimulus stimulus : selectedStimuli) {
            TestStructure structure = new TestStructure();
            structure.setTest(savedTest);
            structure.setStimulus(stimulus);
            structure.setSection(sectionOrder++);
            testStructureRepository.save(structure);
        }

        miniTestTask.setTest(savedTest);
        miniTestTask.setStatus("TODO");
        taskRepository.save(miniTestTask);

        log.info("Sinh MINI_TEST thành công! Task ID: {}, Test ID mới: {}", miniTestTask.getId(), savedTest.getId());
    }

    private void createLockedMiniTestPlaceholder(Roadmap roadmap, int order) {
        Task miniTest = new Task();
        miniTest.setRoadmap(roadmap);
        miniTest.setOrder(order);
        miniTest.setTaskType("MINI_TEST");
        miniTest.setStatus("LOCKED");
        taskRepository.save(miniTest);
        log.info("[Mini-Test Placeholder] Đã tạo task khóa chờ sẵn ở order {}", order);
    }

    private int calculateOptimalTaskCount(Users learner, Goal goal) {
        int baseCount = 2;

        LocalDate examDate = learner.getExamDate();
        if (examDate != null) {
            long daysToExam = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate);
            if (daysToExam > 0 && daysToExam <= 30) {
                baseCount += 1;
                log.debug(
                        "User {} sắp thi ({} ngày), tăng số lượng task lên {}", learner.getId(), daysToExam, baseCount);
            }
        }

        if (goal.getTargetBand() != null && goal.getStartingBand() != null) {
            double bandGap = goal.getTargetBand() - goal.getStartingBand();
            if (bandGap >= 1.5) {
                baseCount += 1;
                log.debug("Khoảng cách band điểm lớn ({}), tăng số lượng task lên {}", bandGap, baseCount);
            }
        }

        return Math.min(Math.max(baseCount, 2), 4);
    }

    private Integer calculateRecommendedDailyStudyMinutes(Double currentBand, Double targetBand, Integer daysToExam) {
        if (currentBand == null || targetBand == null || currentBand >= targetBand) {
            return 0; // Đã đạt mục tiêu hoặc không có target
        }

        double bandGap = targetBand - currentBand;

        // Chuẩn Cambridge: 150 giờ học cho mỗi 1.0 Band
        double totalRequiredHours = bandGap * 150.0;
        double totalRequiredMinutes = totalRequiredHours * 60.0;

        // Nếu user không nhập ngày thi, giả định là 90 ngày (3 tháng) để chia trung bình
        int effectiveDays = (daysToExam != null && daysToExam > 0) ? daysToExam : 90;

        int dailyMinutes = (int) Math.ceil(totalRequiredMinutes / effectiveDays);

        // Chốt chặn an toàn: Tối thiểu 15 phút, Tối đa 240 phút (4 tiếng) để tránh Burnout
        return Math.min(Math.max(dailyMinutes, 15), 240);
    }
}
