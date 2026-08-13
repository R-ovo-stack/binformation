package com.binformation.ledger.controller;

import com.binformation.ledger.dto.graph.AssetGraphDto;
import com.binformation.ledger.dto.graph.PanoramaGraphDto;
import com.binformation.ledger.service.AssetGraphService;
import com.binformation.ledger.service.PanoramaGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class PanoramaGraphController {

    private final PanoramaGraphService panoramaGraphService;
    private final AssetGraphService assetGraphService;

    public PanoramaGraphController(
            PanoramaGraphService panoramaGraphService,
            AssetGraphService assetGraphService) {
        this.panoramaGraphService = panoramaGraphService;
        this.assetGraphService = assetGraphService;
    }

    /**
     * 资产全景图（P0 血缘视角）：派生关系 + 可选共享落点衔接。
     */
    @GetMapping("/panorama")
    public PanoramaGraphDto getPanorama(
            @RequestParam(defaultValue = "true") boolean includeEndpointLinks) {
        return panoramaGraphService.buildLineage(includeEndpointLinks);
    }

    /**
     * 技术全景（P1）：合并多资产落点级流向图；不传 assetIds 则包含全部资产。
     */
    @GetMapping("/panorama/technical")
    public AssetGraphDto getTechnicalPanorama(
            @RequestParam(required = false) List<Long> assetIds,
            @RequestParam(defaultValue = "false") boolean includeAuxiliary,
            @RequestParam(defaultValue = "true") boolean includeDerivationBridges) {
        return assetGraphService.buildTechnicalPanorama(assetIds, includeAuxiliary, includeDerivationBridges);
    }
}
