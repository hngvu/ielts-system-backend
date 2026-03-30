package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.payment.dto.response.AdminRevenueDashboardResponse;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.AdminDashboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    static final String EXPERT_WRITING_SERVICE_CODE = "EXPERT_WRITING_SCORE";
    static final String EXPERT_SPEAKING_SERVICE_CODE = "EXPERT_SPEAKING_SCORE";

    PaymentRepository paymentRepository;
    CreditTransactionRepository creditTransactionRepository;

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

        // Build daily revenue data
        List<Object[]> dailyRevenueData = paymentRepository.getDailyRevenueByStatusAndCreatedAtBetween(
                PaymentStatus.SUCCESS, safeStart, safeEnd);
        List<AdminRevenueDashboardResponse.DailyRevenue> dailyRevenue = new ArrayList<>();
        for (Object[] row : dailyRevenueData) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long amount = ((Number) row[1]).longValue();
            dailyRevenue.add(new AdminRevenueDashboardResponse.DailyRevenue(date, amount));
        }

        // Build daily expert jobs data
        List<Object[]> dailyWritingData = creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);
        List<Object[]> dailySpeakingData = creditTransactionRepository.countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);

        Map<LocalDate, Long> writingMap = new HashMap<>();
        for (Object[] row : dailyWritingData) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            writingMap.put(date, count);
        }

        Map<LocalDate, Long> speakingMap = new HashMap<>();
        for (Object[] row : dailySpeakingData) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            speakingMap.put(date, count);
        }

        // Combine all dates
        List<LocalDate> allDates = new ArrayList<>();
        allDates.addAll(writingMap.keySet());
        allDates.addAll(speakingMap.keySet());
        allDates = allDates.stream().distinct().sorted().toList();

        List<AdminRevenueDashboardResponse.DailyExpertJobs> dailyExpertJobs = new ArrayList<>();
        for (LocalDate date : allDates) {
            long writingJobs = writingMap.getOrDefault(date, 0L);
            long speakingJobs = speakingMap.getOrDefault(date, 0L);
            dailyExpertJobs.add(new AdminRevenueDashboardResponse.DailyExpertJobs(date, writingJobs, speakingJobs));
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
                dailyRevenue,
                dailyExpertJobs);
    }
}
