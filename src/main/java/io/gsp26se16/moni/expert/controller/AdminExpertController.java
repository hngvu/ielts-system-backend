package io.gsp26se16.moni.expert.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.expert.dto.CreateExpertRequest;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.dto.UpdateExpertStatusRequest;
import io.gsp26se16.moni.expert.service.ExpertService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/experts")
@RequiredArgsConstructor
public class AdminExpertController {

    private final ExpertService expertService;

    @PostMapping
    public ResponseEntity<ExpertProfileResponse> createExpert(@RequestBody CreateExpertRequest request) {
        return ResponseEntity.ok(expertService.createExpert(request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Integer id, @RequestBody UpdateExpertStatusRequest request) {
        expertService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpert(@PathVariable Integer id) {
        expertService.deleteExpert(id);
        return ResponseEntity.noContent().build();
    }
}
