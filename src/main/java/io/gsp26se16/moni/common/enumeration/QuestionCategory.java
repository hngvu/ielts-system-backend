package io.gsp26se16.moni.common.enumeration;

/**
 * Phân loại bản chất/vai trò của câu hỏi trong Speaking.
 *
 * <ul>
 *   <li><b>MAIN</b> — Câu hỏi chính (topic question)</li>
 *   <li><b>FOLLOW_UP</b> — Câu hỏi phụ, thuộc về một câu MAIN</li>
 * </ul>
 *
 * Mở rộng sau: TRANSITION (câu dẫn chuyển đoạn), OPENING (câu chào hỏi), v.v.
 */
public enum QuestionCategory {
    MAIN,
    FOLLOW_UP
}
