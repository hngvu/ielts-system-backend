package io.gsp26se16.moni.placement.util;

public final class BandScoreUtil {

    private BandScoreUtil() {}

    // Official IELTS Academic Reading band conversion (40 questions)
    private static final double[] READING_BANDS = {
        0.0, // 0 correct
        0.0, // 1
        0.0, // 2
        0.0, // 3
        0.0, // 4
        0.0, // 5
        2.5, // 6
        3.0, // 7
        3.0, // 8
        3.5, // 9
        3.5, // 10
        4.0, // 11
        4.0, // 12
        4.0, // 13
        4.5, // 14
        4.5, // 15
        5.0, // 16
        5.0, // 17
        5.0, // 18
        5.5, // 19
        5.5, // 20
        5.5, // 21
        6.0, // 22
        6.0, // 23
        6.5, // 24
        6.5, // 25
        6.5, // 26
        7.0, // 27
        7.0, // 28
        7.0, // 29
        7.5, // 30
        7.5, // 31
        8.0, // 32
        8.0, // 33
        8.5, // 34
        8.5, // 35
        9.0, // 36
        9.0, // 37
        9.0, // 38
        9.0, // 39
        9.0, // 40
    };

    // Official IELTS Listening band conversion (40 questions)
    private static final double[] LISTENING_BANDS = {
        0.0, // 0 correct
        0.0, // 1
        0.0, // 2
        0.0, // 3
        0.0, // 4
        0.0, // 5
        2.5, // 6
        3.0, // 7
        3.0, // 8
        3.5, // 9
        3.5, // 10
        4.0, // 11
        4.0, // 12
        4.0, // 13
        4.5, // 14
        4.5, // 15
        5.0, // 16
        5.0, // 17
        5.0, // 18
        5.5, // 19
        5.5, // 20
        5.5, // 21
        6.0, // 22
        6.0, // 23
        6.0, // 24
        6.5, // 25
        6.5, // 26
        7.0, // 27
        7.0, // 28
        7.5, // 29
        7.5, // 30
        8.0, // 31
        8.0, // 32
        8.0, // 33
        8.5, // 34
        8.5, // 35
        9.0, // 36
        9.0, // 37
        9.0, // 38
        9.0, // 39
        9.0, // 40
    };

    public static double readingBand(int correct, int totalQuestions) {
        if (totalQuestions <= 0) return 0.0;
        if (totalQuestions == 40) {
            int idx = Math.max(0, Math.min(correct, 40));
            return READING_BANDS[idx];
        }
        // Proportional conversion for non-standard question counts
        int scaled = (int) Math.round((double) correct / totalQuestions * 40);
        scaled = Math.max(0, Math.min(scaled, 40));
        return READING_BANDS[scaled];
    }

    public static double listeningBand(int correct, int totalQuestions) {
        if (totalQuestions <= 0) return 0.0;
        if (totalQuestions == 40) {
            int idx = Math.max(0, Math.min(correct, 40));
            return LISTENING_BANDS[idx];
        }
        int scaled = (int) Math.round((double) correct / totalQuestions * 40);
        scaled = Math.max(0, Math.min(scaled, 40));
        return LISTENING_BANDS[scaled];
    }

    public static double overallBand(double reading, double listening, double writing, double speaking) {
        double avg = (reading + listening + writing + speaking) / 4.0;
        return roundToHalf(avg);
    }

    public static double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    public static boolean isValidBand(Double band) {
        if (band == null) return false;
        if (band < 0.0 || band > 9.0) return false;
        return band * 2 == Math.floor(band * 2);
    }
}
