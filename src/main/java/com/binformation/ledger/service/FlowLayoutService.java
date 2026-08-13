package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.layout.LayoutNodeSaveRequest;
import com.binformation.ledger.entity.FlowLayout;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.FlowLayoutMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FlowLayoutService {

    private final FlowLayoutMapper flowLayoutMapper;
    private final DataAssetService dataAssetService;
    private final EndpointMapper endpointMapper;
    private final ChangeLogService changeLogService;

    public FlowLayoutService(
            FlowLayoutMapper flowLayoutMapper,
            DataAssetService dataAssetService,
            EndpointMapper endpointMapper,
            ChangeLogService changeLogService) {
        this.flowLayoutMapper = flowLayoutMapper;
        this.dataAssetService = dataAssetService;
        this.endpointMapper = endpointMapper;
        this.changeLogService = changeLogService;
    }

    @Transactional
    public void saveLayout(Long assetId, List<LayoutNodeSaveRequest> nodes) {
        dataAssetService.getById(assetId);
        if (nodes.isEmpty()) {
            throw new BadRequestException("布局节点不能为空");
        }
        Set<Long> seen = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (LayoutNodeSaveRequest node : nodes) {
            if (!seen.add(node.endpointId())) {
                throw new BadRequestException("重复的落点布局: " + node.endpointId());
            }
            if (endpointMapper.selectById(node.endpointId()) == null) {
                throw new BadRequestException("落点不存在: " + node.endpointId());
            }
            FlowLayout existing = flowLayoutMapper.selectOne(
                    new LambdaQueryWrapper<FlowLayout>()
                            .eq(FlowLayout::getAssetId, assetId)
                            .eq(FlowLayout::getEndpointId, node.endpointId()));
            if (existing == null) {
                FlowLayout layout = new FlowLayout();
                layout.setAssetId(assetId);
                layout.setEndpointId(node.endpointId());
                layout.setLayoutX(node.layoutX());
                layout.setLayoutY(node.layoutY());
                layout.setUpdatedAt(now);
                flowLayoutMapper.insert(layout);
            } else {
                existing.setLayoutX(node.layoutX());
                existing.setLayoutY(node.layoutY());
                existing.setUpdatedAt(now);
                flowLayoutMapper.updateById(existing);
            }
        }
        changeLogService.record("FLOW_LAYOUT", assetId, "UPDATE",
                "保存成图布局 " + nodes.size() + " 个节点", assetId);
    }
}
