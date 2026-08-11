package com.binformation.ledger.controller;

import com.binformation.ledger.dto.asset.DataAssetSaveRequest;
import com.binformation.ledger.dto.graph.AssetGraphDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.service.AssetGraphService;
import com.binformation.ledger.service.DataAssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public DataAsset createAsset(@Valid @RequestBody DataAssetSaveRequest request) {
        return dataAssetService.create(request);
    }

    @PutMapping("/assets/{id}")
    public DataAsset updateAsset(
            @PathVariable Long id,
            @Valid @RequestBody DataAssetSaveRequest request) {
        return dataAssetService.update(id, request);
    }

    @DeleteMapping("/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAsset(@PathVariable Long id) {
        dataAssetService.delete(id);
    }

    /**
     * 一键成图：返回资产视角的 GraphDTO，供前端 X6 自动布局渲染。
     */
    @GetMapping("/assets/{id}/graph")
    public AssetGraphDto getAssetGraph(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeAuxiliary,
            @RequestParam(defaultValue = "false") boolean includeUpstream) {
        return assetGraphService.buildGraph(id, includeAuxiliary, includeUpstream);
    }
}
