---
title: "REST API Speaking Scoring for Practice Mode"
description: "Wire the existing AI speaking evaluation pipeline into the REST endpoint for single-question practice scoring"
status: pending
priority: P1
effort: 3h
branch: quanglm
tags: [speaking, ai, rest-api, practice-mode]
created: 2026-03-28
---

# REST API Speaking Scoring for Practice Mode

## Summary

The backend already has a full IELTS Speaking evaluation pipeline (`ConversationEngine` + `SpeakingRuleEngine` + rubrics). The REST endpoint at `/api/v1/ai/speaking/score` is a stub. Goal: accept audio + question via multipart, transcribe server-side with AssemblyAI, evaluate with the existing pipeline, and return scores in the format the frontend expects.

## Key Findings

| Aspect | Detail |
|---|---|
| **Existing evaluator** | `ConversationEngine.evaluateFromExam(sessionId, userId, fullTranscript)` evaluates 4 criteria (FC, LR, GRA, PR) + feedback |
| **Return format needed** | `{ fluency, pronunciation, vocabulary, grammar, overallScore, comments }` |
| **evaluateFromExam return** | `{ fluency, vocabulary, grammar, pronunciation, final_band, feedback, transcript }` -- almost matches |
| **Transcription** | `TranscriptServiceImpl.transcribeAudio(audioUrl)` exists but takes a **URL**, not raw bytes. AssemblyAI requires upload first to get URL |
| **Credit** | `creditService.checkAndDeduct(userId, "AI_SPEAKING_SCORE")` -- pattern exists in `SpeakingExamHandler` |
| **Frontend expects** | `overallScore` (or `overall_score`) + `comments` (or `comment`) -- see `speaking-store.ts:96-98` |
| **evaluateFromExam creates** | `SpeakingSubmission` entity, `AiEvaluation` entity -- persists results |

## Architecture Decision: Reuse vs. New Method

**Option A (Recommended): Add a new `evaluatePractice()` method in `ConversationEngine`**
- Avoids faking a 3-part transcript for a single Q&A
- Reuses all internal methods (`evaluateCriterion`, `generateFeedback`, `calculateBands`, `persistEvaluation`)
- Passes actual question text instead of "IELTS Speaking Test" placeholder
- Returns response mapped to frontend's expected format

**Option B (Rejected): Format single Q&A as exam transcript**
- Fragile -- prompt templates designed for multi-part exams
- Would require wrapping as `"PART 1:\nExaminer: {question}\nCandidate: {answer}"` -- hacky

## Implementation Plan

### Phase 1: AssemblyAI Audio Upload (New Method)

**File:** `Backend/src/main/java/io/gsp26se16/moni/content/service/TranscriptServiceImpl.java`

Add method to upload raw audio bytes to AssemblyAI and get a text transcript back:

```java
public String transcribeAudioFile(byte[] audioBytes)
```

Steps:
1. `POST` to `https://api.assemblyai.com/v2/upload` with raw bytes + `Authorization` header → returns `{ "upload_url": "..." }`
2. `POST` to `https://api.assemblyai.com/v2/transcript` with `{ "audio_url": upload_url, "language_code": "en" }`
3. Poll `GET /transcript/{id}` until `completed` (reuse existing polling logic)
4. Return the plain `text` field (not sentences/utterances -- we just need the transcript string)

Also add to `TranscriptService` interface:
```java
String transcribeAudioFile(byte[] audioBytes);
```

### Phase 2: Practice Evaluation Method in ConversationEngine

**File:** `Backend/src/main/java/io/gsp26se16/moni/ai/speaking/service/ConversationEngine.java`

Add new public method:

```java
public Map<String, Object> evaluatePractice(String userId, String question, String transcript)
```

