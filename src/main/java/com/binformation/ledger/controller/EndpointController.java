package com.binformation.ledger.controller;

import com.binformation.ledger.dto.endpoint.EndpointDetailDto;
import com.binformation.ledger.dto.endpoint.EndpointSaveRequest;
import com.binformation.ledger.dto.flow.EndpointOptionDto;
import com.binformation.ledger.service.EndpointService;
import com.binformation.ledger.support.EndpointHierarchy;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    /** 管理列表（含安全区） */
    @GetMapping
    public List<?> listEndpoints(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "false") boolean optionsOnly) {
        if (optionsOnly) {
            return endpointService.listOptions(type);
        }
        return endpointService.listAll(type, parentId);
    }

    @GetMapping("/meta/types")
    public Map<String, Object> endpointTypes() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("SECURITY_ZONE", "安全区");
        labels.put("SYSTEM", "系统");
        labels.put("KAFKA", "Kafka集群");
        labels.put("ROCKETMQ", "RocketMQ集群");
        labels.put("OBJECT_STORAGE", "对象存储");
        labels.put("HOST", "主机");
        labels.put("HTTP_API", "HTTP接口");
        labels.put("KAFKA_TOPIC", "Kafka主题");
        labels.put("ROCKETMQ_TOPIC", "RocketMQ主题");
        labels.put("OBJECT_BUCKET", "对象桶");
        labels.put("OBJECT_PREFIX", "对象目录");
        labels.put("DIRECTORY", "磁盘目录");
        return Map.of(
                "types", EndpointHierarchy.ENDPOINT_TYPES,
                "labels", labels
        );
    }

    @GetMapping("/meta/parent-types")
    public Map<String, Set<String>> parentTypes() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (String type : EndpointHierarchy.ENDPOINT_TYPES) {
            if (!EndpointHierarchy.isSecurityZone(type)) {
                map.put(type, EndpointHierarchy.allowedParentTypes(type));
            }
        }
        return map;
    }

    @GetMapping("/{id}")
    public EndpointDetailDto getEndpoint(@PathVariable Long id) {
        return endpointService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointDetailDto createEndpoint(@Valid @RequestBody EndpointSaveRequest request) {
        return endpointService.create(request);
    }

    @PutMapping("/{id}")
    public EndpointDetailDto updateEndpoint(
            @PathVariable Long id,
            @Valid @RequestBody EndpointSaveRequest request) {
        return endpointService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEndpoint(@PathVariable Long id) {
        endpointService.delete(id);
    }
}
