package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.changelog.ChangeLogDto;
import com.binformation.ledger.dto.changelog.ChangeLogItemDto;
import com.binformation.ledger.entity.ChangeLog;
import com.binformation.ledger.entity.ChangeLogItem;
import com.binformation.ledger.mapper.ChangeLogItemMapper;
import com.binformation.ledger.mapper.ChangeLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChangeLogService {

    private static final String DEFAULT_OPERATOR = "system";

    private final ChangeLogMapper changeLogMapper;
    private final ChangeLogItemMapper changeLogItemMapper;

    public ChangeLogService(ChangeLogMapper changeLogMapper, ChangeLogItemMapper changeLogItemMapper) {
        this.changeLogMapper = changeLogMapper;
        this.changeLogItemMapper = changeLogItemMapper;
    }

    @Transactional
    public void record(
            String entityType,
            Long entityId,
            String action,
            String summary,
            Long relatedAssetId) {
        record(entityType, entityId, action, summary, relatedAssetId, List.of());
    }

    @Transactional
    public void record(
            String entityType,
            Long entityId,
            String action,
            String summary,
            Long relatedAssetId,
            List<ChangeLogItemDto> items) {
        ChangeLog log = new ChangeLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setSummary(summary);
        log.setOperator(DEFAULT_OPERATOR);
        log.setOperatedAt(LocalDateTime.now());
        log.setRelatedAssetId(relatedAssetId);
        changeLogMapper.insert(log);

        for (ChangeLogItemDto item : items) {
            ChangeLogItem row = new ChangeLogItem();
            row.setChangeLogId(log.getId());
            row.setFieldName(item.fieldName());
            row.setOldValue(item.oldValue());
            row.setNewValue(item.newValue());
            changeLogItemMapper.insert(row);
        }
    }

    public List<ChangeLogDto> listByAsset(Long assetId) {
        List<ChangeLog> logs = changeLogMapper.selectList(
                new LambdaQueryWrapper<ChangeLog>()
                        .eq(ChangeLog::getRelatedAssetId, assetId)
                        .orderByDesc(ChangeLog::getOperatedAt)
                        .orderByDesc(ChangeLog::getId));
        return logs.stream().map(this::toDto).toList();
    }

    private ChangeLogDto toDto(ChangeLog log) {
        List<ChangeLogItem> items = changeLogItemMapper.selectList(
                new LambdaQueryWrapper<ChangeLogItem>().eq(ChangeLogItem::getChangeLogId, log.getId()));
        List<ChangeLogItemDto> itemDtos = items.stream()
                .map(i -> new ChangeLogItemDto(i.getFieldName(), i.getOldValue(), i.getNewValue()))
                .toList();
        return new ChangeLogDto(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getSummary(),
                log.getOperator(),
                log.getOperatedAt(),
                log.getRelatedAssetId(),
                log.getRemark(),
                itemDtos
        );
    }
}
