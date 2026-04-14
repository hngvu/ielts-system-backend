package io.gsp26se16.moni.content.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.StimulusCreateRequest;
import io.gsp26se16.moni.content.dto.response.StimulusResponse;
import io.gsp26se16.moni.content.service.StimulusService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/stimuli")
@RequiredArgsConstructor
public class StimulusController {
    private final StimulusService stimulusService;

    @PostMapping
    public ResponseEntity<ApiResponse<Integer>> createStimulus(@RequestBody @Valid StimulusCreateRequest request) {
        Integer stimulusId = stimulusService.createStimulus(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Integer>builder()
                        .code(1000)
                        .message("Tạo ngữ liệu mới thành công")
                        .result(stimulusId)
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StimulusResponse>> updateStimulus(
            @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        StimulusResponse updated = stimulusService.updateStimulus(
                id, (String) body.get("content"), (String) body.get("mediaUrl"), body.get("transcript"));
        return ResponseEntity.ok(ApiResponse.<StimulusResponse>builder()
                .code(1000)
                .message("Cập nhật ngữ liệu thành công")
                .result(updated)
                .build());
    }

    @PostMapping("/transcribe-url")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> transcribeByUrl(
            @RequestBody Map<String, String> body) {
        String audioUrl = body.get("audioUrl");
        List<Map<String, Object>> transcript = stimulusService.transcribeByUrl(audioUrl);
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .code(1000)
                .message("Transcript tạo thành công")
                .result(transcript)
                .build());
    }

    @PostMapping("/{id}/transcribe")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> transcribeStimulus(@PathVariable Integer id) {
        List<Map<String, Object>> transcript = stimulusService.transcribeAndSave(id);
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .code(1000)
                .message("Transcript tạo thành công")
                .result(transcript)
                .build());
    }

    @GetMapping("/{id}/transcript")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTranscript(@PathVariable Integer id) {
        List<Map<String, Object>> transcript = stimulusService.getTranscript(id);
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .code(1000)
                .message("Lấy transcript thành công")
                .result(transcript)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StimulusResponse>>> getAllStimuli(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Skill skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StimulusResponse> result = stimulusService.getAllStimuli(keyword, skill, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<StimulusResponse>>builder()
                .code(1000)
                .message("Lấy danh sách ngữ liệu thành công")
                .result(result)
                .build());
    }

    // =========================================================================
    // Chart Analysis (Writing Task 1)
    // =========================================================================

    @PostMapping(value = "/{id}/analyze-chart", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeChart(
            @PathVariable Integer id,
            @RequestPart("chartImage") org.springframework.web.multipart.MultipartFile chartImage)
            throws java.io.IOException {
        Map<String, Object> analysis = stimulusService.analyzeChart(id, chartImage.getBytes());
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Phân tích biểu đồ thành công")
                .result(analysis)
                .build());
    }

    @PutMapping("/{id}/vison-analysis")
    public ResponseEntity<ApiResponse<Void>> updateVisonAnalysis(
            @PathVariable Integer id, @RequestBody Map<String, Object> visonAnalysisResult) {
        stimulusService.updateVisonAnalysisResult(id, visonAnalysisResult);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Cập nhật dữ liệu biểu đồ thành công")
                .build());
    }

    @GetMapping("/{id}/vison-analysis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisonAnalysis(@PathVariable Integer id) {
        Map<String, Object> result = stimulusService.getVisonAnalysisResult(id);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Lấy dữ liệu biểu đồ thành công")
                .result(result)
                .build());
    }
}
