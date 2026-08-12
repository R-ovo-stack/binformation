package com.binformation.ledger.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CsvImportSupport {

    private CsvImportSupport() {
    }

    public static List<Map<String, String>> parseRows(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<String[]> table = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            table.add(parseLine(line));
        }
        if (table.isEmpty()) {
            return List.of();
        }
        String[] headers = table.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            String[] values = table.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length; c++) {
                String key = normalizeHeader(headers[c]);
                if (key.isEmpty()) {
                    continue;
                }
                row.put(key, c < values.length ? values[c].trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT);
    }

    static String[] parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells.toArray(String[]::new);
    }

    public static String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key.toLowerCase(Locale.ROOT));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
