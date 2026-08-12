package com.binformation.ledger.controller;

import com.binformation.ledger.dto.graph.PanoramaGraphDto;
import com.binformation.ledger.service.PanoramaGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class PanoramaGraphController {

    private final PanoramaGraphService panoramaGraphService;

    public PanoramaGraphController(PanoramaGraphService panoramaGraphService) {
        this.panoramaGraphService = panoramaGraphService;
    }

    /**
     * 资产全景图（P0 血缘视角）：派生关系 + 可选共享落点衔接。
     */
    @GetMapping("/panorama")
    public PanoramaGraphDto getPanorama(
            @RequestParam(defaultValue = "true") boolean includeEndpointLinks) {
        return panoramaGraphService.buildLineage(includeEndpointLinks);
    }
}
