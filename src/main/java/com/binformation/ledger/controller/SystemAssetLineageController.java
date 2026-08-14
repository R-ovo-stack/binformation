package com.binformation.ledger.controller;

import com.binformation.ledger.dto.lineage.AssetDownstreamQueryDto;
import com.binformation.ledger.dto.lineage.SystemAssetQueryDto;
import com.binformation.ledger.dto.lineage.SystemOptionDto;
import com.binformation.ledger.service.SystemAssetLineageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lineage")
public class SystemAssetLineageController {

    private final SystemAssetLineageService lineageService;

    public SystemAssetLineageController(SystemAssetLineageService lineageService) {
        this.lineageService = lineageService;
    }

    @GetMapping("/systems")
    public List<SystemOptionDto> listSystems() {
        return lineageService.listSystems();
    }

    @GetMapping("/systems/{systemId}/assets")
    public SystemAssetQueryDto consumedBySystem(
            @PathVariable Long systemId,
            @RequestParam(defaultValue = "false") boolean includeAuxiliary) {
        return lineageService.consumedBySystem(systemId, includeAuxiliary);
    }

    @GetMapping("/assets/{assetId}/downstream-systems")
    public AssetDownstreamQueryDto downstreamSystems(
            @PathVariable Long assetId,
            @RequestParam(defaultValue = "false") boolean includeAuxiliary) {
        return lineageService.downstreamSystems(assetId, includeAuxiliary);
    }
}
