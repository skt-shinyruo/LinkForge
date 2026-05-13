# 审批与审计链路

## 业务目标

Governance 管理敏感操作审批请求、审批决策、执行器回调和审计日志。它不直接理解 Shortlink 或 Analytics 的内部表结构，而是通过 `contract-governance` 中的稳定载荷和执行端口与其他上下文协作。

当前主要审批场景：

- 应用级短链目标地址变更：`PUBLIC_LINK_DESTINATION_CHANGE`，审批通过后由 Shortlink 执行器真正修改 URL。
- 访问明细导出：`ANALYTICS_DETAIL_EXPORT`，当前只创建审批和审计，不生成文件。
- 代码中还保留外部域名绑定、应用配额提升等审批矩阵规则。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1060" height="520" viewBox="0 0 1060 520" role="img" aria-label="审批与审计链路">
  <defs>
    <marker id="arrow-gov" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .payload{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .rule{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-gov)}
    </style>
  </defs>
  <rect class="box" x="30" y="50" width="170" height="82"/>
  <text class="text" x="115" y="80" text-anchor="middle">业务上下文</text>
  <text class="small" x="115" y="102" text-anchor="middle">Shortlink / Analytics</text>
  <text class="small" x="115" y="122" text-anchor="middle">提交敏感操作</text>

  <rect class="payload" x="265" y="45" width="190" height="92"/>
  <text class="text" x="360" y="75" text-anchor="middle">ApprovalSubmissionPort</text>
  <text class="small" x="360" y="98" text-anchor="middle">版本化 before/after</text>
  <text class="small" x="360" y="118" text-anchor="middle">requester / target app</text>

  <rect class="box" x="525" y="45" width="190" height="92"/>
  <text class="text" x="620" y="75" text-anchor="middle">GovernanceService</text>
  <text class="small" x="620" y="98" text-anchor="middle">submitRequest</text>
  <text class="small" x="620" y="118" text-anchor="middle">PENDING_APPROVAL</text>

  <rect class="ok" x="785" y="45" width="190" height="92"/>
  <text class="text" x="880" y="75" text-anchor="middle">审批库 + 审计</text>
  <text class="small" x="880" y="98" text-anchor="middle">approval_request</text>
  <text class="small" x="880" y="118" text-anchor="middle">SUBMIT_REQUEST</text>

  <rect class="box" x="30" y="230" width="170" height="82"/>
  <text class="text" x="115" y="260" text-anchor="middle">管理员审批</text>
  <text class="small" x="115" y="282" text-anchor="middle">POST /approvals/{id}/approve</text>

  <rect class="rule" x="265" y="215" width="190" height="112"/>
  <text class="text" x="360" y="245" text-anchor="middle">ApprovalRequest.approve</text>
  <text class="small" x="360" y="268" text-anchor="middle">必须 pending</text>
  <text class="small" x="360" y="288" text-anchor="middle">禁止自审批</text>
  <text class="small" x="360" y="308" text-anchor="middle">审批矩阵</text>

  <rect class="rule" x="525" y="215" width="190" height="112"/>
  <text class="text" x="620" y="245" text-anchor="middle">并发抢占</text>
  <text class="small" x="620" y="268" text-anchor="middle">markApprovedIfPending</text>
  <text class="small" x="620" y="288" text-anchor="middle">查找执行器</text>
  <text class="small" x="620" y="308" text-anchor="middle">执行成功再 markExecuted</text>

  <rect class="ok" x="785" y="215" width="190" height="112"/>
  <text class="text" x="880" y="245" text-anchor="middle">ApprovalExecutionPort</text>
  <text class="small" x="880" y="268" text-anchor="middle">Shortlink 执行 URL 更新</text>
  <text class="small" x="880" y="288" text-anchor="middle">发布事件</text>
  <text class="small" x="880" y="308" text-anchor="middle">提交后驱逐缓存</text>

  <rect class="ok" x="525" y="400" width="450" height="64"/>
  <text class="text" x="750" y="427" text-anchor="middle">APPROVE_REQUEST 审计日志</text>
  <text class="small" x="750" y="449" text-anchor="middle">actor / resource / requestId / beforeSnapshot / afterSnapshot / createdAt</text>

  <path class="line" d="M200 91 H265"/>
  <path class="line" d="M455 91 H525"/>
  <path class="line" d="M715 91 H785"/>
  <path class="line" d="M200 271 H265"/>
  <path class="line" d="M455 271 H525"/>
  <path class="line" d="M715 271 H785"/>
  <path class="line" d="M880 327 V400"/>
