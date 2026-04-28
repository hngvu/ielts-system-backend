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
        sb.append("Bạn là chuyên gia tư vấn IELTS có nhiều năm kinh nghiệm luyện thi. ");
        sb.append("Hãy phân tích trình độ hiện tại của học viên, đối chiếu với mục tiêu họ đặt ra, ");
        sb.append("sau đó đề xuất mục tiêu band điểm phù hợp và giải thích chi tiết LÝ DO.\n\n");

        sb.append("## Kiến thức nền IELTS (Academic) — bám theo IELTS Band Descriptors chính thức\n");
        sb.append(
                "- Band IELTS thang 0-9, bước 0.5. Overall = trung bình 4 kỹ năng, làm tròn .25→.5 và .75→band kế tiếp.\n");
        sb.append("- Tăng 0.5 band thường cần ~100-200 giờ học tập trung; tăng 1.0 band cần ~200-400 giờ.\n");
        sb.append("- Reading & Listening (kỹ năng tiếp thụ) tăng nhanh hơn Writing & Speaking (kỹ năng sản sinh).\n");
        sb.append(
                "- Writing & Speaking thường bị nghẽn ở band 6.0-6.5 vì yêu cầu lexical resource và grammar range cao.\n");
        sb.append("- Khoảng cách band > 2.0 trong thời gian < 6 tháng là phi thực tế với đa số học viên.\n");
        sb.append("- Mục tiêu hợp lý nên cao hơn trình độ hiện tại 0.5-1.5 band tùy thời gian và cường độ học.\n\n");

        sb.append("## Trình độ hiện tại của học viên (qua placement test)\n");
        sb.append(String.format("- Reading: %.1f\n", req.getCurrentReading()));
        sb.append(String.format("- Listening: %.1f\n", req.getCurrentListening()));
        sb.append(String.format("- Writing: %.1f\n", req.getCurrentWriting()));
        sb.append(String.format("- Speaking: %.1f\n", req.getCurrentSpeaking()));
        sb.append(String.format("- Overall: %.1f\n\n", req.getCurrentOverall()));

        sb.append("## Mục tiêu học viên đang đặt\n");
        sb.append(String.format("- Reading: %.1f\n", safe(req.getTargetReading())));
        sb.append(String.format("- Listening: %.1f\n", safe(req.getTargetListening())));
        sb.append(String.format("- Writing: %.1f\n", safe(req.getTargetWriting())));
        sb.append(String.format("- Speaking: %.1f\n", safe(req.getTargetSpeaking())));
        sb.append(String.format("- Overall: %.1f\n\n", safe(req.getTargetOverall())));

        if (daysRemaining >= 0) {
            sb.append(String.format("## Thời gian còn lại: %d ngày (đến %s)\n", daysRemaining, req.getExamDate()));
            sb.append("Hãy cân nhắc thời gian này để đánh giá mục tiêu có khả thi không.\n\n");
        } else {
            sb.append("## Học viên CHƯA chốt lịch thi\n");
            sb.append("Không có ràng buộc thời gian — phân tích DỰA TRÊN BAND GAP (chênh lệch giữa trình độ hiện tại ");
            sb.append("và mục tiêu) để đưa ra mục tiêu phù hợp với năng lực hiện tại của học viên. ");
            sb.append("Tự bạn quyết định band hợp lý dựa trên IELTS Band Descriptors.\n\n");
        }

        sb.append("## Yêu cầu phân tích\n");
        sb.append(
                "1. So sánh từng kỹ năng: trình độ hiện tại vs mục tiêu, chỉ ra kỹ năng nào ĐANG QUÁ XA, kỹ năng nào hợp lý.\n");
        sb.append(
                "2. Đề xuất band MỤC TIÊU phù hợp cho từng kỹ năng (bước 0.5) — có thể giữ nguyên nếu mục tiêu đã hợp lý.\n");
        sb.append("3. Trong phần \"analysis\" GIẢI THÍCH CHI TIẾT lý do tại sao học viên nên chọn band bạn đề xuất, ");
        sb.append("dẫn chứng cụ thể: gap bao nhiêu band, cần bao nhiêu giờ học, kỹ năng nào nên ưu tiên trước. ");
        sb.append("Viết tự nhiên, thuyết phục, độ dài 5-8 câu, có thể dùng xuống dòng để tách ý.\n");
        sb.append("4. Trong \"studyPlan\" tóm tắt lộ trình học 3-5 dòng, ưu tiên kỹ năng nào trước.\n\n");

        sb.append("## Trả lời theo JSON format chính xác:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"recommendedReading\": 6.5,\n");
        sb.append("  \"recommendedListening\": 6.5,\n");
        sb.append("  \"recommendedWriting\": 6.0,\n");
        sb.append("  \"recommendedSpeaking\": 6.0,\n");
        sb.append("  \"recommendedOverall\": 6.5,\n");
        sb.append("  \"analysis\": \"Phân tích chi tiết 5-8 câu, giải thích rõ lý do chọn band đề xuất\",\n");
        sb.append("  \"studyPlan\": \"Lộ trình học 3-5 dòng\"\n");
        sb.append("}\n");
        sb.append("```\n");
        sb.append("CHỈ trả về JSON, không thêm text khác. Toàn bộ nội dung text bằng tiếng Việt.");

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