Implementation:
1. Guard: if transcript is blank, return default result
2. Create `SpeakingSubmission` via existing `createSubmission(userId, transcript)`
3. Build `ChatClient`
4. Call `evaluateCriterion(chatClient, "FC"|"LR"|"GRA"|"PR", transcript, question)` -- pass actual question, not "IELTS Speaking Test"
5. Call `speakingRuleEngine.calculateBands(fc, lr, gra, pr)`
6. Call `generateFeedback(chatClient, transcript, assessment)`
7. Persist via existing `persistEvaluation()`
8. Return map with keys: `fluency`, `pronunciation`, `vocabulary`, `grammar`, `overallScore`, `comments`, `transcript`

Key difference from `evaluateFromExam`: no `examSessionId` param, returns `overallScore` (camelCase) + `comments` (string extracted from feedback map).

### Phase 3: Wire Up the REST Endpoint

**File:** `Backend/src/main/java/io/gsp26se16/moni/ai/writing/controller/AiController.java`

Changes:
1. Inject `TranscriptService` and `ConversationEngine`
2. In `scoreSpeaking()`:
   - Get `userId` via `getCurrentUserId()`
   - Transcribe: `transcriptService.transcribeAudioFile(audio.getBytes())`
   - Evaluate: `conversationEngine.evaluatePractice(userId, question, transcript)`
   - Deduct credit: `creditService.checkAndDeduct(userId, "AI_SPEAKING_SCORE")` -- AFTER successful evaluation
   - Return result map

### Phase 4: Error Handling

In `AiController.scoreSpeaking()`:
- Wrap in try-catch
- If transcription fails → 500 with `{ "error": "Transcription failed" }`
- If evaluation fails → 500 with `{ "error": "Evaluation failed" }`
- If credit insufficient → existing `AppException` handler returns 403
- If audio is empty/null → 400 with `{ "error": "Audio file is required" }`

Credit deduction order: **evaluate first, deduct after** -- user not charged on failure.

## Files to Modify

| File | Change |
|---|---|
| `content/service/TranscriptService.java` | Add `String transcribeAudioFile(byte[])` to interface |
| `content/service/TranscriptServiceImpl.java` | Implement `transcribeAudioFile()` with upload + polling |
| `ai/speaking/service/ConversationEngine.java` | Add `evaluatePractice(userId, question, transcript)` method |
| `ai/writing/controller/AiController.java` | Wire transcription + evaluation + credit in `scoreSpeaking()` |

## Files NOT to Modify

- Frontend -- already expects the right format
- `SpeakingRuleEngine` -- reused as-is
- `PromptLoader` -- reused as-is
- Rubric files -- reused as-is

## Response Format Mapping

| ConversationEngine returns | Frontend expects | Notes |
|---|---|---|
| `fluency` (double) | `fluency` | Direct pass |
| `pronunciation` (double) | `pronunciation` | Direct pass |
| `vocabulary` (double) | `vocabulary` | Direct pass |
| `grammar` (double) | `grammar` | Direct pass |
| `final_band` (double) | `overallScore` | Rename key |
| `feedback` (Map) | `comments` (String) | Extract `feedback.summary` or serialize |

## Todo

- [ ] Add `transcribeAudioFile(byte[])` to `TranscriptService` interface
- [ ] Implement upload + transcribe in `TranscriptServiceImpl`
- [ ] Add `evaluatePractice()` to `ConversationEngine`
- [ ] Wire `AiController.scoreSpeaking()` with real logic
- [ ] Test with actual audio file via Postman/curl
- [ ] Verify credit deduction occurs only after success

## Unresolved Questions

1. **Audio format**: Frontend sends webm. AssemblyAI supports webm natively -- no conversion needed. Confirm?
2. **Timeout**: AssemblyAI polling can take up to 5 min. Should we make the REST endpoint async (return 202 + poll) or keep synchronous? Current writing endpoint is synchronous with LLM calls, so synchronous is consistent. But transcription adds ~10-30s. Recommend keeping synchronous for simplicity since practice audio is short (1-3 min).
3. **Feedback.comments extraction**: The `generateFeedback()` returns a Map. Frontend expects a string `comments`. Plan: extract `feedback.get("summary")` or `objectMapper.writeValueAsString(feedback)` as fallback.
