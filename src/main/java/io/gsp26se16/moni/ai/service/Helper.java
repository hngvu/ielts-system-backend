package io.gsp26se16.moni.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class Helper {

    private final ObjectMapper objectMapper;
    private final RuleEngine ruleEngine;

    // =========================================================================
    // VIOLATION COLLECTION
    // =========================================================================

    /**
     * Flat-merges active violations from all criterion phase outputs into a
     * single map keyed by violation name.
     *
     * Each phase output map must already be normalised before calling this:
     *   - TR: "irrelevant_content" renamed → "irrelevant_content_tr"
     *   - CC / LR / GRA: "criterion" field injected
     * See normaliseTrOutput() and withCriterion() below.
     */
    @SafeVarargs
    public final Map<String, RuleEngine.Violation> collectViolations(
            Map<String, Object>... criteriaResults) {

        Map<String, RuleEngine.Violation> result = new HashMap<>();

        for (Map<String, Object> criterion : criteriaResults) {

            Object vObj = criterion.get("violations");
            if (!(vObj instanceof Map<?, ?> violations)) continue;

            for (Map.Entry<?, ?> entry : violations.entrySet()) {

                String key = entry.getKey().toString();
                if (!(entry.getValue() instanceof Map<?, ?> vMap)) continue;

                boolean active = Boolean.TRUE.equals(vMap.get("active"));
                if (!active) continue;

                result.put(key, new RuleEngine.Violation(
                        true,
                        String.valueOf(vMap.get("location")),
                        String.valueOf(vMap.get("evidence")),
                        String.valueOf(vMap.get("reason"))
                ));
            }
        }

        return result;
    }

    // =========================================================================
    // BAND MERGE
    // =========================================================================

    /**
     * Returns a copy of a phase output map with the post-rule-engine
     * adjusted band injected as "adjusted_band".
     *
     * Reads "criterion" from the map to find the correct adjusted value.
     * CC, LR, and GRA outputs must have "criterion" injected via
     * withCriterion() before this is called.
     */
    public Map<String, Object> mergeCriterion(
            Map<String, Object> original,
            Map<String, Double> adjustedBands) {

        String key = (String) original.get("criterion");
        Map<String, Object> copy = new HashMap<>(original);

        if (key != null && adjustedBands.containsKey(key)) {
            copy.put("adjusted_band", adjustedBands.get(key));
        }
        return copy;
    }

    // =========================================================================
    // BAND CALCULATION  (Phase 6)
    // =========================================================================

    /**
     * Runs the full Rule Engine pipeline over the four criterion outputs and
     * returns the structured result map used by the service response.
     *
     * Steps performed:
     *   1. Normalise TR output (rename irrelevant_content_tr, inject criterion).
     *   2. Inject "criterion" into CC / LR / GRA outputs.
     *   3. Extract raw bands.
     *   4. Collect all active violations from every criterion.
     *   5. Apply hard caps + soft penalties via RuleEngine.
     *   6. Calculate final IELTS-rounded band.
     *   7. Merge adjusted bands back into each criterion output.
     *
     * @param tr  Task Response phase output
     * @param cc  Coherence and Cohesion phase output
     * @param lr  Lexical Resource phase output
     * @param gra Grammatical Range and Accuracy phase output
     * @return structured result map: final_band, overall_cap, applied_hard_rules, criteria
     */
    public Map<String, Object> calculateBands(
            Map<String, Object> tr,
            Map<String, Object> cc,
            Map<String, Object> lr,
            Map<String, Object> gra) {

        // ── Step 1 & 2: Normalise outputs ─────────────────────────────────────
        Map<String, Object> trNormalised = normaliseTrOutput(tr);
        Map<String, Object> ccTagged     = withCriterion(cc,  "CC");
        Map<String, Object> lrTagged     = withCriterion(lr,  "LR");
        Map<String, Object> graTagged    = withCriterion(gra, "GRA");

        // ── Step 3: Raw bands ──────────────────────────────────────────────────
        Map<String, Double> rawBands = Map.of(
                "TR",  getBand(trNormalised),
                "CC",  getBand(ccTagged),
                "LR",  getBand(lrTagged),
                "GRA", getBand(graTagged)
        );

        // ── Step 4: Collect violations ─────────────────────────────────────────
        Map<String, RuleEngine.Violation> violations =
                collectViolations(trNormalised, ccTagged, lrTagged, graTagged);

        // ── Step 5: Apply Rule Engine ──────────────────────────────────────────
        RuleEngine.RuleResult ruleResult = ruleEngine.applyAllRules(rawBands, violations);

        // ── Step 6: Final band ─────────────────────────────────────────────────
        double finalBand = ruleEngine.calculateFinalBand(
                ruleResult.adjustedBands(), ruleResult.overallCap());

        // ── Step 7: Build result ───────────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("final_band",         finalBand);
        result.put("overall_cap",         ruleResult.overallCap());
        result.put("applied_hard_rules",  ruleResult.appliedHardRules());
        result.put("criteria", Map.of(
                "TR",  mergeCriterion(trNormalised, ruleResult.adjustedBands()),
                "CC",  mergeCriterion(ccTagged,     ruleResult.adjustedBands()),
                "LR",  mergeCriterion(lrTagged,     ruleResult.adjustedBands()),
                "GRA", mergeCriterion(graTagged,    ruleResult.adjustedBands())
        ));
        return result;
    }

    // =========================================================================
    // OUTPUT NORMALISATION  (Task 2-specific, used before violation collection)
    // =========================================================================

    /**
     * Returns a mutable copy of the TR phase output with two normalisations:
     *
     * 1. Injects "criterion": "TR" if absent (safety guard).
     *
     * 2. Renames the "irrelevant_content" violation key → "irrelevant_content_tr":
     *    - Matches the RuleEngine hard-rule key so the correct TR cap is applied.
     *    - Prevents key collision with CC's "irrelevant_content" in the merged
     *      violations map produced by collectViolations().
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normaliseTrOutput(Map<String, Object> tr) {
        Map<String, Object> copy = new HashMap<>(tr);
        copy.putIfAbsent("criterion", "TR");

        Object rawViolations = copy.get("violations");
        if (!(rawViolations instanceof Map<?, ?> vRaw)) return copy;

        Map<String, Object> violations = new HashMap<>((Map<String, Object>) vRaw);
        if (violations.containsKey("irrelevant_content")) {
            violations.put("irrelevant_content_tr", violations.remove("irrelevant_content"));
            copy.put("violations", violations);
        }
        return copy;
    }

    /**
     * Returns a mutable copy of a phase output with "criterion" injected.
     *
     * CC, LR, and GRA prompts don't emit a "criterion" field.
     * Both mergeCriterion() and collectViolations() rely on it, so we inject
     * it here before any downstream processing.
     */
    public Map<String, Object> withCriterion(Map<String, Object> phaseOutput, String criterion) {
        Map<String, Object> copy = new HashMap<>(phaseOutput);
        copy.put("criterion", criterion);
        return copy;
    }

    // =========================================================================
    // BAND EXTRACTION
    // =========================================================================

    /**
     * Safely extracts the "band" value from a phase output map as a double.
     * Defaults to 5.0 on any missing or unparseable value.
     */
    public double getBand(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            if (map.containsKey("error")) {
                log.warn("Criterion contains error, defaulting to band 5.0: {}", map.get("error"));
                return 5.0;
            }
            Object val = map.get("band");
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s) {
                try { return Double.parseDouble(s); }
                catch (NumberFormatException e) { log.warn("Cannot parse band: {}", val); }
            }
        }
        log.warn("Invalid band value, defaulting to 5.0: {}", obj);
        return 5.0;
    }

    // =========================================================================
    // JSON PARSING
    // =========================================================================

    /**
     * Parse JSON response from LLM, with repair fallback for common LLM JSON errors.
     */
    public Map<String, Object> parseJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            log.error("Empty response from LLM");
            return Map.of("error", "Empty response", "raw", "");
        }

        try {
            String cleaned = cleanJsonResponse(response);
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("Initial JSON parse failed, attempting repair. Raw: {}", response);
            try {
                String repaired = repairJson(cleanJsonResponse(response));
                return objectMapper.readValue(repaired, Map.class);
            } catch (Exception e2) {
                log.error("JSON repair also failed. Raw response: {}", response, e2);
                return Map.of("error", "Invalid JSON", "raw", response);
            }
        }
    }

    /**
     * Repair common LLM JSON errors:
     * 1. Unquoted string values:  "evidence": oil consuming patterns  →  "evidence": "oil consuming patterns"
     * 2. Trailing commas before } or ]
     */
    public String repairJson(String json) {
        if (json == null) return "{}";

        // Pass 1: fix trailing commas before closing braces/brackets
        String step1 = json.replaceAll(",\\s*([}\\]])", "$1");

        // Pass 2: fix unquoted string values
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\"[^\"]+\"\\s*:\\s*)(?![\"\\[\\{\\]\\}\\d]|true\\b|false\\b|null\\b)([^,}\\]]+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(step1);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String keyPart   = matcher.group(1);
            String valuePart = matcher.group(2).trim()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            matcher.appendReplacement(sb,
                    java.util.regex.Matcher.quoteReplacement(keyPart + "\"" + valuePart + "\""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Strips markdown code fences (```json / ```) that LLMs sometimes wrap around JSON.
     */
    public String cleanJsonResponse(String response) {
        if (response == null) return "{}";

        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? "{}" : cleaned;
    }
}