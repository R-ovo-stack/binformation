package com.binformation.ledger.service;

import com.binformation.ledger.dto.export.FullLedgerExportDto;
import com.binformation.ledger.dto.flow.FlowPathDto;
import com.binformation.ledger.dto.flow.FlowStepDto;
import com.binformation.ledger.support.CsvExportSupport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerCsvExportService {

    private final LedgerExportService ledgerExportService;

    public LedgerCsvExportService(LedgerExportService ledgerExportService) {
        this.ledgerExportService = ledgerExportService;
    }

    public byte[] buildZipExport() throws IOException {
        FullLedgerExportDto data = ledgerExportService.buildFullExport();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("endpoints.csv", buildEndpointsCsv(data));
        files.put("data_assets.csv", buildAssetsCsv(data));
        files.put("flows.csv", buildFlowsCsv(data));
        files.put("flow_paths.csv", buildFlowPathsCsv(data));
        files.put("flow_steps.csv", buildFlowStepsCsv(data));
        files.put("executors.csv", buildExecutorsCsv(data));
        files.put("derivations.csv", buildDerivationsCsv(data));
        files.put("derivation_inputs.csv", buildDerivationInputsCsv(data));
        files.put("README.txt", readmeBytes(data));
        return CsvExportSupport.zipEntries(files);
    }

    private byte[] readmeBytes(FullLedgerExportDto data) {
        String text = """
                数据中心台账全量导出（CSV 包）
                版本: %s
                导出时间: %s
                落点: %d | 资产: %d | 流向: %d | 派生: %d | 程序: %d

                文件说明:
                - endpoints.csv          全部落点
                - data_assets.csv        全部数据资产
                - flows.csv              流向（含源/目标落点 ID）
                - flow_paths.csv         路径
                - flow_steps.csv         步骤（含程序/主机）
                - executors.csv          程序/脚本
                - derivations.csv        派生定义
                - derivation_inputs.csv  派生输入资产

                完整嵌套结构请使用 format=json 导出。
                """.formatted(
                data.version(),
                data.exportedAt(),
                data.endpointCount(),
                data.assetCount(),
                data.flowCount(),
                data.derivationCount(),
                data.executorCount());
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] buildEndpointsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "id", "type", "name", "code", "parentId", "parentName", "zoneId", "zoneName",
                "breadcrumb", "attrs", "status", "owner", "remark"
        });
        data.endpoints().forEach(ep -> rows.add(new String[] {
                CsvExportSupport.str(ep.id()),
                ep.type(),
                ep.name(),
                ep.code(),
                CsvExportSupport.str(ep.parentId()),
                ep.parentName(),
                CsvExportSupport.str(ep.zoneId()),
                ep.zoneName(),
                ep.breadcrumb(),
                ep.attrs(),
                ep.status(),
                ep.owner(),
                ep.remark()
        }));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildAssetsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "id", "name", "code", "dataType", "status", "owner", "remark", "flowCount" });
        data.assets().forEach(a -> rows.add(new String[] {
                CsvExportSupport.str(a.id()),
                a.name(),
                a.code(),
                a.dataType(),
                a.status(),
                a.owner(),
                a.remark(),
                CsvExportSupport.str(a.flows().size())
        }));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildFlowsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "id", "assetId", "assetName", "sourceEndpointId", "sourceEndpointLabel",
                "targetEndpointId", "targetEndpointLabel", "purpose", "primary", "status", "owner", "remark"
        });
        data.assets().forEach(asset -> asset.flows().forEach(flow -> rows.add(new String[] {
                CsvExportSupport.str(flow.id()),
                CsvExportSupport.str(flow.assetId()),
                flow.assetName(),
                CsvExportSupport.str(flow.sourceEndpointId()),
                flow.sourceEndpointLabel(),
                CsvExportSupport.str(flow.targetEndpointId()),
                flow.targetEndpointLabel(),
                flow.purpose(),
                CsvExportSupport.str(flow.primary()),
                flow.status(),
                flow.owner(),
                flow.remark()
        })));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildFlowPathsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "id", "flowId", "assetId", "name", "enabled", "sortOrder", "remark" });
        data.assets().forEach(asset -> asset.flows().forEach(flow ->
                flow.paths().forEach(path -> rows.add(new String[] {
                        CsvExportSupport.str(path.id()),
                        CsvExportSupport.str(flow.id()),
                        CsvExportSupport.str(flow.assetId()),
                        path.name(),
                        CsvExportSupport.str(path.enabled()),
                        CsvExportSupport.str(path.sortOrder()),
                        path.remark()
                }))));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildFlowStepsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "id", "pathId", "flowId", "assetId", "seq", "executorId", "executorName",
                "hostId", "hostLabel", "method", "remark"
        });
        data.assets().forEach(asset -> asset.flows().forEach(flow ->
                flow.paths().forEach((FlowPathDto path) -> path.steps().forEach((FlowStepDto step) -> rows.add(new String[] {
                        CsvExportSupport.str(step.id()),
                        CsvExportSupport.str(path.id()),
                        CsvExportSupport.str(flow.id()),
                        CsvExportSupport.str(flow.assetId()),
                        CsvExportSupport.str(step.seq()),
                        CsvExportSupport.str(step.executorId()),
                        step.executorName(),
                        CsvExportSupport.str(step.hostId()),
                        step.hostLabel(),
                        step.method(),
                        step.remark()
                })))));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildExecutorsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "id", "name", "code", "kind", "defaultHostId", "defaultHostLabel", "status", "owner", "remark"
        });
        data.executors().forEach(ex -> rows.add(new String[] {
                CsvExportSupport.str(ex.id()),
                ex.name(),
                ex.code(),
                ex.kind(),
                CsvExportSupport.str(ex.defaultHostId()),
                ex.defaultHostLabel(),
                ex.status(),
                ex.owner(),
                ex.remark()
        }));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildDerivationsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {
                "id", "name", "outputAssetId", "outputAssetName", "executorId", "executorName",
                "hostId", "hostLabel", "status", "owner", "remark"
        });
        data.derivations().forEach(d -> rows.add(new String[] {
                CsvExportSupport.str(d.id()),
                d.name(),
                CsvExportSupport.str(d.outputAssetId()),
                d.outputAssetName(),
                CsvExportSupport.str(d.executorId()),
                d.executorName(),
                CsvExportSupport.str(d.hostId()),
                d.hostLabel(),
                d.status(),
                d.owner(),
                d.remark()
        }));
        return CsvExportSupport.toCsvBytes(rows);
    }

    private byte[] buildDerivationInputsCsv(FullLedgerExportDto data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "derivationId", "inputAssetId", "inputAssetName", "sortOrder" });
        data.derivations().forEach(d -> d.inputs().forEach(input -> rows.add(new String[] {
                CsvExportSupport.str(d.id()),
                CsvExportSupport.str(input.inputAssetId()),
                input.inputAssetName(),
                CsvExportSupport.str(input.sortOrder())
        })));
        return CsvExportSupport.toCsvBytes(rows);
    }
}
