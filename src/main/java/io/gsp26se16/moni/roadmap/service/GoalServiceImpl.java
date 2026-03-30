package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.StimulusRepository;
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
import io.gsp26se16.moni.roadmap.repository.GoalRepository;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.repository.RoadmapRepository;
import io.gsp26se16.moni.roadmap.repository.TaskRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
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

    @Override
    @Transactional
    public GoalCreateResponse createGoal(GoalCreateRequest request) {
        Users learner = getCurrentUser();

        if (request.getTargetBand() <= request.getStartingBand()) {
            throw new AppException(ErrorCode.INVALID_IELTS_BAND);
        }

        // 1. LƯU TRỮ GOAL CŨ (Cùng kỹ năng)
        goalRepository
                .findTopByUserAndSkillAndStatusOrderByIdDesc(learner, request.getSkill(), "ACTIVE")
                .ifPresent(oldGoal -> {
                    oldGoal.setStatus("ARCHIVED");
                    goalRepository.save(oldGoal);
                });

        // 2. TẠO GOAL MỚI
        Goal newGoal = new Goal();
        newGoal.setUser(learner);
        newGoal.setSkill(request.getSkill());
        newGoal.setStartingBand(request.getStartingBand());
        newGoal.setTargetBand(request.getTargetBand());
        newGoal.setDeadline(request.getDeadline());
        newGoal.setStatus("ACTIVE");
        Goal savedGoal = goalRepository.save(newGoal);

        // 3. TỰ ĐỘNG SINH ROADMAP V1.0 CHO KỸ NĂNG NÀY
        Roadmap roadmap = new Roadmap();
        roadmap.setGoal(savedGoal);
        roadmap.setVersion(1);
        roadmap.setStatus("ACTIVE");
        roadmap.setPriority(1);
        roadmap.setCreatedAt(LocalDateTime.now());
        Roadmap savedRoadmap = roadmapRepository.save(roadmap);

        // 4. SINH TASK ĐÁNH GIÁ NĂNG LỰC (Placement Task)
        Task placementTask = new Task();
        placementTask.setRoadmap(savedRoadmap);
        placementTask.setOrder(1);
        placementTask.setTaskType("PLACEMENT_TEST");
        placementTask.setStatus("TODO");
        taskRepository.save(placementTask);

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

            // Phase 02 Fix: Nếu là PRACTICE_STIMULUS DONE → kiểm tra unlock MINI_TEST
            if ("PRACTICE_STIMULUS".equals(task.getTaskType())) {
                List<Task> practiceTasksInRoadmap =
                        taskRepository.findAllByRoadmapAndTaskType(currentRoadmap, "PRACTICE_STIMULUS");
                boolean allPracticeDone = practiceTasksInRoadmap.stream().allMatch(t -> "DONE".equals(t.getStatus()));

                if (allPracticeDone) {
                    log.info(
                            "Tất cả PRACTICE_STIMULUS đã DONE → Mở khóa MINI_TEST trong roadmap {}",
                            currentRoadmap.getId());
                    List<Task> lockedMiniTests =
                            taskRepository.findAllByRoadmapAndTaskType(currentRoadmap, "MINI_TEST");
                    lockedMiniTests.forEach(miniTest -> {
                        if ("LOCKED".equals(miniTest.getStatus())) {
                            miniTest.setStatus("TODO");
                            taskRepository.save(miniTest);
                        }
                    });
                }
            }

            // Kịch bản tự động sinh roadmap mới khi hết tất cả bài
            long remainingTasks = taskRepository.countByRoadmapIdAndStatusNot(currentRoadmap.getId(), "DONE");

            if (remainingTasks == 0) {
                currentRoadmap.setStatus("ARCHIVED");
                roadmapRepository.save(currentRoadmap);

                Roadmap newRoadmap = new Roadmap();
                newRoadmap.setGoal(currentRoadmap.getGoal());
                newRoadmap.setVersion(currentRoadmap.getVersion() + 1);
                newRoadmap.setStatus("ACTIVE");
                newRoadmap.setPriority(1);
                newRoadmap.setCreatedAt(LocalDateTime.now());
                Roadmap savedNewRoadmap = roadmapRepository.save(newRoadmap);

                generateSmartTasksForRoadmap(savedNewRoadmap, learner);
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

            // Archive goal cũ cùng skill (nếu có)
            final Skill currentSkill = skill;
            goalRepository
                    .findTopByUserAndSkillAndStatusOrderByIdDesc(user, currentSkill, "ACTIVE")
                    .ifPresent(oldGoal -> {
                        oldGoal.setStatus("ARCHIVED");
                        goalRepository.save(oldGoal);
                        log.info("Archived old goal {} for skill {}", oldGoal.getId(), currentSkill);
                    });

            // Tính deadline
            LocalDate deadline = (examDate != null) ? examDate : LocalDate.now().plusDays(90);

            // Tạo Goal mới
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

            // Sinh smart tasks (không tạo PLACEMENT_TEST vì đã làm rồi)
            generateSmartTasksForRoadmap(savedRoadmap, user);

            log.info(
                    "Created Goal+Roadmap for skill {} (starting={}, target={}, deadline={})",
                    skill,
                    startingBand,
                    targetBand,
                    deadline);
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
                                        // Count all questions across all question groups
                                        questionCount = stimulus.getQuestionGroups().stream()
                                                .mapToInt(
                                                        qg -> qg.getQuestions().size())
                                                .sum();
                                    }

                                    Integer testId = task.getTest() != null
                                            ? task.getTest().getId()
                                            : null;
                                    // Lookup testId from TestStructure if not set directly
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

        PlacementResult placement = placementResultRepository
                .findFirstByUserOrderByCompletedAtDesc(learner)
                .orElse(null);

        List<LearnerMetric> allMetrics = learnerMetricRepository.findByUserOrderByUpdatedAtDesc(learner);
        double masteryIndex = computeAvg(allMetrics.stream().map(LearnerMetric::getMasteryLevel).toList(), 0.5);
        double confidenceIndex = computeAvg(allMetrics.stream().map(LearnerMetric::getConfidenceScore).toList(), 0.0);
        var lastMetricUpdatedAt = allMetrics.isEmpty() ? null : allMetrics.get(0).getUpdatedAt();

        LocalDate examDate = learner.getExamDate();
        Integer daysToExam = examDate != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate) : null;
        if (daysToExam != null && daysToExam < 0) daysToExam = 0;

        Calibration calibration = calibrateBands(placement, masteryIndex, confidenceIndex);
        double achievableOverallByExam = computeAchievableOverallByExam(calibration.calibratedOverall, daysToExam);

        Double targetOverall = learner.getTargetBand();
        boolean targetOverAmbitious = targetOverall != null
                && targetOverall > 0
                && daysToExam != null
                && targetOverall > (achievableOverallByExam + 0.25);

        String targetWarning = null;
        if (targetOverAmbitious) {
            targetWarning =
                    "Muc tieu hien tai co the hoi qua tam so voi thoi gian con lai. Hay can nhac giam muc tieu hoac tang cuong tan suat luyen tap.";
        }

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> weakest = learnerMetricRepository
                .findTop8ByUserOrderByMasteryLevelAsc(learner)
                .stream()
                .map(this::toTagMetric)
                .toList();

        List<LearnerRoadmapInsightsResponse.TagMetricResponse> strongest = learnerMetricRepository
                .findTop5ByUserOrderByMasteryLevelDesc(learner)
                .stream()
                .map(this::toTagMetric)
                .toList();

        return LearnerRoadmapInsightsResponse.builder()
                .examDate(examDate)
                .daysToExam(daysToExam)
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

    private Calibration calibrateBands(PlacementResult placement, double masteryIndex, double confidenceIndex) {
        double estimatedOverall = estimateOverallFromMetrics(masteryIndex, confidenceIndex);

        if (placement == null || placement.getOverallBand() == null) {
            return new Calibration(estimatedOverall, 0, 0, 0, 0, "Chua co placement, band dang uoc tinh tu qua trinh luyen tap.");
        }

        boolean isSelfAssessed = Boolean.TRUE.equals(placement.getIsSelfAssessed());
        if (!isSelfAssessed) {
            return new Calibration(
                    clampBand(placement.getOverallBand()),
                    clampBand(placement.getReadingBand()),
                    clampBand(placement.getListeningBand()),
                    clampBand(placement.getWritingBand()),
                    clampBand(placement.getSpeakingBand()),
                    "Band duoc lay tu ket qua placement gan nhat.");
        }

        double placementOverall = clampBand(placement.getOverallBand());
        double calibratedOverall = Math.min(placementOverall, clampBand(estimatedOverall + 0.5));
        String note =
                calibratedOverall < placementOverall
                        ? "Ban da tu danh gia. He thong se hieu chinh dan theo ket qua luyen tap de phan anh dung thuc luc."
                        : "Band tu danh gia gan voi du lieu luyen tap hien tai.";

        return new Calibration(
                calibratedOverall,
                clampBand(Math.min(nonNullOr(placement.getReadingBand(), calibratedOverall), calibratedOverall + 1.0)),
                clampBand(Math.min(nonNullOr(placement.getListeningBand(), calibratedOverall), calibratedOverall + 1.0)),
                clampBand(Math.min(nonNullOr(placement.getWritingBand(), calibratedOverall), calibratedOverall + 1.0)),
                clampBand(Math.min(nonNullOr(placement.getSpeakingBand(), calibratedOverall), calibratedOverall + 1.0)),
                note);
    }

    private double estimateOverallFromMetrics(double masteryIndex, double confidenceIndex) {
        // conservative mapping: 0.5 mastery ~= 5.0-5.5 band
        double masteryComponent = 3.5 + (safe01(masteryIndex, 0.5) * 4.0); // ~3.5..7.5
        double confidenceBoost = (safe01(confidenceIndex, 0.0) - 0.5) * 0.5; // -0.25..+0.25
        return clampBand(masteryComponent + confidenceBoost);
    }

    private double computeAchievableOverallByExam(double currentOverall, Integer daysToExam) {
        if (daysToExam == null) return clampBand(currentOverall + 0.5);
        double weeks = daysToExam / 7.0;
        double maxDelta = Math.min(2.0, weeks * 0.08); // conservative ceiling
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

    // --- Helper lấy User từ JWT Token ---
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

        // 1. BẮT MẠCH: Đọc hồ sơ năng lực của Học viên
        List<LearnerMetric> weakMetrics = learnerMetricRepository.findTop3ByUserOrderByMasteryLevelAsc(learner);

        if (!weakMetrics.isEmpty()) {
            List<Tag> weakTags = weakMetrics.stream().map(LearnerMetric::getTag).toList();
            List<Stimulus> smartStimuli = stimulusRepository.findSmartStimuli(currentSkill, weakTags);
            Collections.shuffle(smartStimuli);
            selectedStimuli = smartStimuli.stream().limit(2).toList();
        }

        // 3. FALLBACK: Cold Start hoặc DB không có bài khớp Tag
        if (selectedStimuli.isEmpty()) {
            List<Stimulus> fallbackStimuli = stimulusRepository.findBySkill(currentSkill);
            Collections.shuffle(fallbackStimuli);
            selectedStimuli = fallbackStimuli.stream().limit(2).toList();

            if (selectedStimuli.isEmpty()) {
                log.warn("Không có bài tập cho kỹ năng {} — bỏ qua sinh task", currentSkill);
                return;
            }
        }

        // 4. GIAO VIỆC: Tạo PRACTICE_STIMULUS tasks
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

        // 5. CHỐT CHẶN: MINI_TEST cuối lộ trình (LOCKED cho đến khi xong hết practice)
        Task checkPointTask = new Task();
        checkPointTask.setRoadmap(roadmap);
        checkPointTask.setOrder(order);
        checkPointTask.setTaskType("MINI_TEST");
        checkPointTask.setStatus("LOCKED");
        taskRepository.save(checkPointTask);
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
}
