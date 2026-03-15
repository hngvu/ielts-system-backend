package io.gsp26se16.moni.vocab.util;

import java.time.LocalDateTime;

public class SM2Calculator {

    public record SM2Result(double easeFactor, int repetitions, int interval, LocalDateTime nextReviewAt) {}

    public static SM2Result calculate(double easeFactor, int repetitions, int interval, int quality) {
        quality = Math.max(0, Math.min(5, quality));

        int newRepetitions;
        int newInterval;

        if (quality >= 3) {
            if (repetitions == 0) newInterval = 1;
            else if (repetitions == 1) newInterval = 6;
            else newInterval = (int) Math.round(interval * easeFactor);
            newRepetitions = repetitions + 1;
        } else {
            newRepetitions = 0;
            newInterval = 1;
        }

        double newEaseFactor = easeFactor + (0.1 - (5 - quality) * (0.08 + 0.02 * (5 - quality)));
        newEaseFactor = Math.max(1.3, newEaseFactor);

        return new SM2Result(
                newEaseFactor, newRepetitions, newInterval, LocalDateTime.now().plusDays(newInterval));
    }
}
