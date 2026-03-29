# Luồng chấm điểm IELTS Writing (Integration Guide cho FE)

Tài liệu này giải thích chi tiết luồng xử lý chấm điểm Writing trong hệ thống Backend (`src/main/java/io/gsp26se16/moni/ai/writing/...`) để team Frontend (FE) nắm rõ phục vụ cho việc tích hợp.

## 1. Thông tin API Endpoint

**Endpoint:** `POST /api/v1/ai/writing/score`
**Content-Type:** `multipart/form-data`
**Authentication:** Yêu cầu (gửi kèm JWT Token). API sẽ tự động trừ Credit (`AI_WRITING_SCORE`) của User.

### Request Parameters (Form Data)
- `question` (String, Bắt buộc): Đề bài Writing.
- `answer` (String, Tùy chọn, thông thường FE sẽ gửi): Bài làm (essay) của người dùng.
- `stimulusId` (Integer, Tùy chọn): ID của Question Group (dùng cho Task 1 để lấy/cache bản phân tích hình ảnh biểu đồ - Vision Analysis).
- `chartImage` (File, Tùy chọn): File hình ảnh biểu đồ của Task 1.

> **⚠️ LƯU Ý RẤT QUAN TRỌNG VỀ PHÂN LOẠI TASK 1 VÀ TASK 2 TỪ PHÍA BE**
> Backend hiện tại đang dựa vào `chartImage` để phân biệt Task 1 và Task 2:
> ```java
> boolean isTask1 = (request.getChartImage() != null && !request.getChartImage().isEmpty() && request.getChartImage().getSize() > 0);
> ```
> - **Để chấm Task 1:** FE **BẮT BUỘC** phải đính kèm file `chartImage` (có size > 0) trong request body, nếu không BE sẽ tự động chuyển sang luồng chấm của Task 2.
> - **Để chấm Task 2:** FE **KHÔNG ĐƯỢC** đính kèm file `chartImage` (hoặc gửi file rỗng).

---

## 2. Luồng xử lý chi tiết (Pipeline Flow)

Mỗi khi Request hợp lệ được gửi lên, Backend sẽ tạo một bản ghi `WritingSubmission` vào Database với trạng thái `PROCESSING`. Sau đó, bài viết sẽ trải qua nhiều Phase phân tích và chấm điểm hoàn toàn độc lập (sử dụng Spring AI / LLM).

### Kịch bản A: Luồng chấm Task 1 (Khi có `chartImage`)

1. **Phase 0 (Vision Analysis & Caching):**
   - Nếu FE có gửi kèm `stimulusId`, Backend sẽ kiểm tra trong DB xem đã có sẵn "Vision Analysis" (bản phân tích biểu đồ) cho `stimulusId` này chưa.
   - Nếu chưa có (hoặc chưa cache), Backend gọi Gemini Vision API cùng `chartImage` để phân tích biểu đồ. Kết quả sau đó được lưu lại theo `stimulusId` này để tái sử dụng.
2. **Phase 1 (Structural Parse):** Giao tiếp với LLM (`phase1_parse.txt`) để phân tích và bóc tách cấu trúc bài luận.
3. **Phase 2 (TA - Task Achievement):** Gửi Cấu trúc bài luận (ở Phase 1) + Dữ liệu phân tích biểu đồ (ở Phase 0) + Bài làm tới LLM để chấm tiêu chí Task Achievement.
4. **Phase 3 (CC - Coherence):** Chấm độ mạch lạc, liên kết câu/đoạn.
5. **Phase 4 (LR - Lexical Resource):** Chấm tiêu chí từ vựng.
6. **Phase 5 (GRA - Grammar):** Chấm tiêu chí ngữ pháp.
7. **Phase 6 (Calculate & Rule Engine):** Tổng hợp điểm từ Phase 2->5. BE có một `RuleEngine` để phát hiện các lỗi (Violations) nhằm xác định điểm `Overall Band` (áp dụng luật trừ điểm hoặc cap điểm một cách tự động).
8. **Phase 7 (Feedback):** Tổng hợp toàn bộ kết quả đưa về cho LLM (`phase7_feedback.txt`) để sinh ra lời giải thích và nhận xét chi tiết.
9. **Lưu DB & Trả kết quả:** Cập nhật trạng thái `COMPLETED`, lưu `AiEvaluation` và trả JSON response.

### Kịch bản B: Luồng chấm Task 2 (Khi không có `chartImage`)

1. **Phase 1 (Structural Parse):** Dùng prompt riêng cho Task 2 (`phase1_parse_task2.txt`) để phân tích cấu trúc bài viết (nhận diện mở, thân, kết...).
2. **Phase 2 (TR - Task Response):** Đưa câu hỏi, bài làm và cấu trúc vào LLM để đánh giá mức độ trả lời đúng trọng tâm (`phase2_tr.txt`).
3. **Phase 3 (CC - Coherence):** Đánh giá tiêu chí độ mạch lạc.
4. **Phase 4 (LR - Lexical Resource):** Đánh giá vốn từ vựng.
5. **Phase 5 (GRA - Grammar):** Đánh giá ngữ pháp.
6. **Phase 6 (Calculate & Rule Engine):** Tương tự Task 1, tính toán các điểm thành phần qua Rule Engine nhằm ấn định Band điểm tổng cuối cùng.
7. **Phase 7 (Feedback):** Đưa tất cả thông tin vào prompt (`phase7_feedback_task2.txt`) để tạo phản hồi cụ thể cho sinh viên.
8. **Lưu DB & Trả kết quả:** Cập nhật trạng thái `COMPLETED`, lưu `AiEvaluation` và trả JSON response.

---

## 3. Cấu trúc Response (JSON)

Sau khi xử lý xong (có thể mất thời gian tuỳ vào API LLM do BE gọi LLM nhiều lần qua nhiều phase), BE sẽ trả về Object theo định dạng sau:

```json
{
  "parsed_structure": {
    // Thông tin phân tách cấu trúc của bài viết kiếm được từ Phase 1. 
    // Các giá trị trong đây sẽ khác nhau đôi chút giữa Task 1 và Task 2 tùy vào kết quả parse json của LLM.
  },
  "assessment": {
    "final_band": 6.5,                // Điểm số Overall sau cùng
    "overall_cap": null,              // Nếu bị giới hạn điểm do RuleEngine (vd: bài off-topic)
    "applied_hard_rules": [ ... ],    // Các quy tắc Penalty/Hard Rule đã vi phạm
    "criteria": {                     // Chi tiết điểm từng thành phần 
      "TA": {                         // (Task 1 là TA, Task 2 là TR)
        "band": 6.5,
        ...
      },
      "CC": {
        "band": 6.0,
        ...
      },
      "LR": { ... },
      "GRA": { ... }
    }
  },
  "feedback": {
    // Nhận xét chi tiết do hệ thống AI tổng hợp dựa trên kết quả từng phase
    ...
  }
}
```

### Xử lý lỗi (Error Handling)
Nếu quá trình giải quyết thất bại (ví dụ: Lỗi gọi API bên thứ ba, Hết credit...), BE sẽ thay đổi trạng thái submission thành `FAILED` trong DB và trả về error response cho FE kèm theo mã lỗi thích hợp. 
- Thiếu authentication: `UNAUTHENTICATED`
- Hết credit: Trả lỗi theo ngoại lệ từ `creditService`
- Hoặc HTTP 500 do lỗi kết nối nội bộ đến LLM. Trang FE nên hiển thị loading trong lúc đợi và quản lý timeout phù hợp.
