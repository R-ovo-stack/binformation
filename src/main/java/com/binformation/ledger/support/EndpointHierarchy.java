package com.binformation.ledger.support;

import com.binformation.ledger.exception.BadRequestException;

import java.util.Map;
import java.util.Set;

/**
 * 落点层级与父子类型约束（见 docs/schema-design.md）。
 */
public final class EndpointHierarchy {

    private static final Map<String, Set<String>> ALLOWED_PARENT_TYPES = Map.ofEntries(
            Map.entry("SYSTEM", Set.of(EndpointSupport.TYPE_SECURITY_ZONE)),
            Map.entry("KAFKA", Set.of("SYSTEM")),
            Map.entry("ROCKETMQ", Set.of("SYSTEM")),
            Map.entry("OBJECT_STORAGE", Set.of("SYSTEM")),
            Map.entry("HOST", Set.of("SYSTEM")),
            Map.entry("HTTP_API", Set.of("SYSTEM")),
            Map.entry("KAFKA_TOPIC", Set.of("KAFKA")),
            Map.entry("ROCKETMQ_TOPIC", Set.of("ROCKETMQ")),
            Map.entry("OBJECT_BUCKET", Set.of("OBJECT_STORAGE")),
            Map.entry("OBJECT_PREFIX", Set.of("OBJECT_BUCKET")),
            Map.entry("DIRECTORY", Set.of("HOST"))
    );

    public static final Set<String> ENDPOINT_TYPES = Set.of(
            EndpointSupport.TYPE_SECURITY_ZONE,
            "SYSTEM",
            "KAFKA",
            "ROCKETMQ",
            "OBJECT_STORAGE",
            "HOST",
            "HTTP_API",
            "KAFKA_TOPIC",
            "ROCKETMQ_TOPIC",
            "OBJECT_BUCKET",
            "OBJECT_PREFIX",
            "DIRECTORY"
    );

    private EndpointHierarchy() {
    }

    public static void validateParentType(String childType, String parentType) {
        if (EndpointSupport.TYPE_SECURITY_ZONE.equals(childType)) {
            throw new BadRequestException("安全区不能有父落点");
        }
        Set<String> allowed = ALLOWED_PARENT_TYPES.get(childType);
        if (allowed == null) {
            throw new BadRequestException("未知落点类型: " + childType);
        }
        if (!allowed.contains(parentType)) {
            throw new BadRequestException(
                    "类型 " + childType + " 的父落点必须是: " + String.join(" / ", allowed));
        }
    }

    public static boolean isSecurityZone(String type) {
        return EndpointSupport.TYPE_SECURITY_ZONE.equals(type);
    }

    public static Set<String> allowedParentTypes(String childType) {
        if (isSecurityZone(childType)) {
            return Set.of();
        }
        return ALLOWED_PARENT_TYPES.getOrDefault(childType, Set.of());
    }
}
