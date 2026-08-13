package com.binformation.ledger.dto.graph;

/**
 * 拓扑关系边（非业务流向），如 Kafka 包含主题、Kafka 关联 Broker 节点。
 */
public record GraphRelationDto(
        String id,
        String source,
        String target,
        String type,
        String label
) {
}
