package com.elyadata.feature.etl.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.etl.application.dto.ExportFormat;
import com.elyadata.feature.etl.application.dto.ExportRequest;
import com.elyadata.feature.etl.application.dto.ExportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class ExportService {

    private static final String DEFAULT_TABLE = "scrape_export";
    private static final String EXPORT_LOG_PREFIX = "[EXPORT]";

    private static final String CONTEXT_KEY = "@context";
    private static final String TYPE_KEY = "@type";
    private static final String SCHEMA_ORG_VOCAB = "https://schema.org/";
    private static final String TITLE_FIELD = "title";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PRICE_FIELD = "price";
    private static final String URL_FIELD = "url";
    private static final String IMAGE_FIELD = "image";

    private static final String PRODUCT_TYPE = "Product";
    private static final String ARTICLE_TYPE = "Article";
    private static final String EVENT_TYPE = "Event";
    private static final String JOB_POSTING_TYPE = "JobPosting";
    private static final String DEFAULT_SCHEMA_TYPE = "Thing";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\W");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");

    private final ExportStorageService storageService;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "app.base-url")
    String baseUrl;

    public ExportService(ExportStorageService storageService, ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    public ExportResponse export(List<Map<String, Object>> records, ExportRequest request) {
        long start = System.currentTimeMillis();
        log.info("{} Starting export: {} records → format={} destination={}",
                EXPORT_LOG_PREFIX, records.size(), request.format(), request.destination());

        try {
            return switch (request.destination()) {
                case FILE -> exportToFile(records, request.format(), start);
                case POSTGRESQL -> exportToPostgres(records, request.destinationConfig(), start);
                case WEBHOOK -> exportToWebhook(records, request.destinationConfig(), start);
            };
        } catch (SQLException e) {
            log.error("{} Database error during export: {}", EXPORT_LOG_PREFIX, e.getMessage(), e);
            throw DomainException.exportFailure("Database export failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("{} I/O error during export: {}", EXPORT_LOG_PREFIX, e.getMessage(), e);
            throw DomainException.exportFailure("File generation failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} Export interrupted", EXPORT_LOG_PREFIX, e);
            throw DomainException.exportFailure("Export was interrupted");
        }
    }

    private ExportResponse exportToFile(List<Map<String, Object>> records,
                                        ExportFormat format, long start) throws IOException {
        String timestamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = "export_" + timestamp + "." + resolveFileExtension(format);
        byte[] content = generateFileContent(records, format);
        String contentType = resolveContentType(format);
        String exportId = storageService.store(content, filename, contentType);
        String downloadUrl = baseUrl + "/v1/exports/" + exportId + "/download";

        long durationMs = System.currentTimeMillis() - start;
        log.info("{} FILE ready: id={} file={} size={}b {}ms",
                EXPORT_LOG_PREFIX, exportId, filename, content.length, durationMs);

        return new ExportResponse(downloadUrl, records.size(), durationMs,
                records.size() + " records exported as " + format);
    }

    private String resolveFileExtension(ExportFormat format) {
        return switch (format) {
            case CSV -> "csv";
            case JSON -> "json";
            case EXCEL -> "xlsx";
            case PARQUET -> "parquet";
            case MARKDOWN -> "md";
            case JSON_LD -> "jsonld";
            case LLMS_TXT -> "txt";
        };
    }

    private byte[] generateFileContent(List<Map<String, Object>> records,
                                       ExportFormat format) throws IOException {
        return switch (format) {
            case CSV -> buildCsv(records).getBytes(StandardCharsets.UTF_8);
            case JSON -> buildJson(records);
            case EXCEL -> buildExcel(records);
            case PARQUET -> buildParquet(records);
            case MARKDOWN -> buildMarkdown(records).getBytes(StandardCharsets.UTF_8);
            case JSON_LD -> buildJsonLd(records);
            case LLMS_TXT -> buildLlmsTxt(records).getBytes(StandardCharsets.UTF_8);
        };
    }

    private String buildCsv(List<Map<String, Object>> records) {
        if (records.isEmpty()) return "";
        List<String> headers = new ArrayList<>(records.getFirst().keySet());
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\r\n");
        for (Map<String, Object> entry : records) {
            String line = headers.stream()
                    .map(h -> escapeCsv(Objects.toString(entry.getOrDefault(h, ""), "")))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private byte[] buildJson(List<Map<String, Object>> records) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(records);
    }

    private byte[] buildExcel(List<Map<String, Object>> records) throws IOException {
        if (records.isEmpty()) return new byte[0];

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> headers = new ArrayList<>(records.getFirst().keySet());

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIdx = 0; rowIdx < records.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                Map<String, Object> entry = records.get(rowIdx);
                CellStyle rowStyle = (rowIdx % 2 == 0) ? evenStyle : null;
                for (int colIdx = 0; colIdx < headers.size(); colIdx++) {
                    Cell cell = row.createCell(colIdx);
                    setCellValue(cell, entry.get(headers.get(colIdx)));
                    if (rowStyle != null) cell.setCellStyle(rowStyle);
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i), 15000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellValue(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setCellValue("");
            case Number num -> cell.setCellValue(num.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            default -> cell.setCellValue(Objects.toString(value, ""));
        }
    }

    private byte[] buildParquet(List<Map<String, Object>> records) throws IOException {
        log.warn("{} Parquet not fully implemented — falling back to JSON", EXPORT_LOG_PREFIX);
        return buildJson(records);
    }

    private ExportResponse exportToPostgres(List<Map<String, Object>> records,
                                            Map<String, Object> config, long start) throws SQLException {
        if (records.isEmpty()) {
            return new ExportResponse(null, 0, 0L, "No records to export");
        }

        String jdbcUrl = resolveConfig(config, "jdbcUrl", null);
        String username = resolveConfig(config, "username", null);
        String password = resolveConfig(config, "password", null);
        String table = resolveConfig(config, "table", DEFAULT_TABLE);

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw DomainException.notValid(
                    "PostgreSQL export requires 'jdbcUrl' (e.g. jdbc:postgresql://host:5432/dbname)");
        }
        if (username == null || username.isBlank()) {
            throw DomainException.notValid("PostgreSQL export requires 'username'");
        }
        if (password == null) {
            throw DomainException.notValid("PostgreSQL export requires 'password'");
        }

        log.info("{} PostgreSQL → table={} records={}", EXPORT_LOG_PREFIX, table, records.size());

        List<String> columns = new ArrayList<>(records.getFirst().keySet());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            ensureTableExists(conn, table, columns, records.getFirst());
            int inserted = batchInsert(conn, table, columns, records);
            long durationMs = System.currentTimeMillis() - start;
            log.info("{} PostgreSQL: {} rows → '{}' in {}ms", EXPORT_LOG_PREFIX, inserted, table, durationMs);
            return new ExportResponse(null, inserted, durationMs,
                    inserted + " records inserted into table '" + table + "'");
        }
    }

    private void ensureTableExists(Connection conn, String table,
                                   List<String> columns, Map<String, Object> sample) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(quoteIdentifier(table))
                .append(" (id SERIAL PRIMARY KEY");
        for (String col : columns) {
            ddl.append(", ").append(quoteIdentifier(col))
                    .append(" ").append(inferPgType(sample.get(col)));
        }
        ddl.append(", exported_at TIMESTAMP DEFAULT NOW())");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
            log.debug("{} DDL: {}", EXPORT_LOG_PREFIX, ddl);
        }
    }

    private int batchInsert(Connection conn, String table,
                            List<String> columns, List<Map<String, Object>> records) throws SQLException {
        String colList = columns.stream().map(this::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElse("");
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + quoteIdentifier(table) + " (" + colList + ") VALUES (" + placeholders + ")";

        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> entry : records) {
                for (int i = 0; i < columns.size(); i++) {
                    ps.setObject(i + 1, entry.get(columns.get(i)));
                }
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            conn.commit();
            return Arrays.stream(counts).sum();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private String inferPgType(Object sample) {
        if (sample instanceof Integer || sample instanceof Long) return "BIGINT";
        if (sample instanceof Double || sample instanceof Float) return "DOUBLE PRECISION";
        if (sample instanceof Boolean) return "BOOLEAN";
        return "TEXT";
    }

    private String quoteIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private ExportResponse exportToWebhook(List<Map<String, Object>> records,
                                           Map<String, Object> config,
                                           long start) throws IOException, InterruptedException {
        String url = resolveConfig(config, "url", "");
        if (url.isBlank()) {
            throw DomainException.notValid("Webhook export requires 'url' in destinationConfig");
        }

        String authHeader = resolveConfig(config, "authHeader", null);
        int batchSize = Integer.parseInt(resolveConfig(config, "batchSize", "500"));
        int totalBatches = (int) Math.ceil((double) records.size() / batchSize);

        log.info("{} Webhook → url={} records={} batchSize={}", EXPORT_LOG_PREFIX, url, records.size(), batchSize);

        HttpClient client = HttpClient.newHttpClient();
        int totalSent = 0;

        for (int i = 0; i < records.size(); i += batchSize) {
            List<Map<String, Object>> batch = records.subList(i, Math.min(i + batchSize, records.size()));
            int batchNum = (i / batchSize) + 1;

            String body = objectMapper.writeValueAsString(Map.of(
                    "batch", batchNum,
                    "total_batches", totalBatches,
                    "count", batch.size(),
                    "records", batch
            ));

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (authHeader != null && !authHeader.isBlank()) {
                reqBuilder.header("Authorization", authHeader);
            }

            HttpResponse<String> response = client.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw DomainException.notValid(
                        "Webhook returned HTTP " + response.statusCode() + " for batch " + batchNum);
            }

            totalSent += batch.size();
            log.info("{} Webhook batch {}/{} — {} records, HTTP {}",
                    EXPORT_LOG_PREFIX, batchNum, totalBatches, batch.size(), response.statusCode());
        }

        long durationMs = System.currentTimeMillis() - start;
        return new ExportResponse(null, totalSent, durationMs,
                totalSent + " records POSTed to webhook in " + durationMs + "ms");
    }

    private String resolveContentType(ExportFormat format) {
        return switch (format) {
            case CSV -> "text/csv";
            case JSON -> "application/json";
            case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PARQUET -> "application/octet-stream";
            case MARKDOWN -> "text/markdown; charset=utf-8";
            case JSON_LD -> "application/ld+json";
            case LLMS_TXT -> "text/plain; charset=utf-8";
        };
    }

    private String resolveConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private String buildMarkdown(List<Map<String, Object>> records) {
        if (records.isEmpty()) return "# Export\n\n_No records._\n";

        StringBuilder sb = new StringBuilder();
        sb.append("# Scrape Export\n\n");
        sb.append("_Generated on ").append(LocalDate.now()).append(" — ")
                .append(records.size()).append(" records._\n\n");
        sb.append("---\n\n");

        int idx = 1;
        for (Map<String, Object> entry : records) {
            sb.append("---\n");
            sb.append("record_id: ").append(idx).append('\n');
            entry.forEach((k, v) -> {
                if (v != null && !isLongTextField(v)) {
                    sb.append(escapeYamlKey(k)).append(": ")
                            .append(escapeYamlValue(v)).append('\n');
                }
            });
            sb.append("---\n\n");
            sb.append("## Record ").append(idx).append("\n\n");
            entry.forEach((k, v) -> {
                if (isLongTextField(v)) {
                    sb.append("### ").append(humanize(k)).append("\n\n");
                    sb.append(v).append("\n\n");
                }
            });
            sb.append("\n");
            idx++;
        }
        return sb.toString();
    }

    private byte[] buildJsonLd(List<Map<String, Object>> records) throws IOException {
        Map<String, Object> jsonLd = new LinkedHashMap<>();
        jsonLd.put(CONTEXT_KEY, Map.of(
                "@vocab", SCHEMA_ORG_VOCAB,
                TITLE_FIELD, "name",
                URL_FIELD, URL_FIELD,
                DESCRIPTION_FIELD, DESCRIPTION_FIELD,
                PRICE_FIELD, PRICE_FIELD,
                IMAGE_FIELD, IMAGE_FIELD
        ));
        jsonLd.put(TYPE_KEY, "DataFeed");
        jsonLd.put("dateCreated", LocalDate.now().toString());
        jsonLd.put("dataFeedElement", records.stream()
                .map(this::toJsonLdElement)
                .toList());

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonLd);
    }

    private Map<String, Object> toJsonLdElement(Map<String, Object> entry) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put(TYPE_KEY, inferSchemaOrgType(entry));
        element.putAll(entry);
        return element;
    }

    private String inferSchemaOrgType(Map<String, Object> entry) {
        Set<String> keys = entry.keySet().stream()
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        if (keys.contains(PRICE_FIELD) || keys.contains("sku"))
            return PRODUCT_TYPE;
        if (keys.contains("author") && keys.contains(TITLE_FIELD))
            return ARTICLE_TYPE;
        if (keys.contains("startdate") || keys.contains("location"))
            return EVENT_TYPE;
        if (keys.contains("jobtitle") || keys.contains("hiringorganization"))
            return JOB_POSTING_TYPE;
        return DEFAULT_SCHEMA_TYPE;
    }

    private String buildLlmsTxt(List<Map<String, Object>> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Scrape Export\n\n");
        sb.append("> Structured data extracted from a web source. ")
                .append(records.size()).append(" records, exported on ")
                .append(LocalDate.now()).append(".\n\n");

        if (records.isEmpty()) {
            sb.append("_No records available._\n");
            return sb.toString();
        }
        String urlField = detectUrlField(records.getFirst());

        sb.append("## Records\n\n");
        int idx = 1;
        for (Map<String, Object> entry : records) {
            String title = detectTitleField(entry, idx);
            String url = urlField != null ? Objects.toString(entry.get(urlField), "") : "";
            String description = buildLlmsTxtDescription(entry);

            if (!url.isBlank()) {
                sb.append("- [").append(escapeMarkdownLink(title)).append("](").append(url).append(")");
            } else {
                sb.append("- **").append(escapeMarkdownLink(title)).append("**");
            }
            if (!description.isBlank()) {
                sb.append(": ").append(description);
            }
            sb.append('\n');
            idx++;
        }

        sb.append("\n## Metadata\n\n");
        sb.append("- Total records: ").append(records.size()).append('\n');
        sb.append("- Format: llms.txt (https://llmstxt.org)\n");
        sb.append("- Exported: ").append(LocalDate.now()).append('\n');

        return sb.toString();
    }

    private boolean isLongTextField(Object value) {
        return value instanceof String s && s.length() > 200;
    }

    private String escapeYamlKey(String key) {
        return IDENTIFIER_PATTERN.matcher(key).replaceAll("_");
    }

    private String escapeYamlValue(Object value) {
        String s = Objects.toString(value, "");
        if (s.contains("\n") || s.contains(":") || s.contains("#") || s.contains("\"")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n") + "\"";
        }
        return s;
    }

    private String humanize(String key) {
        String spaced = CAMEL_CASE_PATTERN.matcher(key).replaceAll("$1 $2")
                .replace('_', ' ').replace('-', ' ').trim();
        if (spaced.isEmpty()) return key;
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String detectUrlField(Map<String, Object> sample) {
        for (String key : sample.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.equals(URL_FIELD) || lower.equals("link") || lower.equals("href"))
                return key;
        }
        return null;
    }

    private String detectTitleField(Map<String, Object> entry, int fallbackIdx) {
        String[] candidates = {TITLE_FIELD, "name", "label", "heading"};
        for (String candidate : candidates) {
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).equals(candidate) && e.getValue() != null) {
                    return Objects.toString(e.getValue(), "");
                }
            }
        }
        return "Record " + fallbackIdx;
    }

    private String buildLlmsTxtDescription(Map<String, Object> entry) {
        String[] candidates = {DESCRIPTION_FIELD, "summary", "excerpt", "snippet"};
        for (String candidate : candidates) {
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).equals(candidate) && e.getValue() != null) {
                    String s = Objects.toString(e.getValue(), "");
                    return s.length() > 200 ? s.substring(0, 197) + "..." : s;
                }
            }
        }
        return "";
    }

    private String escapeMarkdownLink(String text) {
        return text.replace("[", "\\[").replace("]", "\\]");
    }
}