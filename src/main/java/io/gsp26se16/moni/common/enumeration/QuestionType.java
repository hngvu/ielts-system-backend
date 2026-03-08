package io.gsp26se16.moni.common.enumeration;

public enum QuestionType {
    MCQ, // Multiple Choice Question (Trắc nghiệm 1 đáp án)
    MCQ_MULTIPLE, // Trắc nghiệm nhiều đáp án
    TFNG, // True / False / Not Given
    YNNG, // Yes / No / Not Given
    MATCHING, // Nối đáp án (Matching headings, Matching features)

    // Nhóm điền từ
    FILL_IN_THE_BLANK, // Điền từ vào chỗ trống (Form, Note, Table, Flow-chart)
    SHORT_ANSWER, // Trả lời câu hỏi ngắn

    // Nhóm Tự luận (Speaking & Writing)
    ESSAY,
    SPEAKING_PROMPT
}
