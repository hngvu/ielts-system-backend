package io.gsp26se16.moni.roadmap.service;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.Roadmap;
import io.gsp26se16.moni.roadmap.entity.Task;
import io.gsp26se16.moni.roadmap.repository.GoalRepository;
import io.gsp26se16.moni.roadmap.repository.RoadmapRepository;
import io.gsp26se16.moni.roadmap.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final RoadmapRepository roadmapRepository;
    private final TaskRepository taskRepository;
    private final UserCredentialsRepository userCredentialsRepository;

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
        Users learner = getCurrentUser(); // Lấy học viên đang đăng nhập

        // 1. Lấy tất cả các Goal đang ACTIVE của học viên này
        List<Goal> activeGoals = goalRepository.findAllByUserAndStatus(learner, "ACTIVE");

        // 2. Map sang DTO và tìm kèm theo Roadmap tương ứng
        return activeGoals.stream().map(goal -> {

            // Tìm cái "Bìa kẹp hồ sơ" (Roadmap) đang mở của kỹ năng này
            Roadmap activeRoadmap = roadmapRepository.findByGoalAndStatus(goal, "ACTIVE").orElse(null);

            return GoalResponse.builder()
                    .goalId(goal.getId())
                    .skill(goal.getSkill())
                    .startingBand(goal.getStartingBand())
                    .targetBand(goal.getTargetBand())
                    .deadline(goal.getDeadline())
                    .status(goal.getStatus())
                    // Gắn ID Roadmap vào để Frontend làm nút "Tiếp tục học"
                    .activeRoadmapId(activeRoadmap != null ? activeRoadmap.getId() : null)
                    .activeRoadmapVersion(activeRoadmap != null ? activeRoadmap.getVersion() : null)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public GoalCreateResponse updateGoal(Integer goalId, GoalUpdateRequest request) {
        Users learner = getCurrentUser();

        // 1. Tìm và xác thực Goal
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

        // 2. Cập nhật dữ liệu Goal
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
        newRoadmap.setVersion(oldRoadmap.getVersion() + 1); // 🔥 Version được nâng cấp ở đây!
        newRoadmap.setStatus("ACTIVE");
        newRoadmap.setPriority(1);
        newRoadmap.setCreatedAt(LocalDateTime.now());
        Roadmap savedRoadmap = roadmapRepository.save(newRoadmap);

        // 5. Sinh Task mới cho lộ trình (Ví dụ: 1 bài kiểm tra định vị lại năng lực)
        Task reEvalTask = new Task();
        reEvalTask.setRoadmap(savedRoadmap);
        reEvalTask.setOrder(1);
        reEvalTask.setTaskType("RE_EVALUATION_TEST");
        reEvalTask.setStatus("TODO");
        taskRepository.save(reEvalTask);

        // Trả về DTO (Dùng lại GoalCreateResponse cho tiện)
        return GoalCreateResponse.builder()
                .goalId(savedGoal.getId())
                .skill(savedGoal.getSkill())
                .startingBand(savedGoal.getStartingBand())
                .targetBand(savedGoal.getTargetBand())
                .deadline(savedGoal.getDeadline())
                .status(savedGoal.getStatus())
                .roadmapId(savedRoadmap.getId())
                .roadmapVersion(savedRoadmap.getVersion())
                .message("Đã cập nhật Mục tiêu và sinh Lộ trình version " + savedRoadmap.getVersion() + " thành công!")
                .build();
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
}