</svg>

## 提交审批

业务上下文不直接插 Governance 表，而是调用 `ApprovalSubmissionPort`：

- `requestLinkDestinationChangeApproval()`：短链目标地址变更。
- `requestAnalyticsDetailExportApproval()`：访问明细导出。

`GovernanceApprovalApplicationService` 把这些窄口径请求转换成 `SubmitApprovalRequest`，构造版本化结构化 payload，交给 `GovernanceService.submitRequest()`。

`submitRequest()` 会：

1. 校验 actor 属于当前租户。
2. 生成 requestId。
3. 创建 `ApprovalRequest`，状态为 `PENDING_APPROVAL`。
4. 保存 before/after snapshot。
5. 写 `SUBMIT_REQUEST` 审计日志。

## 审批执行

`ApprovalController.approve()` 调用 `GovernanceService.approveRequest()`：

1. 校验 actor 和审批请求存在。
2. 调领域对象 `ApprovalRequest.approve()`。
3. 防止非 pending 状态审批。
4. 防止申请人审批自己的请求。
5. 执行审批矩阵。
6. 调 `markApprovedIfPending()` 抢占 pending 状态，避免并发审批。
7. 查找支持该 operation 的 `ApprovalExecutionPort`。
8. 如果存在执行器，调用执行器。
9. 执行成功后调 `markExecutedIfApproved()`。
10. 写 `APPROVE_REQUEST` 审计日志。
11. 回读最新审批请求返回。

如果执行器执行失败，不会写成功审计，也不会标记 executed。这样审批状态和业务执行结果不会被错误地标记为完成。

## 审批矩阵

- `TENANT_ADMIN` 和 `PLATFORM_ADMIN` 可审批一般请求。
- `EXTERNAL_DOMAIN_BINDING` 必须平台管理员审批。
- `APPLICATION_QUOTA_INCREASE` 如果请求月发链额度超过 100000，必须平台管理员审批。
- 没有审批角色直接返回 forbidden。

## 目标地址变更审批

提交阶段：

- `UpdateShortLinkCommandHandler` 判断应用级短链修改 `originalUrl`。
- 要求本次请求不能夹带其他有效修改。
- 提交 `PUBLIC_LINK_DESTINATION_CHANGE` 审批。
- 不立即修改短链。

执行阶段由 `LinkDestinationChangeApprovalExecutor` 完成：

- 只支持 `PUBLIC_LINK_DESTINATION_CHANGE`。
- 校验 before/after snapshot 的 linkId 一致。
- 校验短链仍属于审批目标应用。
- 校验短链仍 active 且未归档。
- 校验当前 originalUrl 仍等于 before snapshot，避免审批期间被其他流程修改。
- 更新目标地址，乐观锁写入。
- 发布事件并在事务提交后驱逐 Redirect 缓存。

## 访问明细导出审批

`AnalyticsExportRequestService` 校验链接归属和时间范围后提交 `ANALYTICS_DETAIL_EXPORT`。Governance 会记录审批和审计。当前没有实现审批通过后生成文件的 `ApprovalExecutionPort`，所以审批通过后状态保持 approved，不会进入 executed。

## 源码分析

- `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalSubmissionPort.java`
  - 跨上下文提交审批的窄口径接口。
- `server/contracts/governance/src/main/java/com/linkforge/contract/governance/ApprovalExecutionPort.java`
  - 审批通过后的业务执行器接口。
- `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalRequest.java`
  - 审批聚合，保护 pending、approved、executed 状态转换和自审批规则。
- `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceApprovalApplicationService.java`
  - 把跨上下文请求转换成 Governance 内部审批请求。
- `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
  - `submitRequest()`、`approveRequest()`、审批矩阵、执行器查找和审计写入。
- `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/ApprovalController.java`
  - 审批列表和审批动作入口。
- `server/governance/interfaces/src/main/java/com/linkforge/governance/interfaces/web/AuditController.java`
  - 审计日志查询入口。
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java`
  - 短链目标地址变更执行器。
- `server/analytics/application/src/main/java/com/linkforge/analytics/application/AnalyticsExportRequestService.java`
  - 访问明细导出审批提交方。

## 审计日志

审计记录包含：

- actorUserId
- actorEmail
- actionType
- resourceType
- resourceId
- requestId
- beforeSnapshot
- afterSnapshot
- createdAt

审计查询入口是 `GET /api/v1/audit-logs`，要求 `TENANT_ADMIN` 或 `PLATFORM_ADMIN`。
