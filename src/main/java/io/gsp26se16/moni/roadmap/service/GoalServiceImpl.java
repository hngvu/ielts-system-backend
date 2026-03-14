package io.gsp26se16.moni.roadmap.service;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.request.TaskStatusUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.entity.Roadmap;
import io.gsp26se16.moni.roadmap.entity.Task;
import io.gsp26se16.moni.roadmap.repository.GoalRepository;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.repository.RoadmapRepository;
import io.gsp26se16.moni.roadmap.repository.TaskRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final RoadmapRepository roadmapRepository;
    private final TaskRepository taskRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final StimulusRepository stimulusRepository;

    @Override
    @Transactional
    public GoalCreateResponse createGoal(GoalCreateRequest request) {
        Users learner = getCurrentUser(); // Hàm helper lấy User từ Token

        if (request.getTargetBand() <= request.getStartingBand()) {
            throw new RuntimeException("Điểm mục tiêu phải lớn hơn điểm xuất phát!");
        }

        // 1. LƯU TRỮ GOAL CŨ (Cùng kỹ năng)
        goalRepository.findTopByUserAndSkillAndStatusOrderByIdDesc(learner, request.getSkill(), "ACTIVE")
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
        // Hệ thống giao 1 bài test định vị để kiểm tra xem điểm Starting Band học viên tự nhập có đúng không.
        Task placementTask = new Task();
        placementTask.setRoadmap(savedRoadmap);
        placementTask.setOrder(1);
        placementTask.setTaskType("PLACEMENT_TEST");
        placementTask.setStatus("TODO");
        // TODO: Đoạn này sau này bạn query 1 bài Test hoặc Stimulus có sẵn trong DB (theo skill) gán vào đây
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

        return activeGoals.stream().map(goal -> {
            Roadmap activeRoadmap = roadmapRepository.findByGoalAndStatus(goal, "ACTIVE").orElse(null);

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
        }).toList();
    }

    @Override
    @Transactional
    public GoalCreateResponse updateGoal(Integer goalId, GoalUpdateRequest request) {
        Users learner = getCurrentUser();

        // 1. Validate
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Mục tiêu!"));

        if (!goal.getUser().getId().equals(learner.getId())) {
            throw new RuntimeException("Bạn không có quyền sửa mục tiêu này");
        }
        if (!"ACTIVE".equals(goal.getStatus())) {
            throw new RuntimeException("Chỉ có thể sửa mục tiêu đang ACTIVE");
        }
        if (request.getTargetBand() <= goal.getStartingBand()) {
            throw new RuntimeException("Điểm mục tiêu (" + request.getTargetBand() + ") phải lớn hơn điểm xuất phát (" + goal.getStartingBand() + ")!");
        }

        // 2. Cập nhật Goal
        goal.setTargetBand(request.getTargetBand());
        goal.setDeadline(request.getDeadline());
        Goal savedGoal = goalRepository.save(goal);

        // 3. Đóng Roadmap cũ
        Roadmap oldRoadmap = roadmapRepository.findByGoalAndStatus(savedGoal, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy Lộ trình đang chạy cho Mục tiêu này"));
        oldRoadmap.setStatus("ARCHIVED");
        roadmapRepository.save(oldRoadmap);

        // 4. Mở Roadmap mới (Tăng version)
        Roadmap newRoadmap = new Roadmap();
        newRoadmap.setGoal(savedGoal);
        newRoadmap.setVersion(oldRoadmap.getVersion() + 1);
        newRoadmap.setStatus("ACTIVE");
        newRoadmap.setPriority(1);
        newRoadmap.setCreatedAt(LocalDateTime.now());
        Roadmap savedRoadmap = roadmapRepository.save(newRoadmap);

        generateSmartTasksForRoadmap(savedRoadmap, learner);

        return buildResponse(savedGoal, savedRoadmap, "Đã cập nhật Mục tiêu và sinh Lộ trình version " + savedRoadmap.getVersion() + " thành công!");
    }

    @Override
    @Transactional
    public void updateTaskStatus(Integer taskId, TaskStatusUpdateRequest request) {
        Users learner = getCurrentUser();

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài tập!"));

        // Bảo mật: Chỉ chủ nhân của Lộ trình mới được phép đánh dấu DONE
        if (!task.getRoadmap().getGoal().getUser().getId().equals(learner.getId())) {
            throw new RuntimeException("Bạn không có quyền cập nhật bài tập này");
        }

        // 1. Cập nhật trạng thái Task
        task.setStatus(request.getStatus().toUpperCase());
        taskRepository.save(task);

        // 2. 🔥 KỊCH BẢN 1: TRIGGER TỰ ĐỘNG SINH ROADMAP MỚI
        if ("DONE".equals(task.getStatus())) {
            Roadmap currentRoadmap = task.getRoadmap();

            // Đếm xem Lộ trình này còn bài nào chưa làm không?
            long remainingTasks = taskRepository.countByRoadmapIdAndStatusNot(currentRoadmap.getId(), "DONE");

            if (remainingTasks == 0) {
                // ĐÃ HẾT BÀI! KÍCH HOẠT QUY TRÌNH ĐẺ ROADMAP MỚI

                // Đóng Roadmap cũ
                currentRoadmap.setStatus("ARCHIVED");
                roadmapRepository.save(currentRoadmap);

                // Mở Roadmap mới (Version tăng lên 1)
                Roadmap newRoadmap = new Roadmap();
                newRoadmap.setGoal(currentRoadmap.getGoal());
                newRoadmap.setVersion(currentRoadmap.getVersion() + 1);
                newRoadmap.setStatus("ACTIVE");
                newRoadmap.setPriority(1);
                newRoadmap.setCreatedAt(LocalDateTime.now());
                Roadmap savedNewRoadmap = roadmapRepository.save(newRoadmap);

                // Gọi hàm để nhét bài tập chữa điểm yếu vào Lộ trình mới!
                generateSmartTasksForRoadmap(savedNewRoadmap, learner);
            }
        }
    }

    // --- Helper lấy User từ JWT Token ---
    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Chưa xác thực (Unauthenticated)");
        }

        String credentialId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId"); // Tùy thuộc vào claim bạn config trong token
        }

        if (credentialId == null) {
            throw new RuntimeException("Token không hợp lệ (Không tìm thấy userId)");
        }

        UserCredentials credentials = userCredentialsRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (credentials.getUser() == null) {
            throw new RuntimeException("Lỗi dữ liệu: UserCredentials không gắn với Users nào");
        }
        return credentials.getUser();
    }

    private void generateSmartTasksForRoadmap(Roadmap roadmap, Users learner) {
        Skill currentSkill = roadmap.getGoal().getSkill(); // Ví dụ: READING
        List<Stimulus> selectedStimuli = new ArrayList<>();

        // 1. BẮT MẠCH: Đọc hồ sơ năng lực của Học viên
        List<LearnerMetric> weakMetrics = learnerMetricRepository.findTop3ByUserOrderByMasteryLevelAsc(learner);

        if (!weakMetrics.isEmpty()) {
            // Lọc ra danh sách các Tag điểm yếu
            List<Tag> weakTags = weakMetrics.stream()
                    .map(LearnerMetric::getTag)
                    .toList();

            // 2. KÊ ĐƠN & BỐC THUỐC: Tìm các đoạn văn chứa nhiều câu hỏi trị điểm yếu nhất
            List<Stimulus> smartStimuli = stimulusRepository.findSmartStimuli(currentSkill, weakTags);

            // Xáo trộn ngẫu nhiên để bài tập không bị lặp lại và lấy 2 bài đầu tiên
            Collections.shuffle(smartStimuli);
            selectedStimuli = smartStimuli.stream().limit(2).toList();
        }

        // 3. FALLBACK (DỰ PHÒNG): Rơi vào đây nếu User mới tinh (Cold Start)
        // hoặc DB chưa có bài nào khớp Tag. Hệ thống sẽ lấy ngẫu nhiên 2 bài cùng Skill.
        if (selectedStimuli.isEmpty()) {
            List<Stimulus> fallbackStimuli = stimulusRepository.findBySkill(currentSkill);
            Collections.shuffle(fallbackStimuli);
            selectedStimuli = fallbackStimuli.stream().limit(2).toList();

            if (selectedStimuli.isEmpty()) {
                throw new RuntimeException("Kho dữ liệu trống! Vui lòng nhờ Admin tạo thêm bài tập cho kỹ năng " + currentSkill);
            }
        }

        // 4. GIAO VIỆC: Đóng gói thành các Task và đưa vào Lộ trình
        int order = 1;
        for (Stimulus stimulus : selectedStimuli) {
            Task practiceTask = new Task();
            practiceTask.setRoadmap(roadmap);
            practiceTask.setStimulus(stimulus); // 🔥 Gắn bài tập "vừa miếng" vào đây!
            practiceTask.setOrder(order++);
            practiceTask.setTaskType("PRACTICE_STIMULUS");
            practiceTask.setStatus("TODO");
            taskRepository.save(practiceTask);
        }

        // 5. CHỐT CHẶN KIỂM TRA: Bài Task cuối cùng luôn là một bài Test ngắn để đánh giá lại
        // (Để UI hiển thị cho user biết họ chuẩn bị được thăng cấp)
        Task checkPointTask = new Task();
        checkPointTask.setRoadmap(roadmap);
        checkPointTask.setOrder(order);
        checkPointTask.setTaskType("MINI_TEST"); // Hoặc "RE_EVALUATION"
        checkPointTask.setStatus("LOCKED"); // Khóa lại, bắt làm xong 2 bài trên mới mở
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
