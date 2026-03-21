# Speaking Exam Pipeline — Luồng hoạt động chi tiết

## Tổng quan kiến trúc

```
speaking/
  model/    ActiveExamSession       — Trạng thái in-memory của 1 buổi thi
  service/  ExamSessionManager      — Quản lý các session đang active (ConcurrentHashMap)
  service/  ExaminerService         — Load đề từ DB, điều phối flow 3 parts
  service/  ElevenLabsService       — TTS câu hỏi → stream audio về client
  service/  ConversationEngine      — Chấm điểm transcript bằng LLM
  service/  SpeakingRuleEngine      — Tính band theo rule IELTS
  ws/       JwtHandshakeInterceptor — Validate JWT khi client kết nối WS
  ws/       SpeakingExamHandler     — WebSocket handler tại /ws/speaking/exam
  ws/       WebSocketConfig         — Đăng ký endpoint WS
```

---

## Luồng chi tiết

### Bước 1 — Client kết nối WebSocket

```
Client → ws://server/ws/speaking/exam?token=<JWT>
```

**`JwtHandshakeInterceptor.beforeHandshake()`**
- Đọc `?token=` từ URL query string
- Gọi `CustomJwtDecoder.decode(token)` để validate JWT
- Nếu hợp lệ → inject `userId` vào WS session attributes
- Nếu không hợp lệ → reject connection (trả về 401)

---

### Bước 2 — Client gửi `start_exam`

```json
{ "type": "start_exam", "testId": 123 }
```

**`SpeakingExamHandler.handleStartExam()`**

1. Lấy `userId` từ WS session attributes
2. **`ExamSessionManager.create()`** → tạo `ActiveExamSession`, lưu vào ConcurrentHashMap (key = WS session ID)
3. **`ExaminerService.loadExam()`**:
   - Query `TestStructureRepository.findByTestId()` → lấy danh sách `TestStructure` (section 1, 2, 3)
   - Duyệt `TestStructure` → `Stimulus` → `QuestionGroup` → `Question`
   - Phân loại vào queues của `ActiveExamSession`:

   | section | position | Vai trò | Lưu vào |
   |---------|----------|---------|---------|
   | 1 | bất kỳ | Câu hỏi Part 1 | `part1Queue` |
   | 2 | 0 | Câu dẫn Part 2 | `part2TransitionScript` |
   | 2 | > 0 | Cue card | `part2Question` |
   | 3 | 0 | Câu dẫn Part 3 | `part3TransitionScript` |
   | 3 | > 0 | Câu hỏi Part 3 | `part3Queue` |

4. **`ExaminerService.startPart1()`**:
   - Set `state = PART1_QUESTIONING`
   - Gọi `askNextQuestion()`

---

### Bước 3 — Server hỏi câu Part 1

**`ExaminerService.askNextQuestion()`** (state = PART1_QUESTIONING):

1. Kiểm tra `followUpQueue` → rỗng (lần đầu)
2. Poll câu MAIN đầu tiên từ `part1Queue`
3. **`loadFollowUps()`** → load `followUpQuestions` của câu MAIN vào `followUpQueue` (sắp xếp theo `position`)
4. **`sendQuestionEvent()`** → gửi về client:
   ```json
   { "type": "question", "partNumber": 1, "questionId": 5, "text": "Where do you live?", "isFollowUp": false }
   ```
5. **`ElevenLabsService.streamToClient()`**:
   - POST lên ElevenLabs API với text câu hỏi
   - Nhận MP3 bytes → chia thành chunks 4096 bytes
   - Gửi từng chunk:
     ```json
     { "type": "audio_chunk", "data": "<base64 mp3>" }
     ```
   - Khi xong:
     ```json
     { "type": "audio_end" }
     ```

---

### Bước 4 — Client gửi transcript Part 1

> Client dùng **AssemblyAI STT** ở phía mình để chuyển giọng nói → text, sau đó gửi lên server.

```json
{ "type": "transcript", "partNumber": 1, "questionId": 5, "text": "I live in Hanoi..." }
```

**`SpeakingExamHandler.handleTranscript()`** → **`ExaminerService.handleTranscript()`**:

1. `state = PART1_QUESTIONING` → `session.addPart1Transcript(questionId, text)` → lưu vào `List<TranscriptEntry>`
2. Gọi lại `askNextQuestion()`:
   - `followUpQueue` còn câu → hỏi follow-up (`isFollowUp: true`)
   - `followUpQueue` rỗng + `part1Queue` còn → poll MAIN tiếp, load follow-ups mới
   - **Cả hai rỗng → hết Part 1** → gọi `transitionToPart2()`

