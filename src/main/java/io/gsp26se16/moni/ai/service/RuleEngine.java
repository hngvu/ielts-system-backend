package io.gsp26se16.moni.ai.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RuleEngine {

    // ===== HARD CAP RULES =====
    private static final Map<String, Map<String, Double>> HARD_CAP_RULES = new HashMap<>();

    static {
        // Task 1 Rules
        HARD_CAP_RULES.put("irrelevant_data", Map.of("TA_max", 6.0, "overall_max", 6.5));
        HARD_CAP_RULES.put("no_overview", Map.of("TA_max", 6.0, "overall_max", 6.5));
        HARD_CAP_RULES.put("data_misinterpretation", Map.of("TA_max", 5.5, "overall_max", 6.0));

        // Task 2 Rules
        HARD_CAP_RULES.put("no_position", Map.of("TR_max", 5.0, "overall_max", 5.5));
        HARD_CAP_RULES.put("contradictory_position", Map.of("TR_max", 5.5, "overall_max", 6.0));
        HARD_CAP_RULES.put("irrelevant_content", Map.of("TR_max", 6.0, "overall_max", 6.5));
        HARD_CAP_RULES.put("mixed_task", Map.of("TR_max", 5.5, "overall_max", 6.0));
    }

    // ===== SOFT PENALTY RULES =====
    private static final Map<String, SoftRule> SOFT_RULES = new HashMap<>();

    static {
        SOFT_RULES.put("weak_overview", new SoftRule("TA", 0.25, "Overview is present but generic"));
        SOFT_RULES.put("missing_key_extreme", new SoftRule("TA", 0.25, "A dominant extreme is not highlighted"));
        SOFT_RULES.put("limited_comparison", new SoftRule("TA", 0.25, "Limited synthesis across categories"));
        SOFT_RULES.put(
                "mechanical_listing", new SoftRule("TA", 0.25, "Data listed mechanically with limited synthesis"));
        SOFT_RULES.put("over_detailing", new SoftRule("CC", 0.25, "Too much detail reduces clarity and focus"));
        SOFT_RULES.put("weak_grouping", new SoftRule("CC", 0.25, "Paragraphs not grouped logically by trend"));
        SOFT_RULES.put(
                "repetitive_sentence_openings",
                new SoftRule("GRA", 0.25, "Repetitive sentence openings reduce grammatical range"));
        SOFT_RULES.put("weak_position", new SoftRule("TR", 0.25, "Position is unclear or partially inconsistent"));
        SOFT_RULES.put("underdeveloped_ideas", new SoftRule("TR", 0.25, "Main ideas lack depth or examples"));
        SOFT_RULES.put("limited_cohesion", new SoftRule("TR", 0.25, "Essay lacks clear structure or progression"));
    }

    // ===== GRA CEILING RULES =====
    private static final Map<String, Double> GRA_CEILING_RULES = Map.of(
            "capitalization_errors", 7.0,
            "systematic_punctuation_errors", 7.0,
            "run_on_sentences", 7.0);

    public RuleResult applyAllRules(Map<String, Double> bands, Map<String, Violation> violations) {

        Map<String, Double> capped = new HashMap<>(bands);
        Double overallCap = null;

        var appliedHard = new java.util.ArrayList<HardCapTrace>();
        var appliedSoft = new java.util.ArrayList<SoftPenaltyTrace>();

        // Track criteria already hard-capped
        var hardCappedCriteria = new java.util.HashSet<String>();

        // =========================
        // APPLY HARD CAPS
        // =========================
        for (var entry : violations.entrySet()) {
            String violationKey = entry.getKey();
            Violation violation = entry.getValue();

            if (!violation.isActive()) continue;

            Map<String, Double> rule = HARD_CAP_RULES.get(violationKey);
            if (rule == null) continue;

            Map<String, Double> caps = new HashMap<>();

            for (var ruleEntry : rule.entrySet()) {

                String key = ruleEntry.getKey();
                Double maxValue = ruleEntry.getValue();

                if (!key.endsWith("_max")) continue;

                String criterion = key.replace("_max", "");
                caps.put(criterion, maxValue);

                if (!criterion.equals("overall") && capped.containsKey(criterion)) {
                    capped.put(criterion, Math.min(capped.get(criterion), maxValue));
                    hardCappedCriteria.add(criterion);
                }

                if (criterion.equals("overall")) {
                    overallCap = (overallCap == null)
                            ? maxValue
                            : Math.min(overallCap, maxValue);
                }
            }

            appliedHard.add(new HardCapTrace(
                    violationKey,
                    caps,
                    violation.getLocation(),
                    violation.getEvidence(),
                    violation.getReason()
            ));
        }

        // =========================
        // APPLY SOFT PENALTIES
        // =========================

        // Track cumulative penalty per criterion
        Map<String, Double> penaltyTracker = new HashMap<>();

        for (var entry : violations.entrySet()) {

            String violationKey = entry.getKey();
            Violation violation = entry.getValue();

            if (!violation.isActive()) continue;

            SoftRule rule = SOFT_RULES.get(violationKey);
            if (rule == null) continue;

            String criterion = rule.criterion;

            // Skip if already hard capped (avoid double punishment)
            if (hardCappedCriteria.contains(criterion)) continue;

            if (!capped.containsKey(criterion)) continue;

            double currentPenalty = penaltyTracker.getOrDefault(criterion, 0.0);

            // Max soft penalty per criterion = 0.5
            if (currentPenalty >= 0.5) continue;

            double penaltyToApply = Math.min(rule.penalty, 0.5 - currentPenalty);

            double oldValue = capped.get(criterion);
            double newValue = oldValue - penaltyToApply;

            capped.put(criterion, newValue);
            penaltyTracker.put(criterion, currentPenalty + penaltyToApply);

            appliedSoft.add(new SoftPenaltyTrace(
                    criterion,
                    violationKey,
                    violation.getLocation(),
                    violation.getEvidence(),
                    rule.explain,
                    penaltyToApply
            ));
        }

        // =========================
        // APPLY OVERALL CAP
        // =========================
        if (overallCap != null) {
            final double finalOverallCap =  overallCap;
            capped.replaceAll((k, v) -> Math.min(v, finalOverallCap));
        }

        return new RuleResult(capped, overallCap, appliedHard, appliedSoft);
    }


    public double applyGraCeiling(double graBand, List<String> graViolations) {
        double capped = graBand;

        if (graViolations != null) {
            for (String violation : graViolations) {
                Double ceiling = GRA_CEILING_RULES.get(violation);
                if (ceiling != null) {
                    capped = Math.min(capped, ceiling);
                }
            }
        }

        return capped;
    }

    public double ieltsRounding(double score) {
        double fractional = score - Math.floor(score);

        if (fractional < 0.25) {
            return Math.floor(score);
        } else if (fractional < 0.75) {
            return Math.floor(score) + 0.5;
        } else {
            return Math.floor(score) + 1.0;
        }
    }

    // Helper classes
    public record Violation(boolean active, String location, String evidence, String reason) {
        public boolean isActive() {
            return active;
        }

        public String getLocation() {
            return location;
        }

        public String getEvidence() {
            return evidence;
        }

        public String getReason() {
            return reason;
        }
    }

    private record SoftRule(String criterion, double penalty, String explain) {}

    public record HardCapTrace(
            String violation, Map<String, Double> caps, String location, String evidence, String reason) {}

    public record SoftPenaltyTrace(
            String criterion, String violation, String location, String evidence, String reason, double penalty) {}

    public record RuleResult(
            Map<String, Double> cappedBands,
            Double overallCap,
            List<HardCapTrace> appliedHard,
            List<SoftPenaltyTrace> appliedSoft) {}
}
