package io.gsp26se16.moni.vocab.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.vocab.dto.VocabLookupResponse;
import io.gsp26se16.moni.vocab.service.VocabLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/vocab")
@RequiredArgsConstructor
@Slf4j
public class VocabController {

    private final VocabLookupService vocabLookupService;

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
}
