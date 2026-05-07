package io.gsp26se16.moni.placement.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.writing.service.PromptLoader;
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
    private final PromptLoader promptLoader;

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
        String timeSection;
        if (daysRemaining >= 0) {
            timeSection = String.format(
                    "## Thời gian còn lại: %d ngày (đến %s)\n"
                            + "Hãy cân nhắc thời gian này để đánh giá mục tiêu có khả thi không.",
                    daysRemaining, req.getExamDate());
        } else {
            timeSection = "## Học viên CHƯA chốt lịch thi\n"
                    + "Không có ràng buộc thời gian — phân tích DỰA TRÊN BAND GAP (chênh lệch giữa trình độ hiện tại "
                    + "và mục tiêu) để đưa ra mục tiêu phù hợp với năng lực hiện tại của học viên. "
                    + "Tự bạn quyết định band hợp lý dựa trên IELTS Band Descriptors.";
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("CURRENT_READING", String.format("%.1f", req.getCurrentReading()));
        placeholders.put("CURRENT_LISTENING", String.format("%.1f", req.getCurrentListening()));
        placeholders.put("CURRENT_WRITING", String.format("%.1f", req.getCurrentWriting()));
        placeholders.put("CURRENT_SPEAKING", String.format("%.1f", req.getCurrentSpeaking()));
        placeholders.put("CURRENT_OVERALL", String.format("%.1f", req.getCurrentOverall()));
        placeholders.put("TARGET_READING", String.format("%.1f", safe(req.getTargetReading())));
        placeholders.put("TARGET_LISTENING", String.format("%.1f", safe(req.getTargetListening())));
        placeholders.put("TARGET_WRITING", String.format("%.1f", safe(req.getTargetWriting())));
        placeholders.put("TARGET_SPEAKING", String.format("%.1f", safe(req.getTargetSpeaking())));
        placeholders.put("TARGET_OVERALL", String.format("%.1f", safe(req.getTargetOverall())));
        placeholders.put("TIME_SECTION", timeSection);

        return promptLoader.loadPrompt("placement/ai_recommend.txt", placeholders);
    }

    private double safe(Double val) {
        return val != null ? val : 0.0;
    }

    private AiRecommendResponse parseResponse(String raw) {
        try {
            // Cắt từ '{' đầu tới '}' cuối — loại bỏ markdown fences ```json,
            // preamble tự nhiên ("Sure, here's..."), và thinking tags <think>...</think>
            String json = raw;
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            // Llama hay xuống dòng raw bên trong string value cho dễ đọc — JSON spec
            // không cho phép, phải escape thành \n. Sanitize trước khi feed Jackson.
            json = escapeRawNewlinesInsideStrings(json);

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

    // Replace raw \n / \r với \\n CHỈ khi đang ở giữa cặp dấu nháy kép của JSON string,
    // không động đến structural newlines giữa các field (vốn không gây lỗi parse).
    private String escapeRawNewlinesInsideStrings(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                out.append(c);
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                out.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                out.append(c);
                continue;
            }
            if (inString && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++; // gộp \r\n thành 1 escape
                }
                out.append("\\n");
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
