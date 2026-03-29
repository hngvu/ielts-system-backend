# Luồng chấm điểm và thi IELTS Speaking (Integration Guide cho FE)

Tài liệu này giải thích chi tiết luồng tích hợp hệ thống thi Speaking (Flow tạo đề, thi trực tiếp từng Part) và luồng phân tích chấm điểm bài thi ở phía Backend thông qua thư mục `ai/speaking`. Mục tiêu để FE dễ dàng hình dung toàn bộ Data đi qua đường truyền trong một phiên làm Test.

**Lưu ý:** Việc làm bài thi Speaking diễn ra **hoàn toàn thông qua WebSocket**, kết nối tới:
`ws://<server>/ws/speaking/exam?token=<JWT>`

---

## 1. Luồng cấu trúc Data tạo đề & luồng thi (Mô phỏng từng Part)

Sau khi user kết nối WebSocket thành công, BE sẽ đóng vai trò là Examiner và liên tục điều hướng người dùng qua 3 Part thi. Dưới đây là tuần tự các messages gửi lên/xuống.

### Bước 1: Khởi động bài test (Start Exam)
FE gửi yêu cầu bắt đầu với `testId` để BE load bộ đề từ Database:
```json
{
  "type": "start_exam",
  "testId": 101
}
```

### Bước 2: Hoạt động trong Part 1 (Hỏi - Đáp liên tục)
Backend sẽ tự động chia ra các câu hỏi chính (Main) và câu hỏi mở rộng (Follow-up) để đẩy dần xuống cho FE.

**1. BE gửi thông tin câu hỏi để FE hiển thị (Text):**
```json
{
  "type": "question",
  "partNumber": 1,
  "questionId": 15,
  "text": "Let's talk about your hometown. Where do you live?",
  "isFollowUp": false
}
```

**2. BE gửi ngay luồng Audio (Giọng đọc AI của câu hỏi trên):**
```json
{ "type": "audio_chunk", "data": "UklGRiQAAABXQVZFZm10IBAAAA... (Base64)" }
{ "type": "audio_chunk", "data": "AABXQVZFZm10IBAAAAABAAEA... (Base64)" }
{ "type": "audio_end" } // Tín hiệu báo hết Audio. FE gộp các chunks lại, thành viên MP3 và PLAY.
```

**3. FE ghi âm user trả lời và gửi lại dạng Văn bản:**
Sau khi audio chạy xong, FE tự động bật mic, dùng STT (AssemblyAI) để nghe người dùng nói. Khi user bấm "Nộp câu trả lời" hoặc tự động ngưng, FE gửi:
```json
{
  "type": "transcript",
  "partNumber": 1,
  "questionId": 15,
  "text": "I live in a small coastal city in central Vietnam..."
}
```
> *Quá trình (1) -> (2) -> (3) diễn ra lặp đi lặp lại cho đến khi hết Part 1.*

---

### Bước 3: Hoạt động trong Part 2 (Cue Card)
Kết thúc các câu Part 1, BE sẽ đọc câu dẫn chuyển ý (dưới dạng text và audio_chunk) sau đó xuất hiện **Cue Card**.

**1. BE gửi Cue Card để user chuẩn bị:**
```json
{
  "type": "show_cue_card",
  "duration": 60,
  "questionId": 28,
  "topic": "Describe a book you have recently read.\n\nYou should say:\n- What kind of book it is\n- What it is about\n- Why you decided to read it\nAnd explain if you would recommend it to others."
}
```
> Khi nhận được event này, FE hiển thị bảng đếm ngược từ 60 (giây) về 0 để User chuẩn bị.

**2. Bắt đầu nói (Speaking):**
Khi hết hạn 60s, FE gửi message để BE ghi nhận sự kiện phần thi nói bắt đầu:
```json
{ "type": "start_speaking_part2" }
```
> FE lại bật Mic đếm ngược 120s (2 phút). STT liên tục capture tiếng người dùng.

**3. Kết thúc nói:**
Khi hết 120s hoặc User nhấn "Kết thúc sớm", FE gửi toàn bộ bài nói trong vòng 2 phút đó lên:
```json
{
  "type": "stop_speaking_part2",
  "text": "The book I want to talk about is Atomic Habits written by James Clear..."
}
```

---

