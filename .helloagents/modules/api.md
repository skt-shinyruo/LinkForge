# api

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/api.md](../wiki/modules/api.md)

## 职责

- 提供管理后台/自助创建 API：`/api/v1/**`
- 负责 IAM、ShortLink、Analytics 查询/落库、OpenAPI（API Key）等核心业务写入与查询
- 承担需要一致性保障的副作用编排（例如事务提交后再刷新/驱逐缓存）
- 承担定时作业（如统计增量 flush、retention 等）并通过互斥机制避免多实例重复执行

## 行为规范

### 统一协议
- 响应包裹统一为 `{code, message, data, requestId}`
- RequestId 通过 `X-Request-Id` 透传/生成，用于排障与日志关联

### 多租户与权限
- 所有租户隔离查询必须显式带 `tenant_id`
- Service 层必须做 tenant guard，避免越权与“误用 tenantId 参数”的风险

### 调度治理
- 多实例部署时，定时任务需互斥运行（ShedLock/Redis）
- 支持通过配置开关禁用全部 `@Scheduled` 作业（测试/特定环境）

## 依赖关系

```yaml
依赖:
  - platform（跨服务 SSOT / 公共契约）
  - iam
  - shortlink
  - analytics
  - mysql
  - redis
被依赖:
  - admin-ui
  - OpenAPI 客户端
```