> Bước 3 và 4 lặp đi lặp lại cho đến khi hết toàn bộ câu Part 1.

---

### Bước 5 — Chuyển sang Part 2

**`ExaminerService.transitionToPart2()`**:

1. Set `state = TRANSITIONING_TO_PART2`
2. TTS câu dẫn `part2TransitionScript.content` → stream audio về client
3. Gửi event:
   ```json
   { "type": "show_cue_card", "duration": 60, "questionId": 10, "topic": "Describe a book you have read..." }
   ```
4. Set `state = PART2_PREPARATION`

> **Client** nhận `show_cue_card` → hiện cue card lên UI, tự chạy đếm ngược 60 giây.

---

### Bước 6 — Client bắt đầu nói Part 2

Sau 60 giây chuẩn bị, client gửi:

```json
{ "type": "start_speaking_part2" }
```

**`SpeakingExamHandler.handleStartPart2()`**:
- Kiểm tra `state == PART2_PREPARATION`
- **`ExaminerService.startPart2Speaking()`** → set `state = PART2_SPEAKING`

> Client tự đếm 120 giây. AssemblyAI STT tiếp tục chạy ở client trong khi user nói.

---

### Bước 7 — Client kết thúc Part 2

Hết 120 giây hoặc user im lặng quá lâu, client gửi:

```json
{ "type": "stop_speaking_part2", "text": "full transcript of part 2..." }
```

**`SpeakingExamHandler.handleStopPart2()`** → **`ExaminerService.stopPart2Speaking()`**:

1. Lưu transcript vào `session.part2Transcript`
2. Set `state = TRANSITIONING_TO_PART3`
3. TTS câu dẫn `part3TransitionScript` → stream audio
4. Set `state = PART3_QUESTIONING`
5. Gọi `askNextQuestion()` → bắt đầu hỏi Part 3

---

### Bước 8 — Part 3 (giống Part 1)

Logic giống hệt Part 1: MAIN → FOLLOW_UP → MAIN tiếp...

Transcript lưu vào `session.part3Transcripts`.

Khi hết tất cả câu Part 3, `askNextQuestion()` gọi **`endExam()`**:
1. Set `state = EVALUATING`
2. Gửi về client:
   ```json
   { "type": "evaluating" }
   ```

---

### Bước 9 — Client gửi `end_exam`

```json
{ "type": "end_exam" }
```

**`SpeakingExamHandler.handleEndExam()`** → **`runEvaluation()`** (chạy trong virtual thread):

1. `session.getFullTranscript()` → gộp transcript 3 parts:
   ```
   === PART 1 ===
   Q5: I live in Hanoi...
   Q6: I have lived there for 10 years...

   === PART 2 ===
   The book I want to talk about is...

   === PART 3 ===
   Q15: I think reading is important because...
   ```

2. **`ConversationEngine.evaluateFromExam(examSessionId, userId, fullTranscript)`**:
   - Tạo `SpeakingSubmission` với `status = PROCESSING` → lưu DB
   - Gọi LLM chấm **4 tiêu chí** (qua `PromptLoader.loadPromptWithSpeakingRubric()`):
     - **FC** — Fluency & Coherence
     - **LR** — Lexical Resource
     - **GRA** — Grammatical Range & Accuracy
     - **PR** — Pronunciation
   - **`SpeakingRuleEngine.calculateBands()`** → áp hard caps + soft penalties → tính `final_band`
   - Gọi LLM sinh feedback tổng hợp
   - Lưu `AiEvaluation` vào DB (`submission_type = SPEAKING`)
   - Update `SpeakingSubmission` → `status = COMPLETED`

3. Gửi kết quả về client:
   ```json
   {
     "type": "evaluation",
     "final_band": 7.0,
     "fluency": 7.0,
     "vocabulary": 6.5,
     "grammar": 7.0,
     "pronunciation": 6.5,
     "feedback": {
       "summary": "...",
       "strengths": [...],
       "areas_for_improvement": [...],
       "next_steps": [...]
     },
     "transcript": "=== PART 1 ===\n..."
   }
   ```

4. **`ExamSessionManager.remove()`** → xóa session khỏi memory

---

