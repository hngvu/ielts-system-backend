package io.gsp26se16.moni.common.enumeration;

/**
 * State machine cho buổi thi Speaking.
 *
 * Flow:
 * IDLE
 * → PART1_QUESTIONING (server hỏi tuần tự MAIN + FOLLOW_UP)
 * → TRANSITIONING_TO_PART2 (server TTS câu dẫn Part 2)
 * → PART2_PREPARATION (client đếm 60s, hiện cue card)
 * → PART2_SPEAKING (client đếm 120s, user nói)
 * → TRANSITIONING_TO_PART3 (server TTS câu dẫn Part 3)
 * → PART3_QUESTIONING (server hỏi tuần tự MAIN + FOLLOW_UP)
 * → EVALUATING (ConversationEngine chấm điểm)
 * → COMPLETED
 */
public enum ExamState {
    IDLE,
    PART1_QUESTIONING,
    TRANSITIONING_TO_PART2,
    PART2_PREPARATION,
    PART2_SPEAKING,
    TRANSITIONING_TO_PART3,
    PART3_QUESTIONING,
    EVALUATING,
    COMPLETED
}
