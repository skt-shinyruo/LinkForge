package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Analytics 查询统一使用的、首尾均包含的 UTC 自然日范围。 */
public final class ReportRange {

    public static final int MAX_INCLUSIVE_DAYS = 366;

    private final LocalDate from;
    private final LocalDate to;

    private ReportRange(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    public static ReportRange of(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表开始日期不能晚于结束日期");
        }
        ReportRange range = new ReportRange(from, to);
        if (range.inclusiveDays() > MAX_INCLUSIVE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表日期范围最大为 366 天");
        }
        return range;
    }

    public static ReportRange ofUtc(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表开始时间不能晚于结束时间");
        }
        return of(from.toLocalDate(), to.toLocalDate());
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public long inclusiveDays() {
        return ChronoUnit.DAYS.between(from, to) + 1L;
    }
}
