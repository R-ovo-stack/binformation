package com.binformation.ledger.controller;

import com.binformation.ledger.dto.impact.ImpactAnalysisDto;
import com.binformation.ledger.service.ImpactAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private final ImpactAnalysisService impactAnalysisService;

    public ImpactController(ImpactAnalysisService impactAnalysisService) {
        this.impactAnalysisService = impactAnalysisService;
    }

    @GetMapping
    public ImpactAnalysisDto analyze(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(defaultValue = "DELETE") String action) {
        return impactAnalysisService.analyze(entityType, entityId, action);
    }
}
