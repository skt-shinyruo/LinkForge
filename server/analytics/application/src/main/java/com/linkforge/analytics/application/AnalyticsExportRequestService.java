package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.context.UserActor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 访问明细导出审批的应用服务。
 *
 * <p>该服务只创建 {@code ANALYTICS_DETAIL_EXPORT} 审批请求，不读取或生成导出文件。调用方必须传入
 * 已认证的用户主体；本服务以主体 tenantId 查询短链，避免由路径中的资源 ID 跨租户访问数据。</p>
 *
 * <p>时间均按 UTC {@link LocalDateTime} 解释。一次有效调用会委托 Governance 创建请求，当前层不提供
 * 去重或幂等键，重复提交是否折叠由审批端口的实现决定。</p>
 */
@Service
public class AnalyticsExportRequestService {

    private final ApprovalSubmissionPort approvalSubmissionPort;
    private final ShortLinkReadPort shortLinkReadPort;
    private final Clock clock;

    public AnalyticsExportRequestService(
            ApprovalSubmissionPort approvalSubmissionPort,
            ShortLinkReadPort shortLinkReadPort,
            Clock clock
    ) {
        this.approvalSubmissionPort = approvalSubmissionPort;
        this.shortLinkReadPort = shortLinkReadPort;
        this.clock = clock;
    }

    /**
     * 为一条短链的访问明细创建审批请求。
     *
     * <p>验证顺序是先确认短链在当前租户可见，再验证可选的应用路径范围，最后检查时间窗口；因此不存在或
     * 无权访问的链接不会因为非法日期泄露额外信息，也不会产生审批副作用。未提供 {@code to} 时取当前 UTC
     * 时间，未提供 {@code from} 时取该时刻前 24 小时。</p>
     *
     * @param actor 已由 HTTP 安全层转换的用户主体
     * @param linkId 要导出明细的短链 ID
     * @param expectedApplicationId 应用级路由传入的预期归属；为 {@code null} 时不额外限制应用
     * @param from UTC 起点，可为空
     * @param to UTC 终点，可为空
     * @return Governance 创建后的审批请求视图，而非导出结果
     * @throws BusinessException 短链不可见、应用不匹配或 {@code from > to} 时抛出
     */
    public ApprovalRequestView requestLinkEventExport(
            UserActor actor,
            long linkId,
            Long expectedApplicationId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        ShortLinkReadPort.ShortLinkOwnership link = requireLinkScope(actor.tenantId(), linkId);
        if (expectedApplicationId != null
                && (link.applicationId() == null || !expectedApplicationId.equals(link.applicationId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "链接不属于该应用");
        }

        LocalDateTime effectiveTo = to == null ? nowUtc() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }

        return approvalSubmissionPort.requestAnalyticsDetailExportApproval(
                actor.tenantId(),
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        linkId,
                        link.applicationId(),
                        effectiveFrom,
                        effectiveTo,
                        new ApprovalRequester(actor.tenantId(), actor.userId(), actor.email()),
                        nowUtc()
                )
        );
    }

    private ShortLinkReadPort.ShortLinkOwnership requireLinkScope(long tenantId, long linkId) {
        return shortLinkReadPort.findOwnership(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "链接不存在"));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
