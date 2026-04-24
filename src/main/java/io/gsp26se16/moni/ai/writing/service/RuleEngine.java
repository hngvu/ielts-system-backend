package io.gsp26se16.moni.ai.writing.service;

import java.util.*;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RuleEngine {

    // =====================================================
    // ================= HARD CAP RULES ====================
    // =====================================================

    private static final Map<String, HardRule> HARD_RULES = Map.ofEntries(

            // ---------- TASK 1 (TA) ----------

            // Sai data nặng → hiếm khi > 5.5
            Map.entry("data_misinterpretation", new HardRule(Map.of("TA", 5.0), 5.5)),
            Map.entry("no_overview", new HardRule(Map.of("TA", 6.0), 6.5)),
            Map.entry("irrelevant_data", new HardRule(Map.of("TA", 6.0), null)),

            // inaccurate_data_reporting: severity-aware dispatch in applyAllRules()
            // severity "major"    → TA ≤ 4, overall ≤ 5.0
            // severity "moderate" → TA ≤ 5, overall ≤ 5.5
            // severity "minor"    → no hard cap
            Map.entry("inaccurate_data_reporting", new HardRule(Map.of("TA", 5.0), 5.5)),
            Map.entry("missing_key_features", new HardRule(Map.of("TA", 6.0), 6.5)),
            Map.entry("incomplete_coverage", new HardRule(Map.of("TA", 6.0), null)),

            // Wrong data source: essay describes correct chart TYPE but wrong data
            Map.entry("wrong_data_source", new HardRule(Map.of("TA", 3.0), 5.5)),

            // No data reference: Task 1 essay with no numbers/percentages/trends at all
            Map.entry("no_data_reference", new HardRule(Map.of("TA", 2.0), 4.5)),

            // ---------- TASK 2 (TR) ----------

            Map.entry("no_position_throughout", new HardRule(Map.of("TR", 5.0), 5.5)),
            Map.entry("no_position", new HardRule(Map.of("TR", 5.0), 5.5)),
            Map.entry("unclear_position_progression", new HardRule(Map.of("TR", 5.0), 6.0)),
            Map.entry("contradictory_position", new HardRule(Map.of("TR", 5.0), 6.0)),
            Map.entry("mixed_task", new HardRule(Map.of("TR", 5.0), 6.0)),
            Map.entry("irrelevant_content_tr", new HardRule(Map.of("TR", 6.0), null)),
            Map.entry("tr_irrelevant_content", new HardRule(Map.of("TR", 6.0), null)),

            // weak_argument_depth: severity-aware dispatch in applyAllRules()
            // severity "serious" → TR ≤ 6
            // default            → TR ≤ 7
            Map.entry("weak_argument_depth", new HardRule(Map.of("TR", 7.0), null)),

            // ---------- OFF-TOPIC / MISMATCH (CRITICAL) ----------

            // Completely off-topic: essay topic ≠ question topic → band 1
            Map.entry("completely_off_topic", new HardRule(Map.of("TR", 1.0, "TA", 1.0), 2.0)),

            // Task mismatch: wrote Task 2 essay for Task 1, or vice versa
            Map.entry("task_mismatch", new HardRule(Map.of("TA", 2.0, "TR", 2.0), 4.5)),

            // ---------- SPAM ----------

            Map.entry(
                    "spam_or_gibberish",
                    new HardRule(Map.of("TR", 1.0, "TA", 1.0, "CC", 1.0, "LR", 1.0, "GRA", 1.0), 1.0)),

            // ---------- GRA ----------
            Map.entry("too_many_errors", new HardRule(Map.of("GRA", 6.0), 6.5)),

            // ---------- CC ----------
            Map.entry("logic_break_significant", new HardRule(Map.of("CC", 6.0), null)));

    // =====================================================
    // ================= SOFT PENALTIES ====================
    // =====================================================

    private static final Map<String, SoftRule> SOFT_RULES = Map.ofEntries(

            // ---------- TA (Task 1) ----------
            Map.entry("weak_overview", new SoftRule("TA", 0.25)),
            Map.entry("missing_key_extreme", new SoftRule("TA", 0.25)),
            Map.entry("limited_comparison", new SoftRule("TA", 0.25)),
            Map.entry("mechanical_listing", new SoftRule("TA", 0.25)),

            // ---------- TR (Task 2) ----------
            Map.entry("partial_task_addressing", new SoftRule("TR", 0.5)),
            Map.entry("insufficient_development", new SoftRule("TR", 0.25)),
            Map.entry("no_clear_position", new SoftRule("TR", 0.25)),
            Map.entry("missing_clear_position", new SoftRule("TR", 0.25)),

            // ---------- CC ----------
            Map.entry("weak_cohesion", new SoftRule("CC", 0.25)),
            Map.entry("mixed_paragraph_focus", new SoftRule("CC", 0.25)),
            Map.entry("logic_break_minor", new SoftRule("CC", 0.25)),

            // ---------- LR ----------
            Map.entry("limited_range", new SoftRule("LR", 0.25)),
            Map.entry("repetition", new SoftRule("LR", 0.25)),
            Map.entry("awkward_collocation", new SoftRule("LR", 0.25)),
            Map.entry("inaccurate_word_choice", new SoftRule("LR", 0.25)),

            // ---------- GRA ----------
            Map.entry("frequent_minor_errors", new SoftRule("GRA", 0.25)),
            Map.entry("limited_sentence_variety", new SoftRule("GRA", 0.25)));

    // =====================================================
    // ================= APPLY RULES =======================
    // =====================================================

    public RuleResult applyAllRules(Map<String, Double> originalBands, Map<String, Violation> violations) {

        Map<String, Double> adjusted = new HashMap<>(originalBands);
        Double overallCap = null;

        Set<String> hardCappedCriteria = new HashSet<>();
        List<String> appliedHardRules = new ArrayList<>();

        // ========== STEP 1: HARD CAPS ==========

        for (Map.Entry<String, Violation> entry : violations.entrySet()) {

            if (!entry.getValue().active()) continue;

            String violationKey = entry.getKey();

            // --- Severity dispatch: logic_break ---
            if ("logic_break".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("significant".equals(severity)) {
                    violationKey = "logic_break_significant";
                } else {
                    continue; // minor → soft rule only
                }
            }

            // --- Criterion dispatch: irrelevant_content ---
            if ("irrelevant_content".equals(violationKey)) {
                String criterion = entry.getValue().criterion();
                if ("TR".equals(criterion)) {
                    violationKey = "irrelevant_content_tr";
                } else {
                    continue; // CC scope → no hard cap
                }
            }

            // --- Severity dispatch: inaccurate_data_reporting ---
            if ("inaccurate_data_reporting".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("major".equals(severity)) {
                    appliedHardRules.add("inaccurate_data_reporting_major");
                    adjusted.computeIfPresent("TA", (k, v) -> Math.min(v, 4.0));
                    hardCappedCriteria.add("TA");
                    overallCap = (overallCap == null) ? 5.0 : Math.min(overallCap, 5.0);
                    continue;
                } else if ("minor".equals(severity)) {
                    continue; // minor → no hard cap
                }
                // "moderate" → fall through to default HardRule
            }

            // --- Severity dispatch: weak_argument_depth ---
            if ("weak_argument_depth".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("serious".equals(severity)) {
                    appliedHardRules.add("weak_argument_depth_serious");
                    adjusted.computeIfPresent("TR", (k, v) -> Math.min(v, 6.0));
                    hardCappedCriteria.add("TR");
                    continue;
                }
                // default → fall through to HardRule (TR ≤ 7)
            }

            HardRule rule = HARD_RULES.get(violationKey);
            if (rule == null) continue;

            appliedHardRules.add(violationKey);

            for (Map.Entry<String, Double> capEntry : rule.criterionCaps.entrySet()) {
                String criterion = capEntry.getKey();
                Double maxBand = capEntry.getValue();
                if (adjusted.containsKey(criterion)) {
                    adjusted.put(criterion, Math.min(adjusted.get(criterion), maxBand));
                    hardCappedCriteria.add(criterion);
                }
            }

            if (rule.overallCap != null) {
                overallCap = (overallCap == null) ? rule.overallCap : Math.min(overallCap, rule.overallCap);
            }
        }

        // ========== STEP 2: EXAMINER BIAS ==========
        // When TA/TR is very low, real examiners also score CC/LR/GRA lower
        // because off-topic or irrelevant content makes coherence & vocabulary
        // assessment less meaningful.

        Double ta = adjusted.get("TA");
        Double tr = adjusted.get("TR");
        double taskScore = ta != null ? ta : tr != null ? tr : 9.0;

        if (taskScore <= 2.0) {
            // Extremely off-topic / gibberish → cap all criteria hard
            adjusted.computeIfPresent("CC", (k, v) -> Math.min(v, 4.0));
            adjusted.computeIfPresent("LR", (k, v) -> Math.min(v, 4.0));
            adjusted.computeIfPresent("GRA", (k, v) -> Math.min(v, 5.0));
        } else if (taskScore <= 3.0) {
            adjusted.computeIfPresent("CC", (k, v) -> Math.min(v, 6.0));
            adjusted.computeIfPresent("LR", (k, v) -> Math.min(v, 6.0));
            adjusted.computeIfPresent("GRA", (k, v) -> Math.min(v, 7.0));
        } else if (taskScore <= 4.0) {
            adjusted.computeIfPresent("CC", (k, v) -> Math.min(v, 7.0));
            adjusted.computeIfPresent("LR", (k, v) -> Math.min(v, 7.0));
        }

        // ========== STEP 3: SOFT PENALTIES ==========

        Map<String, Double> penaltyTracker = new HashMap<>();

        for (Map.Entry<String, Violation> entry : violations.entrySet()) {

            if (!entry.getValue().active()) continue;

            String violationKey = entry.getKey();

            if ("logic_break".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("significant".equals(severity)) {
                    continue; // handled in hard caps
                }
                violationKey = "logic_break_minor";
            }

            if ("irrelevant_content".equals(violationKey)) {
                continue; // hard cap or no soft rule
            }

            SoftRule rule = SOFT_RULES.get(violationKey);
            if (rule == null) continue;

            String criterion = rule.criterion;

            if (!adjusted.containsKey(criterion)) continue;
            if (hardCappedCriteria.contains(criterion)) continue;

            double currentPenalty = penaltyTracker.getOrDefault(criterion, 0.0);
            if (currentPenalty >= 0.5) continue;

            double penaltyToApply = Math.min(rule.penalty, 0.5 - currentPenalty);
            double newBand = adjusted.get(criterion) - penaltyToApply;

            adjusted.put(criterion, round2(newBand));
            penaltyTracker.put(criterion, currentPenalty + penaltyToApply);
        }

        // ========== STEP 4: ROUND CRITERIA TO WHOLE BANDS ==========
        // IELTS: individual criterion scores are whole numbers.
        for (Map.Entry<String, Double> e : adjusted.entrySet()) {
            adjusted.put(e.getKey(), (double) Math.round(e.getValue()));
        }

        return new RuleResult(adjusted, overallCap, appliedHardRules);
    }

    // =====================================================
    // ================= FINAL CALCULATION =================
    // =====================================================

    public double calculateFinalBand(Map<String, Double> adjustedBands, Double overallCap) {
        // If TA/TR ≤ 2 (off-topic/gibberish), cap overall severely
        Double taBand = adjustedBands.getOrDefault("TA", adjustedBands.get("TR"));
        if (taBand != null && taBand <= 2.0) {
            // Average all but ensure result reflects near-failure
            double average = adjustedBands.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            if (overallCap != null) {
                average = Math.min(average, overallCap);
            }
            return ieltsRounding(average);
        }

        double average = adjustedBands.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(5.0);

        if (overallCap != null) {
            average = Math.min(average, overallCap);
        }

        return ieltsRounding(average);
    }

    public double ieltsRounding(double score) {
        double floor = Math.floor(score);
        double fraction = score - floor;

        if (fraction < 0.25) return floor;
        if (fraction < 0.75) return floor + 0.5;
        return floor + 1.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // =====================================================
    // ================= DATA STRUCTURES ===================
    // =====================================================

    public record Violation(
            boolean active, String location, String evidence, String reason, String severity, String criterion) {
        public Violation(boolean active, String location, String evidence, String reason) {
            this(active, location, evidence, reason, "", "");
        }
    }

    private static class HardRule {
        Map<String, Double> criterionCaps;
        Double overallCap;

        HardRule(Map<String, Double> criterionCaps, Double overallCap) {
            this.criterionCaps = criterionCaps;
            this.overallCap = overallCap;
        }
    }

    private static class SoftRule {
        String criterion;
        double penalty;

        SoftRule(String criterion, double penalty) {
            this.criterion = criterion;
            this.penalty = penalty;
        }
    }

    public record RuleResult(Map<String, Double> adjustedBands, Double overallCap, List<String> appliedHardRules) {}
}
