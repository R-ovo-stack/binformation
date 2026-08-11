package com.binformation.ledger.controller;

import com.binformation.ledger.dto.changelog.ChangeLogDto;
import com.binformation.ledger.service.ChangeLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets/{assetId}/change-logs")
public class ChangeLogController {

    private final ChangeLogService changeLogService;

    public ChangeLogController(ChangeLogService changeLogService) {
        this.changeLogService = changeLogService;
    }

    @GetMapping
    public List<ChangeLogDto> list(@PathVariable Long assetId) {
        return changeLogService.listByAsset(assetId);
    }
}
