package io.gsp26se16.moni.ai.speaking.service;

import java.util.*;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.ai.writing.service.Helper;
import io.gsp26se16.moni.ai.writing.service.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Speaking-specific rule engine.
 *
 * <p>Adapts the existing {@link RuleEngine} for the four IELTS speaking criteria:
 * <ul>
 *   <li><strong>FC</strong> — Fluency &amp; Coherence</li>
 *   <li><strong>LR</strong> — Lexical Resource</li>
 *   <li><strong>GRA</strong> — Grammatical Range &amp; Accuracy</li>
 *   <li><strong>PR</strong> — Pronunciation</li>
 * </ul>
 *
 * <p>Speaking hard caps and soft penalties are defined here. Final band calculation
 * and IELTS rounding are delegated to the shared {@link RuleEngine}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingRuleEngine {

    private final RuleEngine ruleEngine;
    private final Helper helper;

    // ─────────────────────────────── Hard caps ───────────────────────────────

    /**
     * Hard caps for speaking violations.
     * Key → criterion cap, optional overall cap.
     */
    private static final Map<String, SpeakingHardRule> SPEAKING_HARD_RULES = Map.of(

            // FC: constant long pauses that severely disrupt communication
            "long_pauses_throughout", new SpeakingHardRule(Map.of("FC", 1.0), 1.5),

            // FC: completely incoherent speech — cannot be understood
            "incoherent_speech", new SpeakingHardRule(Map.of("FC", 0.0), 0.0),

            // LR: vocabulary so limited it prevents effective communication
            "insufficient_vocabulary", new SpeakingHardRule(Map.of("LR", 1.0), null),

            // GRA: pervasive basic errors impede comprehension
            "pervasive_grammar_errors", new SpeakingHardRule(Map.of("GRA", 1.0), 1.5),

            // PR: pronunciation so unclear it causes persistent misunderstanding
            "unintelligible_pronunciation", new SpeakingHardRule(Map.of("PR", 0.0), 0.0));

    // ─────────────────────────────── Soft penalties ──────────────────────────

    private static final Map<String, SpeakingSoftRule> SPEAKING_SOFT_RULES = Map.of(

            // FC
            "frequent_hesitation", new SpeakingSoftRule("FC", 0.25),
            "limited_linking_devices", new SpeakingSoftRule("FC", 0.25),
            "repetitive_self_correction", new SpeakingSoftRule("FC", 0.25),

            // LR
            "limited_range", new SpeakingSoftRule("LR", 0.25),
            "awkward_collocation", new SpeakingSoftRule("LR", 0.25),
            "inaccurate_word_choice", new SpeakingSoftRule("LR", 0.25),

            // GRA
            "frequent_minor_errors", new SpeakingSoftRule("GRA", 0.25),
            "limited_sentence_variety", new SpeakingSoftRule("GRA", 0.25),

            // PR
            "inconsistent_stress", new SpeakingSoftRule("PR", 0.25),
            "unclear_consonants", new SpeakingSoftRule("PR", 0.25));

    // ─────────────────────────────── Public API ───────────────────────────────

    /**
     * Runs the full rule engine pipeline over the four speaking criterion outputs.
     *
     * @param fc  Fluency &amp; Coherence phase output
     * @param lr  Lexical Resource phase output
     * @param gra Grammatical Range &amp; Accuracy phase output
     * @param pr  Pronunciation phase output
     * @return structured result: final_band, overall_cap, applied_hard_rules, criteria
     */
    public Map<String, Object> calculateBands(
            Map<String, Object> fc, Map<String, Object> lr, Map<String, Object> gra, Map<String, Object> pr) {

        // ── Step 1: Raw bands ──────────────────────────────────────────────────
        Map<String, Double> rawBands = Map.of(
                "FC", helper.getBand(fc),
                "LR", helper.getBand(lr),
                "GRA", helper.getBand(gra),
                "PR", helper.getBand(pr));

        // ── Step 2: Collect violations from all criteria ───────────────────────
        Map<String, RuleEngine.Violation> violations = helper.collectViolations(fc, lr, gra, pr);

        // ── Step 3: Apply speaking-specific hard caps + soft penalties ─────────
        SpeakingRuleResult speakingResult = applySpeakingRules(rawBands, violations);

        // ── Step 4: Final band (shared IELTS rounding logic) ──────────────────
        double finalBand = ruleEngine.calculateFinalBand(speakingResult.adjustedBands(), speakingResult.overallCap());

        // ── Step 5: Build result ───────────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("final_band", finalBand);
        result.put("overall_cap", speakingResult.overallCap());
        result.put("applied_hard_rules", speakingResult.appliedHardRules());
        result.put(
                "criteria",
                Map.of(
                        "FC", helper.mergeCriterion(fc, speakingResult.adjustedBands()),
                        "LR", helper.mergeCriterion(lr, speakingResult.adjustedBands()),
                        "GRA", helper.mergeCriterion(gra, speakingResult.adjustedBands()),
                        "PR", helper.mergeCriterion(pr, speakingResult.adjustedBands())));

        return result;
    }

    // ─────────────────────────────── Rule application ─────────────────────────

    private SpeakingRuleResult applySpeakingRules(
            Map<String, Double> originalBands, Map<String, RuleEngine.Violation> violations) {

        Map<String, Double> adjusted = new HashMap<>(originalBands);
        Double overallCap = null;
        Set<String> hardCappedCriteria = new HashSet<>();
        List<String> appliedHardRules = new ArrayList<>();

        // ── Hard caps ────────────────────────────────────────────────────────
        for (Map.Entry<String, RuleEngine.Violation> entry : violations.entrySet()) {
            if (!entry.getValue().active()) continue;

            String key = entry.getKey();
            SpeakingHardRule rule = SPEAKING_HARD_RULES.get(key);
            if (rule == null) continue;

            appliedHardRules.add(key);

            for (Map.Entry<String, Double> capEntry : rule.criterionCaps.entrySet()) {
                String criterion = capEntry.getKey();
                Double max = capEntry.getValue();
                if (adjusted.containsKey(criterion)) {
                    adjusted.put(criterion, Math.min(adjusted.get(criterion), max));
                    hardCappedCriteria.add(criterion);
                }
            }

            if (rule.overallCap != null) {
                overallCap = (overallCap == null) ? rule.overallCap : Math.min(overallCap, rule.overallCap);
            }
        }

        // ── Soft penalties ───────────────────────────────────────────────────
        Map<String, Double> penaltyTracker = new HashMap<>();

        for (Map.Entry<String, RuleEngine.Violation> entry : violations.entrySet()) {
            if (!entry.getValue().active()) continue;

            String key = entry.getKey();
            SpeakingSoftRule rule = SPEAKING_SOFT_RULES.get(key);
            if (rule == null) continue;

            String criterion = rule.criterion;
            if (!adjusted.containsKey(criterion)) continue;
            if (hardCappedCriteria.contains(criterion)) continue;

            double current = penaltyTracker.getOrDefault(criterion, 0.0);
            if (current >= 0.5) continue;

            double apply = Math.min(rule.penalty, 0.5 - current);
            adjusted.put(criterion, Math.round((adjusted.get(criterion) - apply) * 100.0) / 100.0);
            penaltyTracker.put(criterion, current + apply);
        }

        return new SpeakingRuleResult(adjusted, overallCap, appliedHardRules);
    }

    // ─────────────────────────────── Data structures ─────────────────────────

    private static class SpeakingHardRule {
        final Map<String, Double> criterionCaps;
        final Double overallCap;

        SpeakingHardRule(Map<String, Double> criterionCaps, Double overallCap) {
            this.criterionCaps = criterionCaps;
            this.overallCap = overallCap;
        }
    }

    private static class SpeakingSoftRule {
        final String criterion;
        final double penalty;

        SpeakingSoftRule(String criterion, double penalty) {
            this.criterion = criterion;
            this.penalty = penalty;
        }
    }

    private record SpeakingRuleResult(
            Map<String, Double> adjustedBands, Double overallCap, List<String> appliedHardRules) {}
}