## Sơ đồ tổng hợp

```
Client                              Server
  |                                   |
  |──── WS connect + ?token=JWT ─────>| JwtHandshakeInterceptor
  |                                   |   └─ validate JWT, inject userId
  |                                   |
  |──── start_exam (testId) ─────────>| SpeakingExamHandler
  |                                   |   ├─ ExamSessionManager.create()
  |                                   |   ├─ ExaminerService.loadExam()  ← DB
  |                                   |   └─ ExaminerService.startPart1()
  |<─── { type: question } ───────────|       sendQuestionEvent()
  |<─── audio_chunk * N + audio_end ──|       ElevenLabsService.streamToClient()
  |                                   |
  |   ┌── (lặp cho đến hết Part 1) ──────────────────────────────────────────┐
  |   | ──── transcript ─────────────>|       ExaminerService.handleTranscript()
  |   | <─── question + audio ────────|         └─ askNextQuestion()          |
  |   └──────────────────────────────────────────────────────────────────────┘
  |                                   |
  |<─── audio (câu dẫn Part 2) ───────|   transitionToPart2()
  |<─── { type: show_cue_card } ──────|
  | [Client đếm 60s]                  |
  |──── start_speaking_part2 ────────>| ExaminerService.startPart2Speaking()
  | [Client đếm 120s, user nói]       |
  |──── stop_speaking_part2 (text) ──>| ExaminerService.stopPart2Speaking()
  |<─── audio (câu dẫn Part 3) ───────|
  |                                   |
  |   ┌── (lặp cho đến hết Part 3) ──────────────────────────────────────────┐
  |   | ──── transcript ─────────────>|       ExaminerService.handleTranscript()
  |   | <─── question + audio ────────|         └─ askNextQuestion()          |
  |   └──────────────────────────────────────────────────────────────────────┘
  |                                   |
  |<─── { type: evaluating } ─────────|   endExam()
  |──── end_exam ────────────────────>| SpeakingExamHandler.handleEndExam()
  |                                   |   └─ runEvaluation() [virtual thread]
  |                                   |       ├─ ConversationEngine.evaluateFromExam()
  |                                   |       │   ├─ LLM chấm FC, LR, GRA, PR
  |                                   |       │   ├─ SpeakingRuleEngine.calculateBands()
  |                                   |       │   └─ lưu AiEvaluation + SpeakingSubmission
  |<─── { type: evaluation, ... } ────|       └─ ExamSessionManager.remove()
```

---

## State machine của `ActiveExamSession`

```
IDLE
  └─► PART1_QUESTIONING       (startPart1)
        └─► TRANSITIONING_TO_PART2   (hết Part 1)
              └─► PART2_PREPARATION  (show_cue_card gửi xong)
                    └─► PART2_SPEAKING      (start_speaking_part2)
                          └─► TRANSITIONING_TO_PART3  (stop_speaking_part2)
                                └─► PART3_QUESTIONING  (hết transition)
                                      └─► EVALUATING   (hết Part 3)
                                            └─► COMPLETED  (sau khi lưu DB)
```

---

## WebSocket Message Protocol

### Client → Server

| type | payload | Mô tả |
|------|---------|-------|
| `start_exam` | `{ testId }` | Bắt đầu buổi thi |
| `transcript` | `{ partNumber, questionId, text }` | Gửi transcript sau khi nói xong 1 câu |
| `start_speaking_part2` | — | Báo hết 60s chuẩn bị, bắt đầu nói Part 2 |
| `stop_speaking_part2` | `{ text }` | Báo kết thúc Part 2, gửi toàn bộ transcript |
| `end_exam` | — | Kết thúc bài thi, yêu cầu chấm điểm |

### Server → Client

| type | payload | Mô tả |
|------|---------|-------|
| `question` | `{ partNumber, questionId, text, isFollowUp }` | Câu hỏi tiếp theo |
| `audio_chunk` | `{ data: base64 }` | Chunk audio MP3 từ ElevenLabs |
| `audio_end` | — | Kết thúc stream audio |
| `show_cue_card` | `{ duration: 60, questionId, topic }` | Hiện cue card Part 2 |
| `evaluating` | — | Báo đang chấm điểm |
| `evaluation` | `{ final_band, fluency, vocabulary, grammar, pronunciation, feedback, transcript }` | Kết quả chấm |
| `error` | `{ message }` | Lỗi xảy ra |
