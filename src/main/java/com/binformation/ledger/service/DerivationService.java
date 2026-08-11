package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.derivation.DerivationDetailDto;
import com.binformation.ledger.dto.derivation.DerivationInputItemDto;
import com.binformation.ledger.dto.derivation.DerivationInputSaveRequest;
import com.binformation.ledger.dto.derivation.DerivationSaveRequest;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.DerivationInput;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.DerivationInputMapper;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DerivationService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED");

    private final DerivationMapper derivationMapper;
    private final DerivationInputMapper derivationInputMapper;
    private final DataAssetMapper dataAssetMapper;
    private final ExecutorMapper executorMapper;
    private final EndpointMapper endpointMapper;
    private final ChangeLogService changeLogService;

    public DerivationService(
            DerivationMapper derivationMapper,
            DerivationInputMapper derivationInputMapper,
            DataAssetMapper dataAssetMapper,
            ExecutorMapper executorMapper,
            EndpointMapper endpointMapper,
            ChangeLogService changeLogService) {
        this.derivationMapper = derivationMapper;
        this.derivationInputMapper = derivationInputMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.executorMapper = executorMapper;
        this.endpointMapper = endpointMapper;
        this.changeLogService = changeLogService;
    }

    public List<DerivationDetailDto> listByOutputAsset(Long outputAssetId) {
        requireAsset(outputAssetId);
        List<Derivation> list = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>()
                        .eq(Derivation::getOutputAssetId, outputAssetId)
                        .orderByAsc(Derivation::getId));
        return list.stream().map(this::toDetail).toList();
    }

    public DerivationDetailDto getById(Long id) {
        Derivation d = requireDerivation(id);
        return toDetail(d);
    }

    @Transactional
    public DerivationDetailDto create(Long outputAssetId, DerivationSaveRequest request) {
        DataAsset output = requireAsset(outputAssetId);
        validate(request, outputAssetId);
        LocalDateTime now = LocalDateTime.now();
        Derivation d = new Derivation();
        d.setName(request.name().trim());
        d.setOutputAssetId(outputAssetId);
        d.setExecutorId(request.executorId());
        d.setHostId(request.hostId());
        d.setStatus(request.status().trim().toUpperCase());
        d.setOwner(request.owner());
        d.setRemark(request.remark());
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        derivationMapper.insert(d);
        saveInputs(d.getId(), request.inputs(), now);
        changeLogService.record("DERIVATION", d.getId(), "CREATE",
                "新建派生加工: " + d.getName(), outputAssetId);
        return toDetail(d);
    }

    @Transactional
    public DerivationDetailDto update(Long id, DerivationSaveRequest request) {
        Derivation d = requireDerivation(id);
        validate(request, d.getOutputAssetId());
        d.setName(request.name().trim());
        d.setExecutorId(request.executorId());
        d.setHostId(request.hostId());
        d.setStatus(request.status().trim().toUpperCase());
        d.setOwner(request.owner());
        d.setRemark(request.remark());
        d.setUpdatedAt(LocalDateTime.now());
        derivationMapper.updateById(d);
        deleteInputs(id);
        saveInputs(id, request.inputs(), LocalDateTime.now());
        changeLogService.record("DERIVATION", id, "UPDATE",
                "更新派生加工: " + d.getName(), d.getOutputAssetId());
        return toDetail(d);
    }

    @Transactional
    public void delete(Long id) {
        Derivation d = requireDerivation(id);
        derivationMapper.deleteById(id);
        changeLogService.record("DERIVATION", id, "DELETE",
                "删除派生加工: " + d.getName(), d.getOutputAssetId());
    }

    private void validate(DerivationSaveRequest request, Long outputAssetId) {
        if (!STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BadRequestException("无效的状态: " + request.status());
        }
        Executor executor = executorMapper.selectById(request.executorId());
        if (executor == null) {
            throw new BadRequestException("程序/脚本不存在: " + request.executorId());
        }
        if (request.hostId() != null) {
            Endpoint host = endpointMapper.selectById(request.hostId());
            if (host == null || !"HOST".equals(host.getType())) {
                throw new BadRequestException("部署主机必须是 HOST 类型落点");
            }
        }
        if (request.inputs().isEmpty()) {
            throw new BadRequestException("至少需要一个输入资产");
        }
        Set<Long> inputIds = new HashSet<>();
        for (DerivationInputSaveRequest in : request.inputs()) {
            if (Objects.equals(in.inputAssetId(), outputAssetId)) {
                throw new BadRequestException("输入资产不能与输出资产相同");
            }
            if (!inputIds.add(in.inputAssetId())) {
                throw new BadRequestException("输入资产不能重复");
            }
            if (dataAssetMapper.selectById(in.inputAssetId()) == null) {
                throw new BadRequestException("输入资产不存在: " + in.inputAssetId());
            }
        }
    }

    private void saveInputs(Long derivationId, List<DerivationInputSaveRequest> inputs, LocalDateTime now) {
        List<DerivationInputSaveRequest> sorted = new ArrayList<>(inputs);
        sorted.sort(Comparator.comparing(DerivationInputSaveRequest::sortOrder));
        for (DerivationInputSaveRequest in : sorted) {
            DerivationInput row = new DerivationInput();
            row.setDerivationId(derivationId);
            row.setInputAssetId(in.inputAssetId());
            row.setSortOrder(in.sortOrder() == null ? 0 : in.sortOrder());
            row.setCreatedAt(now);
            derivationInputMapper.insert(row);
        }
    }

    private void deleteInputs(Long derivationId) {
        derivationInputMapper.delete(
                new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getDerivationId, derivationId));
    }

    private DerivationDetailDto toDetail(Derivation d) {
        Map<Long, DataAsset> assets = dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity()));
        Executor executor = executorMapper.selectById(d.getExecutorId());
        Endpoint host = d.getHostId() == null ? null : endpointMapper.selectById(d.getHostId());
        DataAsset output = assets.get(d.getOutputAssetId());
        List<DerivationInput> inputs = derivationInputMapper.selectList(
                new LambdaQueryWrapper<DerivationInput>()
                        .eq(DerivationInput::getDerivationId, d.getId())
                        .orderByAsc(DerivationInput::getSortOrder));
        List<DerivationInputItemDto> inputDtos = inputs.stream()
                .map(in -> {
                    DataAsset asset = assets.get(in.getInputAssetId());
                    return new DerivationInputItemDto(
                            in.getInputAssetId(),
                            asset == null ? null : asset.getName(),
                            in.getSortOrder() == null ? 0 : in.getSortOrder()
                    );
                })
                .toList();
        return new DerivationDetailDto(
                d.getId(),
                d.getName(),
                d.getOutputAssetId(),
                output == null ? null : output.getName(),
                d.getExecutorId(),
                executor == null ? null : executor.getName(),
                d.getHostId(),
                host == null ? null : host.getName(),
                d.getStatus(),
                d.getOwner(),
                d.getRemark(),
                inputDtos
        );
    }

    private DataAsset requireAsset(Long id) {
        DataAsset asset = dataAssetMapper.selectById(id);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + id);
        }
        return asset;
    }

    private Derivation requireDerivation(Long id) {
        Derivation d = derivationMapper.selectById(id);
        if (d == null) {
            throw new ResourceNotFoundException("派生加工不存在: " + id);
        }
        return d;
    }
}
