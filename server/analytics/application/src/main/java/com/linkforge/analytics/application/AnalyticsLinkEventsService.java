package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 受限访问明细查询的应用边界。
 *
 * <p>访问明细含 requestId、IP 哈希和经过截断的客户端信息，不能沿用普通统计接口的权限模型。只有
 * {@code TENANT_ADMIN} 或 {@code PLATFORM_ADMIN} 能调用；实际查询仍始终携带主体 tenantId。</p>
 */
@Service
public class AnalyticsLinkEventsService {

    private final AnalyticsQueryService analyticsQueryService;
    private final Clock clock;

    public AnalyticsLinkEventsService(AnalyticsQueryService analyticsQueryService, Clock clock) {
        this.analyticsQueryService = analyticsQueryService;
        this.clock = clock;
    }

    /**
     * 返回指定短链的原始访问明细快照。
     *
     * <p>缺省窗口是以当前 UTC 时刻结束的最近 24 小时；首尾所处 UTC 自然日均计入，最多 366 个自然日。
     * 缺省条数为 50，合法范围为 1 到 200。所有校验在调用查询端口前完成，失败不产生读取或其他副作用。</p>
     *
     * <p>结果来自异步采样的明细读模型，因此空结果不代表没有跳转，结果也不承诺完整、实时或 exactly-once。</p>
     *
     * @param actor 当前用户主体，必须具有租户管理员或平台管理员角色
     * @param linkId 目标短链 ID
     * @param from UTC 起点，可为空
     * @param to UTC 终点，可为空
     * @param limit 最大返回行数，可为空
     * @return tenant 范围内的明细列表
     * @throws BusinessException 主体无效、权限不足、窗口非法或 limit 越界时抛出
     */
    public List<AnalyticsQueryService.VisitEvent> listLinkEvents(
            UserActor actor,
            long linkId,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit
    ) {
        requireAdmin(actor);
        LocalDateTime effectiveTo = to == null ? nowUtc() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
        ReportRange.validateUtc(effectiveFrom, effectiveTo);

        int effectiveLimit = limit == null ? 50 : limit;
        if (effectiveLimit < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (effectiveLimit > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 200");
        }

        return analyticsQueryService.linkEvents(actor.tenantId(), linkId, effectiveFrom, effectiveTo, effectiveLimit);
    }

    private static void requireAdmin(UserActor actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
        }
        Set<String> roles = actor.roles() == null ? Set.of() : actor.roles();
        if (!roles.contains(StandardRoles.TENANT_ADMIN) && !roles.contains(StandardRoles.PLATFORM_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "访问明细需要管理员权限");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
