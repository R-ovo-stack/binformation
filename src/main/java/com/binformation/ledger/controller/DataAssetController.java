package com.binformation.ledger.controller;

import com.binformation.ledger.dto.graph.AssetGraphDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.service.AssetGraphService;
import com.binformation.ledger.service.DataAssetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DataAssetController {

    private final DataAssetService dataAssetService;
    private final AssetGraphService assetGraphService;

    public DataAssetController(DataAssetService dataAssetService, AssetGraphService assetGraphService) {
        this.dataAssetService = dataAssetService;
        this.assetGraphService = assetGraphService;
    }

    @GetMapping("/assets")
    public List<DataAsset> listAssets() {
        return dataAssetService.listAll();
    }

    @GetMapping("/assets/{id}")
    public DataAsset getAsset(@PathVariable Long id) {
        return dataAssetService.getById(id);
    }

    /**
     * 一键成图：返回资产视角的 GraphDTO，供前端 X6 自动布局渲染。
     *
     * @param includeAuxiliary 是否包含辅助流向（purpose=AUX 或 isPrimary=false）
     */
    @GetMapping("/assets/{id}/graph")
    public AssetGraphDto getAssetGraph(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeAuxiliary) {
        return assetGraphService.buildGraph(id, includeAuxiliary);
    }
}
