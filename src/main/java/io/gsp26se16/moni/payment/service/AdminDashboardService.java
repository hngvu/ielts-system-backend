package io.gsp26se16.moni.payment.service;

import java.time.LocalDateTime;

import io.gsp26se16.moni.payment.dto.response.AdminRevenueDashboardResponse;

public interface AdminDashboardService {
    AdminRevenueDashboardResponse getRevenueDashboard(LocalDateTime startDate, LocalDateTime endDate);
}
