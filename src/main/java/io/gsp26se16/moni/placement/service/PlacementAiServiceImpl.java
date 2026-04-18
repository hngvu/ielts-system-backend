package io.gsp26se16.moni.placement.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.placement.dto.request.AiRecommendRequest;
import io.gsp26se16.moni.placement.dto.response.AiRecommendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementAiServiceImpl implements PlacementAiService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public AiRecommendResponse recommend(AiRecommendRequest request) {
        long daysRemaining =
                request.getExamDate() != null ? ChronoUnit.DAYS.between(LocalDate.now(), request.getExamDate()) : -1;

        String prompt = buildPrompt(request, daysRemaining);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt().user(prompt).call().content();
            log.info("AI recommend raw response: {}", response);

            if (response == null || response.isBlank()) {
                log.error("AI returned empty response");
                return fallbackResponse();
            }

            return parseResponse(response);
        } catch (Exception e) {
            log.error("AI recommend call failed", e);
            return fallbackResponse();
        }
    }

    private AiRecommendResponse fallbackResponse() {
        return AiRecommendResponse.builder()
                .analysis("Không thể kết nối AI. Vui lòng thử lại sau.")
                .studyPlan("")
                .build();
    }

    private String buildPrompt(AiRecommendRequest req, long daysRemaining) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia tư vấn IELTS. Phân tích trình độ hiện tại và mục tiêu của học viên, ");
        sb.append("sau đó gợi ý mục tiêu band điểm phù hợp và lộ trình học.\n\n");

        sb.append("## Bảng quy đổi band IELTS (Academic)\n");
        sb.append("Band điểm IELTS là thang 0-9, bước nhảy 0.5.\n");
        sb.append("- Tăng 0.5 band: cần ~100-200 giờ học tập trung\n");
        sb.append("- Tăng 1.0 band: cần ~200-400 giờ học tập trung\n");
        sb.append("- Reading & Listening dễ tăng nhanh hơn Writing & Speaking\n");
        sb.append("- Overall = trung bình 4 kỹ năng, làm tròn đến 0.5 gần nhất\n\n");

        sb.append("## Trình độ hiện tại\n");
        sb.append(String.format("- Reading: %.1f\n", req.getCurrentReading()));
        sb.append(String.format("- Listening: %.1f\n", req.getCurrentListening()));
        sb.append(String.format("- Writing: %.1f\n", req.getCurrentWriting()));
        sb.append(String.format("- Speaking: %.1f\n", req.getCurrentSpeaking()));
        sb.append(String.format("- Overall: %.1f\n\n", req.getCurrentOverall()));

        sb.append("## Mục tiêu hiện tại của học viên\n");
        sb.append(String.format("- Reading: %.1f\n", safe(req.getTargetReading())));
        sb.append(String.format("- Listening: %.1f\n", safe(req.getTargetListening())));
        sb.append(String.format("- Writing: %.1f\n", safe(req.getTargetWriting())));
        sb.append(String.format("- Speaking: %.1f\n", safe(req.getTargetSpeaking())));
        sb.append(String.format("- Overall: %.1f\n\n", safe(req.getTargetOverall())));

        if (daysRemaining >= 0) {
            sb.append(String.format("## Thời gian còn lại: %d ngày (đến %s)\n\n", daysRemaining, req.getExamDate()));
        } else {
            sb.append("## Chưa có ngày thi cụ thể\n\n");
        }

        sb.append("## Yêu cầu\n");
        sb.append("1. Đánh giá mục tiêu hiện tại có thực tế không dựa trên trình độ và thời gian còn lại\n");
        sb.append("2. Gợi ý band điểm MỤC TIÊU phù hợp cho từng kỹ năng (bước 0.5)\n");
        sb.append("3. Đưa ra lộ trình học ngắn gọn, ưu tiên kỹ năng nào trước\n\n");

        sb.append("## Trả lời theo JSON format chính xác:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"recommendedReading\": 6.5,\n");
        sb.append("  \"recommendedListening\": 6.5,\n");
        sb.append("  \"recommendedWriting\": 6.0,\n");
        sb.append("  \"recommendedSpeaking\": 6.0,\n");
        sb.append("  \"recommendedOverall\": 6.5,\n");
        sb.append("  \"analysis\": \"Phân tích ngắn gọn 2-3 câu\",\n");
        sb.append("  \"studyPlan\": \"Lộ trình học ngắn gọn 3-5 dòng\"\n");
        sb.append("}\n");
        sb.append("```\n");
        sb.append("CHỈ trả về JSON, không thêm text khác.");

        return sb.toString();
    }

    private double safe(Double val) {
        return val != null ? val : 0.0;
    }

    private AiRecommendResponse parseResponse(String raw) {
        try {
            // Extract JSON from response (may be wrapped in ```json ... ```)
            String json = raw;
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }

            JsonNode node = objectMapper.readTree(json);
            return AiRecommendResponse.builder()
                    .recommendedReading(node.path("recommendedReading").asDouble())
                    .recommendedListening(node.path("recommendedListening").asDouble())
                    .recommendedWriting(node.path("recommendedWriting").asDouble())
                    .recommendedSpeaking(node.path("recommendedSpeaking").asDouble())
                    .recommendedOverall(node.path("recommendedOverall").asDouble())
                    .analysis(node.path("analysis").asText(""))
                    .studyPlan(node.path("studyPlan").asText(""))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", raw, e);
            return AiRecommendResponse.builder()
                    .analysis("Không thể phân tích. Vui lòng thử lại.")
                    .studyPlan("")
                    .build();
        }
    }
}
