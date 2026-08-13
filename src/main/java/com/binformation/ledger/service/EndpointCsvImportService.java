package com.binformation.ledger.service;

import com.binformation.ledger.dto.endpoint.EndpointDetailDto;
import com.binformation.ledger.dto.endpoint.EndpointImportResultDto;
import com.binformation.ledger.dto.endpoint.EndpointImportRowError;
import com.binformation.ledger.dto.endpoint.EndpointSaveRequest;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.support.CsvExportSupport;
import com.binformation.ledger.support.CsvImportSupport;
import com.binformation.ledger.support.EndpointHierarchy;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EndpointCsvImportService {

    private static final String[] TEMPLATE_HEADERS = {
            "type", "name", "parentPath", "code", "status", "owner", "remark", "attrs"
    };

    private final EndpointService endpointService;
    private final EndpointMapper endpointMapper;

    public EndpointCsvImportService(EndpointService endpointService, EndpointMapper endpointMapper) {
        this.endpointService = endpointService;
        this.endpointMapper = endpointMapper;
    }

    public byte[] buildTemplateCsv() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "# 落点导入模板：parentPath 为父落点完整路径（用 \" / \" 分隔），安全区留空。"
        });
        rows.add(TEMPLATE_HEADERS);
        rows.add(new String[] {
                "SECURITY_ZONE", "示例安全区", "", "ZONE_DEMO", "ACTIVE", "owner@example.com", "安全区示例", ""
        });
        rows.add(new String[] {
                "SYSTEM", "示例系统", "示例安全区", "SYS_DEMO", "ACTIVE", "", "系统示例", ""
        });
        rows.add(new String[] {
                "HOST", "demo-host-01", "示例安全区 / 示例系统", "HOST_DEMO", "ACTIVE", "", "", ""
        });
        rows.add(new String[] {
                "DIRECTORY",
                "/data/demo",
                "示例安全区 / 示例系统 / demo-host-01",
                "",
                "ACTIVE",
                "",
                "目录示例",
                "{\"dirPath\":\"/data/demo\"}"
        });
        return CsvExportSupport.toCsvBytes(rows);
    }

    public EndpointImportResultDto importCsv(byte[] bytes) {
        List<Map<String, String>> rows = CsvImportSupport.parseRows(bytes);
        if (rows.isEmpty()) {
            throw new BadRequestException("CSV 文件为空或缺少数据行");
        }

        Map<Long, Endpoint> endpointMap = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
        Map<String, Long> pathToId = buildPathIndex(endpointMap);

        List<IndexedRow> ordered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ordered.add(new IndexedRow(i + 2, rows.get(i)));
        }
        ordered.sort(Comparator.comparingInt(r -> depth(CsvImportSupport.value(r.row(), "parentpath", "parent_path"))));

        int created = 0;
        int skipped = 0;
        List<EndpointImportRowError> errors = new ArrayList<>();

        for (IndexedRow indexed : ordered) {
            Map<String, String> row = indexed.row();
            String name = CsvImportSupport.value(row, "name");
            String type = CsvImportSupport.value(row, "type").toUpperCase(Locale.ROOT);
            String parentPath = CsvImportSupport.value(row, "parentpath", "parent_path");
            String status = CsvImportSupport.value(row, "status");
            if (status.isBlank()) {
                status = "ACTIVE";
            }

            if (name.isBlank() || type.isBlank()) {
                errors.add(new EndpointImportRowError(indexed.line(), name, "type 与 name 不能为空"));
                continue;
            }

            String fullPath = parentPath.isBlank() ? name : parentPath + " / " + name;
            if (pathToId.containsKey(fullPath)) {
                skipped++;
                errors.add(new EndpointImportRowError(indexed.line(), name, "路径已存在，已跳过: " + fullPath));
                continue;
            }

            Long parentId = null;
            try {
                if (EndpointHierarchy.isSecurityZone(type)) {
                    if (!parentPath.isBlank()) {
                        throw new BadRequestException("安全区不能有 parentPath");
                    }
                } else {
                    if (parentPath.isBlank()) {
                        throw new BadRequestException("非安全区必须填写 parentPath");
                    }
                    parentId = pathToId.get(parentPath);
                    if (parentId == null) {
                        throw new BadRequestException("找不到父落点: " + parentPath);
                    }
                }

                EndpointSaveRequest request = new EndpointSaveRequest(
                        type,
                        name,
                        emptyToNull(CsvImportSupport.value(row, "code")),
                        parentId,
                        emptyToNull(CsvImportSupport.value(row, "attrs")),
                        status,
                        emptyToNull(CsvImportSupport.value(row, "owner")),
                        emptyToNull(CsvImportSupport.value(row, "remark"))
                );
                EndpointDetailDto createdEndpoint = endpointService.create(request);
                endpointMap.put(createdEndpoint.id(), toEntity(createdEndpoint));
                pathToId.put(fullPath, createdEndpoint.id());
                created++;
            } catch (RuntimeException ex) {
                String message = ex.getMessage() == null ? "导入失败" : ex.getMessage();
                errors.add(new EndpointImportRowError(indexed.line(), name, message));
            }
        }

        return new EndpointImportResultDto(rows.size(), created, skipped, errors);
    }

    private Map<String, Long> buildPathIndex(Map<Long, Endpoint> endpointMap) {
        Map<String, Long> pathToId = new HashMap<>();
        for (Endpoint endpoint : endpointMap.values()) {
            pathToId.put(EndpointSupport.buildBreadcrumb(endpoint, endpointMap), endpoint.getId());
        }
        return pathToId;
    }

    private static int depth(String parentPath) {
        if (parentPath == null || parentPath.isBlank()) {
            return 0;
        }
        return parentPath.split(" / ", -1).length;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Endpoint toEntity(EndpointDetailDto dto) {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(dto.id());
        endpoint.setType(dto.type());
        endpoint.setName(dto.name());
        endpoint.setCode(dto.code());
        endpoint.setParentId(dto.parentId());
        endpoint.setZoneId(dto.zoneId());
        endpoint.setAttrs(dto.attrs());
        endpoint.setStatus(dto.status());
        endpoint.setOwner(dto.owner());
        endpoint.setRemark(dto.remark());
        return endpoint;
    }

    private record IndexedRow(int line, Map<String, String> row) {
    }
}
