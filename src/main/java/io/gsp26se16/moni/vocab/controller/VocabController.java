package io.gsp26se16.moni.vocab.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.*;
import io.gsp26se16.moni.vocab.entity.CuratedWord;
import io.gsp26se16.moni.vocab.repository.CuratedWordRepository;
import io.gsp26se16.moni.vocab.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/vocab")
@RequiredArgsConstructor
@Slf4j
public class VocabController {

    private final VocabLookupService vocabLookupService;
    private final VocabService vocabService;
    private final VocabListService vocabListService;
    private final VocabSearchService vocabSearchService;
    private final VocabLearningService vocabLearningService;
    private final CuratedWordRepository curatedWordRepository;

    // --- Existing endpoint ---

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<VocabLookupResponse>> lookup(
            @RequestParam String word, @RequestParam(required = false) String sentence) {
        try {
            VocabLookupResponse result = vocabLookupService.lookupWord(word, sentence);
            return ResponseEntity.ok(
                    ApiResponse.<VocabLookupResponse>builder().result(result).build());
        } catch (Exception e) {
            log.error("Vocab lookup failed for word='{}': {}", word, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<VocabLookupResponse>builder()
                            .code(5001)
                            .message(e.getMessage())
                            .build());
        }
    }

    // --- Personal word management ---

    @GetMapping("/my-words")
    public ResponseEntity<ApiResponse<Page<VocabResponse>>> getMyWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer listId,
            @RequestParam(required = false) String search) {
        String credentialId = getCredentialId();
        Page<VocabResponse> result = vocabService.getMyWords(credentialId, page, size, listId, search);
        return ResponseEntity.ok(
                ApiResponse.<Page<VocabResponse>>builder().result(result).build());
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<VocabResponse>> saveWord(@RequestBody SaveVocabRequest request) {
        String credentialId = getCredentialId();
        VocabResponse result = vocabService.saveWord(credentialId, request);
        return ResponseEntity.ok(
                ApiResponse.<VocabResponse>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWord(@PathVariable Integer id) {
        String credentialId = getCredentialId();
        vocabService.deleteWord(credentialId, id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("Đã xóa từ vựng").build());
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<Void>> moveWord(@PathVariable Integer id, @RequestBody MoveVocabRequest request) {
        String credentialId = getCredentialId();
        vocabService.moveWord(credentialId, id, request.getVocabListId());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("Đã chuyển từ vựng").build());
    }

    // --- Collection management ---

    @GetMapping("/lists")
    public ResponseEntity<ApiResponse<List<VocabListResponse>>> getLists() {
        String credentialId = getCredentialId();
        List<VocabListResponse> result = vocabListService.getUserLists(credentialId);
        return ResponseEntity.ok(
                ApiResponse.<List<VocabListResponse>>builder().result(result).build());
    }

    @PostMapping("/lists")
    public ResponseEntity<ApiResponse<VocabListResponse>> createList(@RequestBody CreateVocabListRequest request) {
        String credentialId = getCredentialId();
        VocabListResponse result = vocabListService.createList(credentialId, request);
        return ResponseEntity.ok(
                ApiResponse.<VocabListResponse>builder().result(result).build());
    }

    @PutMapping("/lists/{id}")
    public ResponseEntity<ApiResponse<VocabListResponse>> updateList(
            @PathVariable Integer id, @RequestBody CreateVocabListRequest request) {
        String credentialId = getCredentialId();
        VocabListResponse result = vocabListService.updateList(credentialId, id, request);
        return ResponseEntity.ok(
                ApiResponse.<VocabListResponse>builder().result(result).build());
    }

    @DeleteMapping("/lists/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteList(@PathVariable Integer id) {
        String credentialId = getCredentialId();
        vocabListService.deleteList(credentialId, id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("Đã xóa bộ từ vựng").build());
    }

    // --- Dictionary search ---

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<VocabSearchResponse>> search(@RequestParam String q) {
        String credentialId = getCredentialId();
        VocabSearchResponse result = vocabSearchService.searchWord(credentialId, q);
        return ResponseEntity.ok(
                ApiResponse.<VocabSearchResponse>builder().result(result).build());
    }

    // --- Learning / Review ---

    @GetMapping("/due-review")
    public ResponseEntity<ApiResponse<List<VocabResponse>>> getDueReview(@RequestParam(defaultValue = "20") int limit) {
        String credentialId = getCredentialId();
        List<VocabResponse> result = vocabLearningService.getDueReview(credentialId, limit);
        return ResponseEntity.ok(
                ApiResponse.<List<VocabResponse>>builder().result(result).build());
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Void>> submitReview(
            @PathVariable Integer id, @RequestBody ReviewRequest request) {
        String credentialId = getCredentialId();
        vocabLearningService.submitReview(credentialId, id, request.getQuality());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("Đã lưu kết quả ôn tập").build());
    }

    @GetMapping("/review-stats")
    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getReviewStats() {
        String credentialId = getCredentialId();
        ReviewStatsResponse result = vocabLearningService.getReviewStats(credentialId);
        return ResponseEntity.ok(
                ApiResponse.<ReviewStatsResponse>builder().result(result).build());
    }

    @GetMapping("/quiz")
    public ResponseEntity<ApiResponse<QuizResponse>> generateQuiz(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "curated") String source,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String band,
            @RequestParam(required = false) String topic) {
        String credentialId = "saved".equals(source) ? getCredentialId() : null;
        QuizResponse result = vocabLearningService.generateQuiz(credentialId, count, source, type, band, topic);
        return ResponseEntity.ok(
                ApiResponse.<QuizResponse>builder().result(result).build());
    }

    @GetMapping("/word-match")
    public ResponseEntity<ApiResponse<WordMatchResponse>> getWordMatch(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "curated") String source,
            @RequestParam(required = false) String band,
            @RequestParam(required = false) String topic) {
        String credentialId = "saved".equals(source) ? getCredentialId() : null;
        WordMatchResponse result = vocabLearningService.getWordMatch(credentialId, count, source, band, topic);
        return ResponseEntity.ok(
                ApiResponse.<WordMatchResponse>builder().result(result).build());
    }

    // --- Browse curated words ---

    @GetMapping("/browse")
    public ResponseEntity<ApiResponse<Page<CuratedWord>>> browse(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String band,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String cefrLevel) {
        var pageable = PageRequest.of(page, size);
        Page<CuratedWord> result = curatedWordRepository.findByFilters(band, topic, cefrLevel, pageable);
        return ResponseEntity.ok(
                ApiResponse.<Page<CuratedWord>>builder().result(result).build());
    }

    @GetMapping("/topics")
    public ResponseEntity<ApiResponse<List<String>>> getTopics() {
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .result(curatedWordRepository.findDistinctTopics())
                .build());
    }

    @GetMapping("/bands")
    public ResponseEntity<ApiResponse<List<String>>> getBands() {
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .result(curatedWordRepository.findDistinctBands())
                .build());
    }

    // --- Helper ---

    private String getCredentialId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String id = jwt.getClaimAsString("userId");
            if (id != null) return id;
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
