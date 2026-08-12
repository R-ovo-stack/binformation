package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.endpoint.EndpointDetailDto;
import com.binformation.ledger.dto.endpoint.EndpointSaveRequest;
import com.binformation.ledger.dto.flow.EndpointOptionDto;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.entity.FlowStep;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.mapper.FlowMapper;
import com.binformation.ledger.mapper.FlowStepMapper;
import com.binformation.ledger.support.EndpointHierarchy;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EndpointService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED");

    private final EndpointMapper endpointMapper;
    private final FlowMapper flowMapper;
    private final FlowStepMapper flowStepMapper;
    private final ExecutorMapper executorMapper;
    private final DerivationMapper derivationMapper;
    private final ChangeLogService changeLogService;

    public EndpointService(
            EndpointMapper endpointMapper,
            FlowMapper flowMapper,
            FlowStepMapper flowStepMapper,
            ExecutorMapper executorMapper,
            DerivationMapper derivationMapper,
            ChangeLogService changeLogService) {
        this.endpointMapper = endpointMapper;
        this.flowMapper = flowMapper;
        this.flowStepMapper = flowStepMapper;
        this.executorMapper = executorMapper;
        this.derivationMapper = derivationMapper;
        this.changeLogService = changeLogService;
    }

    public List<EndpointDetailDto> listAll(String type, Long parentId) {
        LambdaQueryWrapper<Endpoint> query = new LambdaQueryWrapper<Endpoint>()
                .orderByAsc(Endpoint::getType)
                .orderByAsc(Endpoint::getName);
        if (type != null && !type.isBlank()) {
            query.eq(Endpoint::getType, type.trim());
        }
        if (parentId != null) {
            query.eq(Endpoint::getParentId, parentId);
        }
        Map<Long, Endpoint> all = loadAllMap();
        return endpointMapper.selectList(query).stream()
                .map(ep -> toDetail(ep, all))
                .toList();
    }

    public List<EndpointOptionDto> listOptions(String type) {
        LambdaQueryWrapper<Endpoint> query = new LambdaQueryWrapper<Endpoint>()
                .orderByAsc(Endpoint::getType)
                .orderByAsc(Endpoint::getName);
        if (type != null && !type.isBlank()) {
            query.eq(Endpoint::getType, type.trim());
        }
        Map<Long, Endpoint> all = loadAllMap();
        return endpointMapper.selectList(query).stream()
                .map(ep -> toOption(ep, all))
                .toList();
    }

    public EndpointDetailDto getDetail(Long id) {
        Endpoint endpoint = requireById(id);
        return toDetail(endpoint, loadAllMap());
    }

    @Transactional
    public EndpointDetailDto create(EndpointSaveRequest request) {
        validateRequest(request, null);
        LocalDateTime now = LocalDateTime.now();
        Endpoint endpoint = new Endpoint();
        apply(endpoint, request, null);
        endpoint.setCreatedAt(now);
        endpoint.setUpdatedAt(now);
        insertEndpoint(endpoint);
        changeLogService.record("ENDPOINT", endpoint.getId(), "CREATE",
                "新建落点: " + endpoint.getName(), null);
        return getDetail(endpoint.getId());
    }

    @Transactional
    public EndpointDetailDto update(Long id, EndpointSaveRequest request) {
        Endpoint endpoint = requireById(id);
        validateRequest(request, id);
        apply(endpoint, request, id);
        endpoint.setUpdatedAt(LocalDateTime.now());
        try {
            endpointMapper.updateById(endpoint);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("同父级下已存在相同类型与名称的落点");
        }
        refreshZoneForDescendants(endpoint.getId());
        changeLogService.record("ENDPOINT", id, "UPDATE",
                "更新落点: " + endpoint.getName(), null);
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id) {
        Endpoint endpoint = requireById(id);
        Long childCount = endpointMapper.selectCount(
                new LambdaQueryWrapper<Endpoint>().eq(Endpoint::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BadRequestException("该落点下仍有 " + childCount + " 个子落点，请先删除子节点");
        }
        assertNotReferenced(id);
        endpointMapper.deleteById(id);
        changeLogService.record("ENDPOINT", id, "DELETE",
                "删除落点: " + endpoint.getName(), null);
    }

    public Endpoint requireById(Long id) {
        Endpoint endpoint = endpointMapper.selectById(id);
        if (endpoint == null) {
            throw new ResourceNotFoundException("落点不存在: " + id);
        }
        return endpoint;
    }

    public String labelFor(Long endpointId, Map<Long, Endpoint> all) {
        if (endpointId == null) {
            return null;
        }
        Endpoint endpoint = all.get(endpointId);
        if (endpoint == null) {
            endpoint = endpointMapper.selectById(endpointId);
        }
        if (endpoint == null) {
            return "#" + endpointId;
        }
        Map<Long, Endpoint> map = all.containsKey(endpointId) ? all : loadAllMap();
        return EndpointSupport.buildBreadcrumb(endpoint, map) + " / " + endpoint.getName();
    }

    private void validateRequest(EndpointSaveRequest request, Long existingId) {
        String type = request.type().trim().toUpperCase();
        if (!EndpointHierarchy.ENDPOINT_TYPES.contains(type)) {
            throw new BadRequestException("无效的落点类型: " + request.type());
        }
        if (!STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BadRequestException("无效的状态: " + request.status());
        }
        if (EndpointHierarchy.isSecurityZone(type)) {
            if (request.parentId() != null) {
                throw new BadRequestException("安全区不能有父落点");
            }
            ensureSecurityZoneNameUnique(request.name().trim(), existingId);
            return;
        }
        if (request.parentId() == null) {
            throw new BadRequestException("非安全区落点必须选择父落点");
        }
        Endpoint parent = requireById(request.parentId());
        EndpointHierarchy.validateParentType(type, parent.getType());
        if (existingId != null && existingId.equals(request.parentId())) {
            throw new BadRequestException("父落点不能是自身");
        }
        if (existingId != null && isDescendant(request.parentId(), existingId)) {
            throw new BadRequestException("父落点不能是当前节点的子节点");
        }
    }

    private void apply(Endpoint endpoint, EndpointSaveRequest request, Long existingId) {
        String type = request.type().trim().toUpperCase();
        endpoint.setType(type);
        endpoint.setName(request.name().trim());
        endpoint.setCode(request.code());
        endpoint.setAttrs(request.attrs());
        endpoint.setStatus(request.status().trim().toUpperCase());
        endpoint.setOwner(request.owner());
        endpoint.setRemark(request.remark());

        if (EndpointHierarchy.isSecurityZone(type)) {
            endpoint.setParentId(null);
            if (endpoint.getId() != null) {
                endpoint.setZoneId(endpoint.getId());
            }
        } else {
            endpoint.setParentId(request.parentId());
            Map<Long, Endpoint> all = loadAllMap();
            Endpoint parent = all.get(request.parentId());
            if (parent == null) {
                parent = requireById(request.parentId());
                all.put(parent.getId(), parent);
            }
            Endpoint zone = EndpointSupport.resolveZone(parent, all);
            endpoint.setZoneId(zone == null ? null : zone.getId());
        }
    }

    private void insertEndpoint(Endpoint endpoint) {
        try {
            endpointMapper.insert(endpoint);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("同父级下已存在相同类型与名称的落点");
        }
        if (EndpointHierarchy.isSecurityZone(endpoint.getType())) {
            endpoint.setZoneId(endpoint.getId());
            endpointMapper.updateById(endpoint);
        }
    }

    private void ensureSecurityZoneNameUnique(String name, Long excludeId) {
        List<Endpoint> zones = endpointMapper.selectList(
                new LambdaQueryWrapper<Endpoint>()
                        .eq(Endpoint::getType, EndpointSupport.TYPE_SECURITY_ZONE)
                        .eq(Endpoint::getName, name));
        for (Endpoint zone : zones) {
            if (excludeId == null || !zone.getId().equals(excludeId)) {
                throw new BadRequestException("安全区名称已存在: " + name);
            }
        }
    }

    private boolean isDescendant(Long candidateParentId, Long endpointId) {
        Map<Long, Endpoint> all = loadAllMap();
        Endpoint current = all.get(candidateParentId);
        while (current != null && current.getParentId() != null) {
            if (current.getId().equals(endpointId)) {
                return true;
            }
            current = all.get(current.getParentId());
        }
        return false;
    }

    private void refreshZoneForDescendants(Long endpointId) {
        List<Endpoint> children = endpointMapper.selectList(
                new LambdaQueryWrapper<Endpoint>().eq(Endpoint::getParentId, endpointId));
        if (children.isEmpty()) {
            return;
        }
        Map<Long, Endpoint> all = loadAllMap();
        for (Endpoint child : children) {
            Endpoint zone = EndpointSupport.resolveZone(child, all);
            child.setZoneId(zone == null ? null : zone.getId());
            child.setUpdatedAt(LocalDateTime.now());
            endpointMapper.updateById(child);
            refreshZoneForDescendants(child.getId());
        }
    }

    private void assertNotReferenced(Long id) {
        Long asSource = flowMapper.selectCount(
                new LambdaQueryWrapper<Flow>().eq(Flow::getSourceEndpointId, id));
        Long asTarget = flowMapper.selectCount(
                new LambdaQueryWrapper<Flow>().eq(Flow::getTargetEndpointId, id));
        if ((asSource != null && asSource > 0) || (asTarget != null && asTarget > 0)) {
            throw new BadRequestException("该落点仍被流向引用，无法删除");
        }
        Long asHost = flowStepMapper.selectCount(
                new LambdaQueryWrapper<FlowStep>().eq(FlowStep::getHostId, id));
        if (asHost != null && asHost > 0) {
            throw new BadRequestException("该落点仍被流向步骤引用，无法删除");
        }
        Long asDefaultHost = executorMapper.selectCount(
                new LambdaQueryWrapper<Executor>().eq(Executor::getDefaultHostId, id));
        if (asDefaultHost != null && asDefaultHost > 0) {
            throw new BadRequestException("该落点仍被程序/脚本引用，无法删除");
        }
        Long asDerivationHost = derivationMapper.selectCount(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getHostId, id));
        if (asDerivationHost != null && asDerivationHost > 0) {
            throw new BadRequestException("该落点仍被派生加工引用，无法删除");
        }
    }

    private EndpointDetailDto toDetail(Endpoint ep, Map<Long, Endpoint> all) {
        Endpoint parent = ep.getParentId() == null ? null : all.get(ep.getParentId());
        Endpoint zone = ep.getZoneId() == null ? null : all.get(ep.getZoneId());
        return new EndpointDetailDto(
                ep.getId(),
                ep.getType(),
                ep.getName(),
                ep.getCode(),
                ep.getParentId(),
                parent == null ? null : parent.getName(),
                ep.getZoneId(),
                zone == null ? null : zone.getName(),
                EndpointSupport.buildBreadcrumb(ep, all),
                ep.getAttrs(),
                ep.getStatus(),
                ep.getOwner(),
                ep.getRemark()
        );
    }

    private EndpointOptionDto toOption(Endpoint ep, Map<Long, Endpoint> all) {
        Endpoint zone = EndpointSupport.resolveZone(ep, all);
        return new EndpointOptionDto(
                ep.getId(),
                ep.getType(),
                ep.getName(),
                ep.getParentId(),
                EndpointSupport.buildBreadcrumb(ep, all),
                zone == null ? null : zone.getId(),
                zone == null ? null : zone.getName()
        );
    }

    private Map<Long, Endpoint> loadAllMap() {
        return endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
    }
}
