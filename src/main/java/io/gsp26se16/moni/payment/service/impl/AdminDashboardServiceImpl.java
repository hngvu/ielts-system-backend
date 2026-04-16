package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.payment.dto.response.AdminRevenueDashboardResponse;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.AdminDashboardService;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    static final String EXPERT_WRITING_SERVICE_CODE = "EXPERT_WRITING_SCORE";
    static final String EXPERT_SPEAKING_SERVICE_CODE = "EXPERT_SPEAKING_SCORE";
    static final String AI_WRITING_SERVICE_CODE = "AI_WRITING_SCORE";
    static final String AI_SPEAKING_SERVICE_CODE = "AI_SPEAKING_SCORE";

    PaymentRepository paymentRepository;
    CreditTransactionRepository creditTransactionRepository;
    UsersRepository usersRepository;
    TestSessionRepository testSessionRepository;

    @Override
    public AdminRevenueDashboardResponse getRevenueDashboard(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime safeStart =
                startDate != null ? startDate : LocalDateTime.now().minusDays(30);
        LocalDateTime safeEnd = endDate != null ? endDate : LocalDateTime.now();

        long topupRevenue =
                paymentRepository.sumAmountByStatusAndCreatedAtBetween(PaymentStatus.SUCCESS, safeStart, safeEnd);
        long topupCount = paymentRepository.countByStatusAndCreatedAtBetween(PaymentStatus.SUCCESS, safeStart, safeEnd);

        long expertWritingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);
        long expertWritingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);

        long expertSpeakingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);
        long expertSpeakingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);

        // AI Metrics
        long aiWritingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, AI_WRITING_SERVICE_CODE, safeStart, safeEnd);
        long aiSpeakingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, AI_SPEAKING_SERVICE_CODE, safeStart, safeEnd);
        long aiWritingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, AI_WRITING_SERVICE_CODE, safeStart, safeEnd);
        long aiSpeakingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, AI_SPEAKING_SERVICE_CODE, safeStart, safeEnd);

        // Additional operation metrics
        long newUsers = usersRepository.countByCreatedAtBetween(safeStart, safeEnd);
        long totalTests = testSessionRepository.countByStartedAtBetween(safeStart, safeEnd);
        long totalUsers = testSessionRepository.countDistinctUserByStartedAtBetween(safeStart, safeEnd);

        // Build daily revenue data
        List<Object[]> dailyRevenueData =
                paymentRepository.getDailyRevenueByStatusAndCreatedAtBetween(PaymentStatus.SUCCESS, safeStart, safeEnd);
        List<AdminRevenueDashboardResponse.DailyRevenue> dailyRevenue = new ArrayList<>();
        for (Object[] row : dailyRevenueData) {
            LocalDate date = convertToLocalDate(row[0]);
            long amount = ((Number) row[1]).longValue();
            dailyRevenue.add(new AdminRevenueDashboardResponse.DailyRevenue(date, amount));
        }

        // Build daily expert & AI jobs data
        List<Object[]> dailyWritingData =
                creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                        PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);
        List<Object[]> dailySpeakingData =
                creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                        PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);
        List<Object[]> dailyAiWritingData =
                creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                        PaymentType.CONSUME, AI_WRITING_SERVICE_CODE, safeStart, safeEnd);
        List<Object[]> dailyAiSpeakingData =
                creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                        PaymentType.CONSUME, AI_SPEAKING_SERVICE_CODE, safeStart, safeEnd);

        Map<LocalDate, Long> writingMap = new HashMap<>();
        for (Object[] row : dailyWritingData) {
            writingMap.put(convertToLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        Map<LocalDate, Long> speakingMap = new HashMap<>();
        for (Object[] row : dailySpeakingData) {
            speakingMap.put(convertToLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        Map<LocalDate, Long> aiWritingMap = new HashMap<>();
        for (Object[] row : dailyAiWritingData) {
            aiWritingMap.put(convertToLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        Map<LocalDate, Long> aiSpeakingMap = new HashMap<>();
        for (Object[] row : dailyAiSpeakingData) {
            aiSpeakingMap.put(convertToLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        // Combine all dates
        List<LocalDate> allDates = new ArrayList<>();
        allDates.addAll(writingMap.keySet());
        allDates.addAll(speakingMap.keySet());
        allDates.addAll(aiWritingMap.keySet());
        allDates.addAll(aiSpeakingMap.keySet());
        allDates = allDates.stream().distinct().sorted().toList();

        List<AdminRevenueDashboardResponse.DailyExpertJobs> dailyExpertJobs = new ArrayList<>();
        for (LocalDate date : allDates) {
            long writingJobs = writingMap.getOrDefault(date, 0L);
            long sJobs = speakingMap.getOrDefault(date, 0L);
            long aiWJobs = aiWritingMap.getOrDefault(date, 0L);
            long aiSJobs = aiSpeakingMap.getOrDefault(date, 0L);
            dailyExpertJobs.add(
                    new AdminRevenueDashboardResponse.DailyExpertJobs(date, writingJobs, sJobs, aiWJobs, aiSJobs));
        }

        return new AdminRevenueDashboardResponse(
                safeStart,
                safeEnd,
                topupRevenue,
                topupCount,
                expertWritingCredits,
                expertWritingJobs,
                expertSpeakingCredits,
                expertSpeakingJobs,
                expertWritingCredits + expertSpeakingCredits,
                expertWritingJobs + expertSpeakingJobs,
                aiWritingJobs,
                aiSpeakingJobs,
                aiWritingJobs + aiSpeakingJobs,
                aiWritingCredits + aiSpeakingCredits,
                totalTests,
                totalUsers,
                newUsers,
                dailyRevenue,
                dailyExpertJobs);
    }

    private LocalDate convertToLocalDate(Object dateObj) {
        if (dateObj == null) return null;
        if (dateObj instanceof LocalDate) return (LocalDate) dateObj;
        if (dateObj instanceof java.sql.Date) return ((java.sql.Date) dateObj).toLocalDate();
        if (dateObj instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) dateObj).getTime()).toLocalDate();
        }
        return LocalDate.parse(dateObj.toString());
    }
}
