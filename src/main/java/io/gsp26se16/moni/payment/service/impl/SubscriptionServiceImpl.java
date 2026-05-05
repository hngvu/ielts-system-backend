package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.SubscriptionPlanUpsertRequest;
import io.gsp26se16.moni.payment.dto.response.SubscriptionPlanResponse;
import io.gsp26se16.moni.payment.dto.response.UserSubscriptionResponse;
import io.gsp26se16.moni.payment.entity.SubscriptionPlan;
import io.gsp26se16.moni.payment.entity.UserSubscription;
import io.gsp26se16.moni.payment.repository.SubscriptionPlanRepository;
import io.gsp26se16.moni.payment.repository.UserSubscriptionRepository;
import io.gsp26se16.moni.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UsersRepository usersRepository;

    // ----- Plan read -----

    @Override
    public List<SubscriptionPlanResponse> listActivePlans() {
        return planRepository.findAll().stream()
                .filter(SubscriptionPlan::isActive)
                .sorted((a, b) -> Integer.compare(a.getPriceVnd(), b.getPriceVnd()))
                .map(this::toPlanResponse)
                .toList();
    }

    @Override
    public List<SubscriptionPlanResponse> listAllPlans() {
        return planRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getPriceVnd(), b.getPriceVnd()))
                .map(this::toPlanResponse)
                .toList();
    }

    @Override
    public SubscriptionPlanResponse getPlan(Integer id) {
        return planRepository
                .findById(id)
                .map(this::toPlanResponse)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
    }

    // ----- Plan admin CRUD -----

    @Override
    @Transactional
    public SubscriptionPlanResponse createPlan(SubscriptionPlanUpsertRequest req) {
        if (planRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        applyUpsert(plan, req, true);
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse updatePlan(Integer id, SubscriptionPlanUpsertRequest req) {
        SubscriptionPlan plan =
                planRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
        applyUpsert(plan, req, false);
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public void deletePlan(Integer id) {
        SubscriptionPlan plan =
                planRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
        plan.setActive(false);
        planRepository.save(plan);
        log.info("Soft-deleted subscription plan id={} code={}", id, plan.getCode());
    }

    // ----- User subscription -----

    @Override
    public Optional<UserSubscriptionResponse> getMyActiveSubscription(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Users user = credential.getUser();
        return userSubRepository
                .findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(user.getId(), LocalDateTime.now())
                .map(this::toUserSubResponse);
    }

    @Override
    @Transactional
    public void activateSubscription(String userId, Integer planId) {
        Users user = usersRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        SubscriptionPlan plan = planRepository
                .findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));

        String category = plan.getCategory() != null ? plan.getCategory() : "SCORING";
        int rolloverAi = 0;
        int rolloverExpert = 0;

        // Deactivate sub cũ cùng category + rollover quota còn lại.
        var oldPlanSub =
                userSubRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlan_CategoryOrderByEndAtDesc(
                        userId, LocalDateTime.now(), category);
        if (oldPlanSub.isPresent()) {
            UserSubscription old = oldPlanSub.get();
            rolloverAi = old.getRemainAi() > 0 ? old.getRemainAi() : 0;
            rolloverExpert = old.getRemainExpert() > 0 ? old.getRemainExpert() : 0;
            old.setActive(false);
            userSubRepository.save(old);
            log.info(
                    "Deactivated old sub id={} (category={}), rollover ai={} expert={}",
                    old.getId(),
                    category,
                    rolloverAi,
                    rolloverExpert);
        }

        // Cũng deactivate + rollover sub tạo từ package (plan=null) nếu có.
        var oldPkgSub = userSubRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlanIsNullOrderByEndAtDesc(
                userId, LocalDateTime.now());
        if (oldPkgSub.isPresent()) {
            UserSubscription old = oldPkgSub.get();
            rolloverAi += old.getRemainAi() > 0 ? old.getRemainAi() : 0;
            rolloverExpert += old.getRemainExpert() > 0 ? old.getRemainExpert() : 0;
            old.setActive(false);
            userSubRepository.save(old);
            log.info(
                    "Deactivated package-based sub id={}, rollover ai={} expert={}",
                    old.getId(),
                    old.getRemainAi(),
                    old.getRemainExpert());
        }

        int newAi = plan.getQuotaAi() == -1 ? -1 : plan.getQuotaAi() + rolloverAi;
        int newExpert = plan.getQuotaExpert() + rolloverExpert;

        LocalDateTime now = LocalDateTime.now();
        UserSubscription fresh = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startAt(now)
                .endAt(now.plusDays(plan.getDurationDays()))
                .remainAi(newAi)
                .remainExpert(newExpert)
                .usedAi(0)
                .usedExpert(0)
                .isActive(true)
                .build();
        userSubRepository.save(fresh);
        log.info(
                "Activated subscription: user={}, plan={}, endAt={}, quotaAi={} (rollover={}), quotaExpert={} (rollover={})",
                userId,
                plan.getCode(),
                fresh.getEndAt(),
                fresh.getRemainAi(),
                rolloverAi,
                fresh.getRemainExpert(),
                rolloverExpert);
    }

    @Override
    @Transactional
    public void addQuotaFromPackage(String userId, int quotaAi, int quotaExpert) {
        Users user = usersRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Ưu tiên cộng vào subscription có plan (SCORING), sau đó package-based (plan=null).
        var activeSub = userSubRepository
                .findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlan_CategoryOrderByEndAtDesc(
                        userId, LocalDateTime.now(), "SCORING")
                .or(() -> userSubRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlanIsNullOrderByEndAtDesc(
                        userId, LocalDateTime.now()));

        if (activeSub.isPresent()) {
            UserSubscription sub = activeSub.get();
            if (sub.getRemainAi() != -1) { // Không cộng vào unlimited
                sub.setRemainAi(sub.getRemainAi() + quotaAi);
            }
            sub.setRemainExpert(sub.getRemainExpert() + quotaExpert);
            userSubRepository.save(sub);
            log.info(
                    "Added package quota to existing sub id={}: ai+={}, expert+={}", sub.getId(), quotaAi, quotaExpert);
        } else {
            // Chưa có subscription nào → tạo mới (plan=null, không hết hạn).
            LocalDateTime now = LocalDateTime.now();
            UserSubscription fresh = UserSubscription.builder()
                    .user(user)
                    .plan(null)
                    .startAt(now)
                    .endAt(now.plusDays(36500)) // ~100 năm, "không giới hạn thời gian"
                    .remainAi(quotaAi)
                    .remainExpert(quotaExpert)
                    .usedAi(0)
                    .usedExpert(0)
                    .isActive(true)
                    .build();
            userSubRepository.save(fresh);
            log.info("Created package-based subscription for user={}: ai={}, expert={}", userId, quotaAi, quotaExpert);
        }
    }

    @Override
    public boolean hasActiveRoadmapSubscription(String userId) {
        return userSubRepository
                .findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlan_CategoryOrderByEndAtDesc(
                        userId, LocalDateTime.now(), "ROADMAP")
                .isPresent();
    }

    @Override
    public Optional<UserSubscriptionResponse> getActiveRoadmapSubscription(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Users user = credential.getUser();
        return userSubRepository
                .findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlan_CategoryOrderByEndAtDesc(
                        user.getId(), LocalDateTime.now(), "ROADMAP")
                .map(this::toUserSubResponse);
    }

    // ----- Helpers -----

    private void applyUpsert(SubscriptionPlan plan, SubscriptionPlanUpsertRequest req, boolean isCreate) {
        if (req.code() != null) plan.setCode(req.code());
        if (req.name() != null) plan.setName(req.name());
        if (req.description() != null) plan.setDescription(req.description());
        if (req.priceVnd() != null) plan.setPriceVnd(req.priceVnd());
        if (req.durationDays() != null) plan.setDurationDays(req.durationDays());
        if (req.quotaAi() != null) plan.setQuotaAi(req.quotaAi());
        if (req.quotaExpert() != null) plan.setQuotaExpert(req.quotaExpert());
        if (req.category() != null) plan.setCategory(req.category());
        else if (isCreate && plan.getCategory() == null) plan.setCategory("SCORING");
        if (req.isActive() != null) plan.setActive(req.isActive());
        else if (isCreate) plan.setActive(true);
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan p) {
        return new SubscriptionPlanResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getPriceVnd(),
                p.getDurationDays(),
                p.getQuotaAi(),
                p.getQuotaExpert(),
                p.getCategory() != null ? p.getCategory() : "SCORING",
                p.isActive());
    }

    private UserSubscriptionResponse toUserSubResponse(UserSubscription s) {
        return new UserSubscriptionResponse(
                s.getId(),
                s.getPlan() != null ? s.getPlan().getId() : null,
                s.getPlan() != null ? s.getPlan().getCode() : "PACKAGE",
                s.getPlan() != null ? s.getPlan().getName() : "Gói lẻ",
                s.getStartAt(),
                s.getEndAt(),
                s.getRemainAi(),
                s.getRemainExpert(),
                s.getUsedAi(),
                s.getUsedExpert(),
                s.isActive());
    }
}
