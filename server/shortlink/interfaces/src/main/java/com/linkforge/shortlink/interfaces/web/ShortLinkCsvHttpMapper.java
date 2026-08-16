package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExportRow;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.foundation.util.CsvFormulaNeutralizer;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ShortLinkCsvHttpMapper {

    public List<ShortLinkCsvImportRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            List<ShortLinkCsvImportRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(new ShortLinkCsvImportRow(
                        record.getRecordNumber(),
                        safeGet(record, "applicationId"),
                        safeGet(record, "domainId"),
                        safeGet(record, "hostname"),
                        safeGet(record, "originalUrl"),
                        safeGet(record, "code"),
                        safeGet(record, "expiresAt"),
                        safeGet(record, "note"),
                        safeGet(record, "tags")
                ));
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 解析失败");
        }
    }

    public void write(ShortLinkCsvExport export, HttpServletResponse response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "响应对象不能为空");
        }
        response.setHeader("Content-Type", "text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"links.csv\"");
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(
                     writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader("id", "applicationId", "domainId", "hostname", "code", "originalUrl", "note", "enabled", "expiresAt", "tags")
                             .build()
             )) {
            List<ShortLinkCsvExportRow> rows = export == null ? List.of() : export.rows();
            for (ShortLinkCsvExportRow row : rows) {
                printer.printRecord(
                        row.id(),
                        row.applicationId(),
                        row.domainId(),
                        CsvFormulaNeutralizer.neutralizeUserText(row.hostname()),
                        CsvFormulaNeutralizer.neutralizeUserText(row.code()),
                        CsvFormulaNeutralizer.neutralizeUserText(row.originalUrl()),
                        CsvFormulaNeutralizer.neutralizeUserText(row.note()),
                        row.enabled(),
                        row.expiresAt() == null ? "" : row.expiresAt().toString(),
                        CsvFormulaNeutralizer.neutralizeUserText(
                                String.join(",", row.tags() == null ? List.of() : row.tags())
                        )
                );
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private static String safeGet(CSVRecord record, String key) {
        try {
            return record.isMapped(key) ? normalizeNullable(record.get(key)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
