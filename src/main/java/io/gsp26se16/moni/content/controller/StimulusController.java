package io.gsp26se16.moni.content.controller;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.StimulusCreateRequest;
import io.gsp26se16.moni.content.dto.response.StimulusResponse;
import io.gsp26se16.moni.content.service.StimulusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/stimuli")
@RequiredArgsConstructor
public class StimulusController {
    private final StimulusService stimulusService;

    @PostMapping
    public ResponseEntity<ApiResponse<Integer>> createStimulus(@RequestBody @Valid StimulusCreateRequest request) {
        Integer stimulusId = stimulusService.createStimulus(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Integer>builder()
                .code(1000).message("Tạo ngữ liệu mới thành công").result(stimulusId).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StimulusResponse>>> getAllStimuli(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Skill skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StimulusResponse> result = stimulusService.getAllStimuli(keyword, skill, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<StimulusResponse>>builder()
                .code(1000).message("Lấy danh sách ngữ liệu thành công").result(result).build());
    }
}
