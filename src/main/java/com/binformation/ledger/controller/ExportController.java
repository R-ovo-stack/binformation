package com.binformation.ledger.controller;

import com.binformation.ledger.dto.export.FullLedgerExportDto;
import com.binformation.ledger.service.LedgerCsvExportService;
import com.binformation.ledger.service.LedgerExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final LedgerExportService ledgerExportService;
    private final LedgerCsvExportService ledgerCsvExportService;
    private final ObjectMapper objectMapper;

    public ExportController(
            LedgerExportService ledgerExportService,
            LedgerCsvExportService ledgerCsvExportService) {
        this.ledgerExportService = ledgerExportService;
        this.ledgerCsvExportService = ledgerCsvExportService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 全量导出：全部落点 + 全部数据资产及其流向（含路径/步骤），附程序与派生。
     *
     * @param format json（默认，嵌套完整结构）或 zip（CSV 多文件包）
     */
    @GetMapping("/full")
    public ResponseEntity<byte[]> exportFull(@RequestParam(defaultValue = "json") String format)
            throws IOException {
        String ts = LocalDateTime.now().format(FILE_TS);
        if ("zip".equalsIgnoreCase(format) || "csv".equalsIgnoreCase(format)) {
            byte[] body = ledgerCsvExportService.buildZipExport();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"ledger-export-" + ts + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(body);
        }

        FullLedgerExportDto payload = ledgerExportService.buildFullExport();
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ledger-export-" + ts + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