### Bước 4: Hoạt động trong Part 3 (Hỏi - Đáp phân tích)
Luồng đi y hệt như bước 2 của Part 1 với độ khó cao hơn.
```json
// Nhận question + audio từ server
{
  "type": "question",
  "partNumber": 3,
  "questionId": 32,
  "text": "Do you think digital reading habits are changing how we acquire knowledge?",
  "isFollowUp": false
}
// Trả transcript về server
{
  "type": "transcript",
  ...
}
```

---

### Bước 5: Kết thúc và chấm điểm
Khi chạy hết các câu Part 3, BE thông báo chuyển sang trạng thái chấm:
```json
// Server gửi
{ "type": "evaluating" }
```
FE phản hồi yêu cầu xác nhận nộp cho BE:
```json
// Client gửi
{ "type": "end_exam" }
```

> **Giai đoạn này FE hiển thị Loading screen (Đang phân tích kết quả). Phía BE sẽ bắt đầu chạy ConversationEngine để chấm.**

---

## 2. Luồng AI Chấm điểm (Scoring Flow)

Khi phiên kiểm tra ở trạng thái `EVALUATING`, backend (tại thư mục `service/ConversationEngine.java`) sẽ thực hiện các bước sau hoàn toàn độc lập với luồng chính để không block server:

1. **Tổng hợp Transcript (Synthesize):**
   Backend lấy mọi câu hỏi của Examiner và tất cả các responses (transcript) của thí sinh ghép thành 1 file text tổng hợp (Mô phỏng cả cuộc hội thoại thực tế từ Part 1 tới 3).
2. **Phase 1 (Chấm riêng biệt 4 tiêu chí):**
   Gọi song song tới LLM theo 4 luồng riêng biệt (`speaking_eval.txt`), cung cấp Rubric IELTS cụ thể để sinh ra điểm thô cho:
   - **FC (Fluency & Coherence):** Độ trôi chảy và Mạch lạc.
   - **LR (Lexical Resource):** Từ vựng.
   - **GRA (Grammatical Range & Accuracy):** Ngữ pháp.
   - **PR (Pronunciation):** Phát âm (Dựa trên transcript và có thể phân tích thêm từ STT meta trong tương lai).
3. **Phase 2 (Rule Engine - Xử phạt):**
   Chạy `SpeakingRuleEngine` để duyệt lại điểm từ LLM. Nếu thí sinh quá im lặng (transcript text quá ngắn) hoặc xin lỗi nhiều lần, hệ thống tự động bóp (cap) band điểm xuống. Kết quả là điểm Band Cuối cùng.
4. **Phase 3 (Sinh Feedback):**
   LLM đánh giá lại toàn bộ 4 tiêu chí trên cùng bản script hội thoại (`speaking_feedback.txt`) để sinh nhận xét chi tiết, nhược điểm và lộ trình luyện tập.
5. **Phase 4 (Trả Result):**
   Lưu vào Database (`SpeakingSubmission` => `COMPLETED` cùng `AiEvaluation`) và gửi bản tin WebSocket cuối cùng về FE.

### Data Response Kết quả trả về cho FE (`type: evaluation`)
```json
{
  "type": "evaluation",
  "final_band": 6.5,
  "fluency": 6.5,
  "vocabulary": 6.0,
  "grammar": 6.5,
  "pronunciation": 7.0,
  "feedback": {
    "summary": "You demonstrated a good capability of speaking smoothly on familiar topics but struggled slightly with abstract questions.",
    "strengths": [
      "Good use of compound sentences.",
      "Clear pronunciation with minor L1 interference."
    ],
    "areas_for_improvement": [
      "Limited vocabulary for abstract topics in Part 3.",
      "Hesitation when finding the right words."
    ],
    "next_steps": [
      "Practice speaking about less familiar topics in daily life.",
      "Learn and use more idiomatic expressions and phrasal verbs."
    ],
    "overall_strategy": "Try to expand your answers in Part 3 with concrete examples rather than general statements."
  },
  "transcript": "=== PART 1 ===\nQ: Let's talk about your hometown. Where do you live?\nA: I live in a small coastal city...\n..."
}
```

Nhận JSON này, FE tắt kết nối WebSocket và hiển thị Dashboard Báo Cáo. Mọi điểm tổng và nhận xét chi tiết đều là JSON tĩnh trong nhánh `feedback`.
