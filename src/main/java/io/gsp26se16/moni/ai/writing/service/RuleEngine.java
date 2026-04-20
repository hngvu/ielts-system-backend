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
    //
    // CHANGE LOG vs previous version:
    //
    // [RENAMED] "no_position"          → kept, but now also mapped via "no_position_throughout"
    //                                     (phase2_tr emits "no_position_throughout"; was silently lost)
    //
    // [RENAMED] "contradictory_position"→ kept as alias; canonical key now "unclear_position_progression"
    //                                     (phase2_ta emits "unclear_position_progression")
    //
    // [RENAMED] "tr_irrelevant_content" → kept as alias; canonical key now "irrelevant_content_tr"
    //                                     phase2_ta / phase2_tr both emit "irrelevant_content"
    //                                     CC also emits "irrelevant_content" (different criterion/scope)
    //                                     → resolved by criterion-scoped dispatch in applyAllRules()
    //
    // [ADDED]   "weak_argument_depth"   → Hard cap TR ≤ 7  (phase2_ta: "This blocks Band 8+")
    //
    // [ADDED]   "logic_break"           → Replaces two-key approach; engine now checks
    //                                     Violation.severity to dispatch hard vs soft rule.
    //                                     CC prompt emits ONE "logic_break" key with severity field.
    //                                     Kept "logic_break_significant" and "logic_break_minor"
    //                                     as legacy aliases for backward compatibility.
    //
    // ORPHANED ENGINE KEYS (present in engine, NOT emitted by any uploaded prompt):
    //   Hard: "data_misinterpretation", "no_overview", "irrelevant_data"
    //         → Task 1 TA evaluation prompt was NOT uploaded. Keep these rules — they belong
    //           to the Task 1 TA scoring phase (phase2_ta for Task 1). Do NOT remove.
    //   Hard: "mixed_task"
    //         → Not defined in phase2_ta.txt or phase2_tr.txt violation schemas.
    //           ADD "mixed_task" to both TR prompt violation schemas, or remove if unused.
    //           Currently RETAINED in engine — requires prompt-side addition.
    //   Soft: "weak_overview", "missing_key_extreme", "limited_comparison", "mechanical_listing"
    //         → Task 1 TA prompt not uploaded. RETAIN — belong to Task 1 pipeline.
    //
    // =====================================================

    private static final Map<String, HardRule> HARD_RULES = Map.ofEntries(

            // ---------- TASK 1 (TA) ----------
            // NOTE: Task 1 TA prompt not included in uploaded files.
            //       These rules are retained for the Task 1 pipeline.
            Map.entry("data_misinterpretation", new HardRule(Map.of("TA", 5.5), 6.0)),
            Map.entry("no_overview", new HardRule(Map.of("TA", 6.0), 6.5)),
            Map.entry("irrelevant_data", new HardRule(Map.of("TA", 6.0), null)),

            // [NEW] Task 1: inaccurate data reporting from chart
            // severity "major" → cap TA ≤ 4.0, overall ≤ 5.0
            // severity "moderate" → cap TA ≤ 5.0, overall ≤ 6.0
            // The engine checks severity in applyAllRules() for graduated caps
            Map.entry("inaccurate_data_reporting", new HardRule(Map.of("TA", 5.0), 6.0)),

            // [NEW] Task 1: missing key features from chart
            Map.entry("missing_key_features", new HardRule(Map.of("TA", 6.0), 6.5)),

            // [NEW] Task 1: incomplete coverage of chart data
            Map.entry("incomplete_coverage", new HardRule(Map.of("TA", 6.0), null)),

            // ---------- TASK 2 (TR) ----------

            // Emitted by phase2_tr.txt as "no_position_throughout"
            // Emitted by phase2_ta.txt as "no_clear_position" (gating hard cap trigger)
            // Legacy key "no_position" retained via alias entry below
            Map.entry("no_position_throughout", new HardRule(Map.of("TR", 5.0), 5.5)),

            // Legacy alias → same caps as no_position_throughout
            Map.entry("no_position", new HardRule(Map.of("TR", 5.0), 5.5)),

            // Emitted by phase2_ta.txt as "unclear_position_progression"
            // Legacy alias "contradictory_position" retained below
            Map.entry("unclear_position_progression", new HardRule(Map.of("TR", 5.5), 6.0)),

            // Legacy alias → same caps as unclear_position_progression
            Map.entry("contradictory_position", new HardRule(Map.of("TR", 5.5), 6.0)),

            // NOTE: "mixed_task" is in the engine but NOT defined in any uploaded TR prompt
            //       violation schema. Action required: add "mixed_task" to phase2_ta.txt
            //       and phase2_tr.txt violation blocks, or remove this rule if not applicable.
            Map.entry("mixed_task", new HardRule(Map.of("TR", 5.5), 6.0)),

            // Emitted by phase2_ta.txt and phase2_tr.txt as "irrelevant_content" (TR-scoped)
            // Dispatch logic in applyAllRules() uses criterion tag to differentiate
            // from CC-scoped "irrelevant_content".
            // Legacy key "tr_irrelevant_content" retained below.
            Map.entry("irrelevant_content_tr", new HardRule(Map.of("TR", 6.0), null)),

            // Legacy alias → same caps as irrelevant_content_tr
            Map.entry("tr_irrelevant_content", new HardRule(Map.of("TR", 6.0), null)),

            // [NEW] Emitted by phase2_ta.txt — "weak_argument_depth" blocks Band 8+
            // Translated as hard cap: TR ≤ 7.0
            Map.entry("weak_argument_depth", new HardRule(Map.of("TR", 7.0), null)),

            // [NEW] Spam or Gibberish — catches essays > 20 words that make no sense
            Map.entry(
                    "spam_or_gibberish",
                    new HardRule(Map.of("TR", 1.0, "TA", 1.0, "CC", 1.0, "LR", 1.0, "GRA", 1.0), 1.0)),

            // ---------- GRA ----------
            Map.entry("too_many_errors", new HardRule(Map.of("GRA", 6.0), 6.5)),

            // ---------- CC ----------
            // "logic_break_significant" is the legacy key.
            // CC prompt (phase3_cc.txt) emits ONE "logic_break" key with a severity field.
            // See applyAllRules() for severity-aware dispatch logic.
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

            // [NEW] Emitted by phase2_ta.txt and phase2_tr.txt
            Map.entry("partial_task_addressing", new SoftRule("TR", 0.5)),

            // [NEW] Emitted by phase2_ta.txt and phase2_tr.txt
            Map.entry("insufficient_development", new SoftRule("TR", 0.25)),

            // [NEW] Emitted by phase2_ta.txt as "no_clear_position" (soft/moderate case)
            //       phase2_tr.txt emits "missing_clear_position" for the same concept.
            //       Both treated as soft penalty (hard cap is handled by no_position_throughout).
            Map.entry("no_clear_position", new SoftRule("TR", 0.25)),
            Map.entry("missing_clear_position", new SoftRule("TR", 0.25)),

            // ---------- CC ----------
            Map.entry("weak_cohesion", new SoftRule("CC", 0.25)),
            Map.entry("mixed_paragraph_focus", new SoftRule("CC", 0.25)),

            // "logic_break_minor" is the legacy key.
            // If calling code uses the CC prompt output directly, it emits "logic_break"
            // with severity="minor" → handled via severity-aware dispatch in applyAllRules().
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

        // ================= HARD CAPS =================

        for (Map.Entry<String, Violation> entry : violations.entrySet()) {

            if (!entry.getValue().active()) continue;

            String violationKey = entry.getKey();

            // -------------------------------------------------------
            // Severity-aware dispatch for CC "logic_break"
            // The CC prompt emits a single "logic_break" key with
            // a severity field ("minor" or "significant").
            // Route to the correct hard/soft rule based on severity.
            // -------------------------------------------------------
            if ("logic_break".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("significant".equals(severity)) {
                    violationKey = "logic_break_significant";
                } else {
                    // minor or unspecified → soft rule only, skip hard cap block
                    continue;
                }
            }

            // -------------------------------------------------------
            // Criterion-scoped dispatch for "irrelevant_content"
            // TR prompts emit "irrelevant_content" (TR scope).
            // CC prompt also emits "irrelevant_content" (CC scope).
            // Use the violation's criterion hint when available.
            // -------------------------------------------------------
            if ("irrelevant_content".equals(violationKey)) {
                String criterion = entry.getValue().criterion();
                if ("TR".equals(criterion)) {
                    violationKey = "irrelevant_content_tr";
                } else {
                    // CC-scoped irrelevant_content has no hard cap → skip to soft
                    continue;
                }
            }

            // -------------------------------------------------------
            // Severity-aware dispatch for Task 1 "inaccurate_data_reporting"
            // severity "major"    → TA ≤ 4.0, overall ≤ 5.0 (harsher)
            // severity "moderate" → TA ≤ 5.0, overall ≤ 6.0 (default HardRule)
            // severity "minor"    → no hard cap, skip to soft penalties
            // -------------------------------------------------------
            if ("inaccurate_data_reporting".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("major".equals(severity)) {
                    // Override with harsher caps
                    appliedHardRules.add("inaccurate_data_reporting_major");
                    adjusted.computeIfPresent("TA", (k, v) -> Math.min(v, 4.0));
                    hardCappedCriteria.add("TA");
                    overallCap = (overallCap == null) ? 5.0 : Math.min(overallCap, 5.0);
                    continue;
                } else if ("minor".equals(severity)) {
                    // minor inaccuracy → skip hard cap, let soft penalties handle
                    continue;
                }
                // "moderate" → fall through to default HardRule (TA ≤ 5.0, overall ≤ 6.0)
            }

            HardRule rule = HARD_RULES.get(violationKey);
            if (rule == null) continue;

            appliedHardRules.add(violationKey);

            // Criterion caps
            for (Map.Entry<String, Double> capEntry : rule.criterionCaps.entrySet()) {
                String criterion = capEntry.getKey();
                Double maxBand = capEntry.getValue();

                if (adjusted.containsKey(criterion)) {
                    adjusted.put(criterion, Math.min(adjusted.get(criterion), maxBand));
                    hardCappedCriteria.add(criterion);
                }
            }

            // Overall cap
            if (rule.overallCap != null) {
                overallCap = (overallCap == null) ? rule.overallCap : Math.min(overallCap, rule.overallCap);
            }
        }

        // ================= SOFT PENALTIES =================

        Map<String, Double> penaltyTracker = new HashMap<>();

        for (Map.Entry<String, Violation> entry : violations.entrySet()) {

            if (!entry.getValue().active()) continue;

            String violationKey = entry.getKey();

            // Severity-aware dispatch for "logic_break" (soft path)
            if ("logic_break".equals(violationKey)) {
                String severity = entry.getValue().severity();
                if ("significant".equals(severity)) {
                    // Already handled in hard caps block above
                    continue;
                }
                violationKey = "logic_break_minor";
            }

            // Criterion-scoped dispatch for "irrelevant_content" (CC soft path)
            if ("irrelevant_content".equals(violationKey)) {
                String criterion = entry.getValue().criterion();
                if ("TR".equals(criterion)) {
                    // TR irrelevant_content → hard cap only, no soft penalty
                    continue;
                }
                // CC scope → no dedicated soft rule; skip
                continue;
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

        return new RuleResult(adjusted, overallCap, appliedHardRules);
    }

    // =====================================================
    // ================= FINAL CALCULATION =================
    // =====================================================

    public double calculateFinalBand(Map<String, Double> adjustedBands, Double overallCap) {
        // If TA/TR is extremely low (off-topic/irrelevant), cap the entire score
        // In real IELTS, a completely off-topic essay cannot score higher than ~2.0
        Double taBand = adjustedBands.getOrDefault("TA", adjustedBands.get("TR"));
        if (taBand != null && taBand <= 1.0) {
            return ieltsRounding(Math.max(taBand, 0.0));
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

    /**
     * Violation record extended with:
     *  - severity: used by "logic_break" to dispatch hard vs soft rule
     *  - criterion: used by "irrelevant_content" to dispatch TR vs CC scope
     *
     * Both fields default to "" when not applicable.
     * Existing callers that use the 4-arg constructor remain compatible
     * via the legacy constructor below.
     */
    public record Violation(
            boolean active,
            String location,
            String evidence,
            String reason,
            String severity, // "minor" | "significant" | "" — used by logic_break
            String criterion // "TR" | "CC" | "" — used by irrelevant_content dispatch
            ) {
        /** Legacy 4-arg constructor for backward compatibility */
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
