package io.gsp26se16.moni.ai.monitoring.dto;

public record AiHealthResponse(
        // Tổng số bài AI
        long totalAiEvaluations,
        long writingEvaluations,
        long speakingEvaluations,

        // Tỉ lệ lỗi hôm nay vs hôm qua
        long failedToday,
        long totalToday,
        double errorRateToday,
        long failedYesterday,
        long totalYesterday,
        double errorRateYesterday,
        double errorRateChange,

        // Latency trung bình (seconds)
        Double avgLatencySeconds,
        Double avgWritingLatencySeconds,
        Double avgSpeakingLatencySeconds,

        // Alert status
        boolean alertActive,
        String alertMessage) {}
