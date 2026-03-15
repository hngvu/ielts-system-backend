package io.gsp26se16.moni.vocab.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.*;
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
