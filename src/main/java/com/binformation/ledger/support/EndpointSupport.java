package com.binformation.ledger.support;

import com.binformation.ledger.entity.Endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EndpointSupport {

    public static final String TYPE_SECURITY_ZONE = "SECURITY_ZONE";

    private EndpointSupport() {
    }

    public static String nodeId(Long endpointId) {
        return "ep-" + endpointId;
    }

    public static String edgeId(Long flowId) {
        return "flow-" + flowId;
    }

    public static String groupId(Long zoneEndpointId) {
        return "zone-" + zoneEndpointId;
    }

    public static Endpoint resolveZone(Endpoint endpoint, Map<Long, Endpoint> endpointMap) {
        if (endpoint == null) {
            return null;
        }
        if (TYPE_SECURITY_ZONE.equals(endpoint.getType())) {
            return endpoint;
        }
        if (endpoint.getZoneId() != null) {
            return endpointMap.get(endpoint.getZoneId());
        }
        Endpoint current = endpoint;
        while (current.getParentId() != null) {
            current = endpointMap.get(current.getParentId());
            if (current == null) {
                break;
            }
            if (TYPE_SECURITY_ZONE.equals(current.getType())) {
                return current;
            }
        }
        return null;
    }

    public static String buildBreadcrumb(Endpoint endpoint, Map<Long, Endpoint> endpointMap) {
        if (endpoint == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        Endpoint current = endpoint;
        while (current != null) {
            names.add(current.getName());
            if (current.getParentId() == null) {
                break;
            }
            current = endpointMap.get(current.getParentId());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }
}
