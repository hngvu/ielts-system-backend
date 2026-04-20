package io.gsp26se16.moni.payment.dto.response;

/**
 * Trả về trạng thái quota miễn phí 1 lượt/ngày cho user hiện tại.
 * Chỉ áp dụng với AI_WRITING_SCORE / AI_SPEAKING_SCORE.
 *
 * @param serviceCode   service đang query
 * @param defaultCost   giá gốc trong DB (vd. 4 đậu)
 * @param usedToday     user đã tiêu thụ service này hôm nay chưa
 * @param effectiveCost giá user thực sự phải trả lần chấm kế tiếp (0 nếu còn free, default nếu hết)
 */
public record ServiceQuotaResponse(String serviceCode, int defaultCost, boolean usedToday, int effectiveCost) {}
