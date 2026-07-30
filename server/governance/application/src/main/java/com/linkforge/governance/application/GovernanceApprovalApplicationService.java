package com.linkforge.governance.application;

import com.linkforge.contract.governance.AnalyticsDetailExportApprovalPayload;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.LinkDestinationChangeApprovalPayload;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 将跨上下文的审批提交契约适配为 Governance 内部命令。
 *
 * <p>本服务负责把短链目标地址变更和访问明细导出编码为版本 1 的结构化 before/after payload，
 * 并保留调用方传入的租户、申请人和请求时间。事务、身份校验、ID 分配及审计写入统一由
 * {@link GovernanceService} 完成。</p>
 */
@Service
public class GovernanceApprovalApplicationService implements ApprovalSubmissionPort {

    private final GovernanceService governanceService;

    public GovernanceApprovalApplicationService(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 提交公开短链目标地址变更审批。
     *
     * <p>当前地址与目标地址分别编码为同一 linkId 的 before/after 快照，后续执行器会用 before 快照执行陈旧写校验。</p>
     */
    @Override
    public ApprovalRequestView requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    ) {
        ApprovalRequestResult dto = governanceService.submitRequest(
                tenantId,
                new SubmitApprovalRequest(
                        SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        request.targetApplicationId(),
                        linkDestinationSnapshot(request.linkId(), request.currentOriginalUrl()),
                        linkDestinationSnapshot(request.linkId(), request.requestedOriginalUrl()),
                        toUserActor(request.requester()),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    /**
     * 提交访问明细导出审批。
     *
     * <p>导出没有可比较的旧状态，因此 before 快照为空；linkId 与 UTC 时间范围写入版本化 after 快照。</p>
     */
    @Override
    public ApprovalRequestView requestAnalyticsDetailExportApproval(
            long tenantId,
            AnalyticsDetailExportApprovalRequest request
    ) {
        ApprovalRequestResult dto = governanceService.submitRequest(
                tenantId,
                new SubmitApprovalRequest(
                        SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                        request.targetApplicationId(),
                        null,
                        ApprovalPayloadCodec.write(AnalyticsDetailExportApprovalPayload.v1(
                                request.linkId(),
                                request.from(),
                                request.to()
                        )),
                        toUserActor(request.requester()),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    private static String linkDestinationSnapshot(long linkId, String originalUrl) {
        return ApprovalPayloadCodec.write(LinkDestinationChangeApprovalPayload.v1(linkId, originalUrl));
    }

    private static UserActor toUserActor(ApprovalRequester requester) {
        if (requester == null) {
            return null;
        }
        return new UserActor(requester.tenantId(), requester.userId(), requester.email(), Set.of());
    }

    private static ApprovalRequestView toResult(ApprovalRequestResult dto) {
        return new ApprovalRequestView(
                dto.id(),
                dto.tenantId(),
                dto.operationType().name(),
                dto.targetApplicationId(),
                dto.requestedByUserId(),
                dto.requestedByEmail(),
                dto.status().name(),
                dto.approverUserId(),
                dto.approverEmail(),
                dto.decisionReason()
        );
    }
}
