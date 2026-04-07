# Hướng Dẫn Nhập Đề IELTS Speaking (Pipeline Mới)

Tài liệu này hướng dẫn cách cấu trúc JSON payload để tạo/import một đề thi IELTS Speaking hoàn chỉnh, tương thích với cơ chế hỏi đáp tự động (AI Examiner) và cơ chế `MAIN`/`FOLLOW_UP` hiện tại.

---

## 1. Cấu trúc Tổng quan (Hierarchy)

Đề thi Speaking được tổ chức theo cấu trúc chuẩn của hệ thống:

`Test` ➜ `TestStructure` (Các Part) ➜ `Stimulus` (Nội dung Part) ➜ `QuestionGroup` ➜ `Question`.

Trong đó, IELTS Speaking có 3 phần. Bạn cần tạo **3 `TestStructure`**, mỗi cái tương ứng với một section:
- `section: 1` ➜ Part 1 (Introduction & Interview)
- `section: 2` ➜ Part 2 (Long Turn / Cue Card)
- `section: 3` ➜ Part 3 (Two-way Discussion)

---

## 2. Quy tắc cấu hình Câu hỏi (Questions)

Để `ExaminerService` có thể phân loại và hỏi đúng thứ tự, các câu hỏi trong mục `questions` cần tuân thủ các quy tắc sau, dựa trên trường `position` và `questionCategory`:

### A. PART 1: Introduction & Interview
- **Câu hỏi chính**: 
  - `questionCategory: "MAIN"`
  - `position`: Đánh số thứ tự (ví dụ 1, 2, 3...)
- **Câu hỏi phụ (mở rộng thêm)**:
  - `questionCategory: "FOLLOW_UP"`
  - `parentQuestionPosition`: Bằng với `position` của câu hỏi MAIN mà nó thuộc về.

### B. PART 2: Long Turn (Cue Card)
- **Câu dẫn chuyển đoạn (Transition Script)**: Bắt buộc phải có để báo hiệu sang Part 2.
  - `position: 0` (Backend tự nhận diện position 0 ở Part 2 là câu dẫn chuyển từ Part 1 sang).
  - Ví dụ: *"Now, I'm going to give you a topic..."*
- **Cue Card (Đề bài Part 2)**:
  - `position: 1`
  - `questionCategory: "MAIN"`
  - Nội dung text ở đây chính là nội dung trên Cue Card hiển thị cho thí sinh.

### C. PART 3: Two-way Discussion
- **Câu dẫn chuyển đoạn (Transition Script)**: Bắt buộc phải có để báo hiệu chuyển từ Part 2 sang.
  - `position: 0` (Backend tự nhận diện position 0 ở Part 3 là câu dẫn từ Part 2 sang).
  - Ví dụ: *"We've been talking about a memorable trip. Now, I'd like to discuss with you one or two more general questions related to this."*
- **Câu hỏi chính & Câu hỏi phụ**:
  - Tương tự như Part 1. Sử dụng `questionCategory: "MAIN"` và `"FOLLOW_UP"` kết hợp `parentQuestionPosition`.

---

## 3. Ví dụ Payload Import JSON hoàn chỉnh (POST `/api/tests/import`)

Dưới đây là một JSON mẫu để import trọn bộ 1 đề Speaking hoàn chỉnh với cả main và follow-up questions:

```json
{
  "title": "IELTS Speaking Practice Test 01",
  "testType": "PRACTICE",
  "skill": "SPEAKING",
  "estimatedTime": 15,
  "structures": [
    {
      "section": 1,
      "stimulus": {
        "title": "Part 1: Hometown",
        "questionGroups": [
          {
            "instruction": "Hometown Topic",
            "questions": [
              {
                "position": 1,
                "content": "Let's talk about your hometown. Where is your hometown?",
                "questionCategory": "MAIN"
              },
              {
                "position": 2,
                "content": "What is there for a foreigner to do or see in your hometown?",
                "questionCategory": "FOLLOW_UP",
                "parentQuestionPosition": 1
              },
              {
                "position": 3,
                "content": "Is there good public transportation in your hometown?",
                "questionCategory": "MAIN"
              }
            ]
          }
        ]
      }
    },
    {
      "section": 2,
      "stimulus": {
        "title": "Part 2: Describe a memorable trip",
        "questionGroups": [
          {
            "instruction": "Cue Card",
            "questions": [
              {
                "position": 0,
                "content": "Now, I'm going to give you a topic and I'd like you to talk about it for one to two minutes. Before you talk, you'll have one minute to think about what you're going to say. Here is your topic: Describe a memorable trip you took.",
                "questionCategory": "MAIN"
              },
              {
                "position": 1,
                "content": "Describe a memorable trip you took.\nYou should say:\n- where you went\n- who you went with\n- what you did there\nAnd explain why it was so memorable.",
                "questionCategory": "MAIN"
              }
            ]
          }
        ]
      }
    },
    {
      "section": 3,
      "stimulus": {
        "title": "Part 3: Trips and Tourism",
        "questionGroups": [
          {
            "instruction": "Discussion",
            "questions": [
              {
                "position": 0,
                "content": "We've been talking about a memorable trip, and I'd like to ask you some more general questions about traveling.",
                "questionCategory": "MAIN"
              },
              {
                "position": 1,
                "content": "Do you think tourism is always beneficial for a country?",
                "questionCategory": "MAIN"
              },
              {
                "position": 2,
                "content": "What negative impacts can it have on local communities?",
                "questionCategory": "FOLLOW_UP",
                "parentQuestionPosition": 1
              },
              {
                "position": 3,
                "content": "How do you think traveling will change in the next 50 years?",
                "questionCategory": "MAIN"
              }
            ]
          }
        ]
      }
    }
  ]
}
```

## 4. Giải thích logic hoạt động của ExaminerService với File này

1. **Khi vào Part 1**: AI bắt đầu đọc câu vị trí số 1 (`Let's talk about...`). Sau khi thí sinh trả lời, AI check xem câu hiện tại có follow-up nào không. Do câu ở position 2 tham chiếu đến câu 1 (`parentQuestionPosition: 1`), AI sẽ kéo câu 2 (follow-up) ra để hỏi tiếp. Hỏi xong câu 2 sẽ qua câu 3.
2. **Khi chuyển từ Part 1 sang 2**: AI đọc câu `position: 0` của `section 2` (Transition Script). Sau đó, trên màn hình Client của người dùng sẽ hiển thị Cue Card (câu `position: 1`).
3. **Khi chuyển từ Part 2 sang 3**: Tương tự, AI sẽ đọc câu `position: 0` của `section 3` để mồi chuyển đoạn, sau đó mới hỏi vào các câu hỏi chính thức (câu số 1, 2, 3...) và các câu follow-up của Part 3.
