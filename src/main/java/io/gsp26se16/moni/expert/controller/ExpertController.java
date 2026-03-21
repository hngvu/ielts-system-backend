package io.gsp26se16.moni.expert.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.service.ExpertService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/experts")
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertService expertService;

    @GetMapping
    public ResponseEntity<List<ExpertProfileResponse>> listExperts(
            @RequestParam(required = false) ExpertSpecialization specialization) {
        return ResponseEntity.ok(expertService.listExperts(specialization));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpertProfileResponse> getExpert(@PathVariable Integer id) {
        return ResponseEntity.ok(expertService.getExpert(id));
    }
}
