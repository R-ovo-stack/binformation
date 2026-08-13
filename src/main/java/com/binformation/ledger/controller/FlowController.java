package com.binformation.ledger.controller;

import com.binformation.ledger.dto.flow.FlowDetailDto;
import com.binformation.ledger.dto.flow.FlowSaveRequest;
import com.binformation.ledger.dto.flow.FlowSummaryDto;
import com.binformation.ledger.service.FlowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping("/assets/{assetId}/flows")
    public List<FlowSummaryDto> listFlows(@PathVariable Long assetId) {
        return flowService.listByAsset(assetId);
    }

    @PostMapping("/assets/{assetId}/flows")
    @ResponseStatus(HttpStatus.CREATED)
    public FlowDetailDto createFlow(
            @PathVariable Long assetId,
            @Valid @RequestBody FlowSaveRequest request) {
        return flowService.create(assetId, request);
    }

    @GetMapping("/flows/{flowId}")
    public FlowDetailDto getFlow(@PathVariable Long flowId) {
        return flowService.getById(flowId);
    }

    @PutMapping("/flows/{flowId}")
    public FlowDetailDto updateFlow(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowSaveRequest request) {
        return flowService.update(flowId, request);
    }

    @DeleteMapping("/flows/{flowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFlow(@PathVariable Long flowId) {
        flowService.delete(flowId);
    }
}
