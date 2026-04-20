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
        // Plan đã có user subscription → soft delete để giữ history (giống logic package).
        // Không cho hard delete vì Payment + UserSubscription FK tham chiếu.
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

        // Deactivate sub cũ (nếu có) — mua mới sẽ ghi đè, không rollover quota (đã chốt).
        userSubRepository
                .findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(userId, LocalDateTime.now())
                .ifPresent(old -> {
                    old.setActive(false);
                    userSubRepository.save(old);
                    log.info("Replaced existing active sub id={} when user buys new plan", old.getId());
                });

        LocalDateTime now = LocalDateTime.now();
        UserSubscription fresh = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startAt(now)
                .endAt(now.plusDays(plan.getDurationDays()))
                .remainAi(plan.getQuotaAi())
                .remainExpert(plan.getQuotaExpert())
                .usedAi(0)
                .usedExpert(0)
                .isActive(true)
                .build();
        userSubRepository.save(fresh);
        log.info(
                "Activated subscription: user={}, plan={}, endAt={}, quotaAi={}, quotaExpert={}",
                userId,
                plan.getCode(),
                fresh.getEndAt(),
                fresh.getRemainAi(),
                fresh.getRemainExpert());
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
                p.isActive());
    }

    private UserSubscriptionResponse toUserSubResponse(UserSubscription s) {
        return new UserSubscriptionResponse(
                s.getId(),
                s.getPlan().getId(),
                s.getPlan().getCode(),
                s.getPlan().getName(),
                s.getStartAt(),
                s.getEndAt(),
                s.getRemainAi(),
                s.getRemainExpert(),
                s.getUsedAi(),
                s.getUsedExpert(),
                s.isActive());
    }
}
