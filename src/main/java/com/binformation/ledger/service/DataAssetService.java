package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.asset.DataAssetSaveRequest;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.DerivationInput;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.DerivationInputMapper;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.FlowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class DataAssetService {

    private static final Set<String> DATA_TYPES = Set.of("FILE", "KAFKA_MESSAGE");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED");

    private final DataAssetMapper dataAssetMapper;
    private final FlowMapper flowMapper;
    private final DerivationMapper derivationMapper;
    private final DerivationInputMapper derivationInputMapper;
    private final ChangeLogService changeLogService;

    public DataAssetService(
            DataAssetMapper dataAssetMapper,
            FlowMapper flowMapper,
            DerivationMapper derivationMapper,
            DerivationInputMapper derivationInputMapper,
            ChangeLogService changeLogService) {
        this.dataAssetMapper = dataAssetMapper;
        this.flowMapper = flowMapper;
        this.derivationMapper = derivationMapper;
        this.derivationInputMapper = derivationInputMapper;
        this.changeLogService = changeLogService;
    }

    public List<DataAsset> listAll() {
        return dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>().orderByAsc(DataAsset::getId));
    }

    public DataAsset getById(Long id) {
        DataAsset asset = dataAssetMapper.selectById(id);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + id);
        }
        return asset;
    }

    @Transactional
    public DataAsset create(DataAssetSaveRequest request) {
        validate(request);
        ensureCodeUnique(request.code().trim(), null);
        LocalDateTime now = LocalDateTime.now();
        DataAsset asset = new DataAsset();
        apply(asset, request);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        dataAssetMapper.insert(asset);
        changeLogService.record("DATA_ASSET", asset.getId(), "CREATE",
                "新建数据资产: " + asset.getName(), asset.getId());
        return asset;
    }

    @Transactional
    public DataAsset update(Long id, DataAssetSaveRequest request) {
        DataAsset asset = getById(id);
        validate(request);
        ensureCodeUnique(request.code().trim(), id);
        apply(asset, request);
        asset.setUpdatedAt(LocalDateTime.now());
        dataAssetMapper.updateById(asset);
        changeLogService.record("DATA_ASSET", id, "UPDATE",
                "更新数据资产: " + asset.getName(), id);
        return asset;
    }

    @Transactional
    public void delete(Long id) {
        DataAsset asset = getById(id);
        Long flowCount = flowMapper.selectCount(
                new LambdaQueryWrapper<Flow>().eq(Flow::getAssetId, id));
        if (flowCount != null && flowCount > 0) {
            throw new BadRequestException("该资产下仍有 " + flowCount + " 条流向，请先删除或迁移流向");
        }
        Long outputDerivations = derivationMapper.selectCount(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, id));
        if (outputDerivations != null && outputDerivations > 0) {
            throw new BadRequestException("该资产仍被 " + outputDerivations + " 条派生作为输出，请先处理派生");
        }
        Long inputRefs = derivationInputMapper.selectCount(
                new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getInputAssetId, id));
        if (inputRefs != null && inputRefs > 0) {
            throw new BadRequestException("该资产仍被 " + inputRefs + " 条派生输入引用，请先处理派生");
        }
        dataAssetMapper.deleteById(id);
        changeLogService.record("DATA_ASSET", id, "DELETE",
                "删除数据资产: " + asset.getName(), id);
    }

    private void validate(DataAssetSaveRequest request) {
        if (!DATA_TYPES.contains(request.dataType().trim().toUpperCase())) {
            throw new BadRequestException("无效的数据类型: " + request.dataType());
        }
        if (!STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BadRequestException("无效的状态: " + request.status());
        }
    }

    private void apply(DataAsset asset, DataAssetSaveRequest request) {
        asset.setName(request.name().trim());
        asset.setCode(request.code().trim());
        asset.setDataType(request.dataType().trim().toUpperCase());
        asset.setStatus(request.status().trim().toUpperCase());
        asset.setOwner(request.owner());
        asset.setRemark(request.remark());
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        DataAsset existing = dataAssetMapper.selectOne(
                new LambdaQueryWrapper<DataAsset>().eq(DataAsset::getCode, code));
        if (existing != null && (excludeId == null || !existing.getId().equals(excludeId))) {
            throw new BadRequestException("资产编码已存在: " + code);
        }
    }
}
