# 项目上下文

## 1. 基本信息

```yaml
名称: LinkForge
描述: 面向公网的多租户自助短链平台（含管理后台与 OpenAPI）
类型: Web 应用 + 后端服务（API + Redirect Edge）+ 前端管理后台
状态: 开发中
```

## 2. 技术上下文

```yaml
语言: Java 17（目标 Java 21）
框架: Spring Boot 3.2.x
包管理器: Maven（后端）/ npm（前端）
构建工具: Maven / Vite
```

### 主要依赖
| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | API/Edge 服务框架 |
| MySQL | 8.x | 核心业务数据持久化 |
| Redis | 7.x | 缓存、统计计数/去重结构、互斥锁（ShedLock） |
| ShedLock | - | 多实例定时作业互斥（使用 Redis 存储） |
| Vue | 3 | 管理后台 UI |
| Vite | - | 前端构建 |
| JUnit | 5 | 单元测试 |
| Testcontainers | - | 集成测试（MySQL/Redis） |

## 3. 项目概述

### 核心功能
- 自助注册/登录 + 多租户隔离 + 角色权限（管理员/普通用户）
- 短链创建/编辑/禁用/有效期/备注/标签 + 生命周期治理（归档/恢复/删除）
- 跳转服务：`/r/{code}`（短码解析、跳转策略、基础风控）
- 统计采集与报表：PV/UV 聚合、趋势、Top 链接、维度聚合与访问明细（短期留存）
- OpenAPI：API Key 管理（创建/禁用/启用/轮换，仅返回一次明文）

### 项目边界
```yaml
范围内:
  - 自助注册/登录、多租户隔离、角色权限
  - 短链生成、管理、跳转与统计报表
  - 管理后台与 OpenAPI
范围外:
  - 复杂风控体系（反作弊、内容审核、黑白名单联动等）
  - 运营投放系统、A/B 实验等高级能力
```

## 4. 开发约定

> 更完整的工程/接口/部署约定见：`.helloagents/project.md` 与 `.helloagents/wiki/*`。

### 代码规范
```yaml
后端根目录: server/
Maven 模块: platform + api + edge
包命名: com.linkforge.*
```

### 错误处理
```yaml
响应包裹: {code, message, data, requestId}
RequestId Header: X-Request-Id
```

### 测试要求
```yaml
单元测试: JUnit 5
集成测试: Spring Boot Test + Testcontainers（MySQL/Redis）
运行: cd server && mvn test
```

### Git规范
```yaml
提交格式: Conventional Commits（feat/fix/refactor/docs/chore）
```

## 5. 当前约束（源自历史决策）

> 这些是当前生效的技术约束，详细决策过程见对应方案包。

| 约束 | 原因 | 决策来源 |
|------|------|---------|
| 避免 split package，明确包归属 | 降低模块耦合与构建/运行期类冲突风险 | [package-ownership-refactor#D001](archive/2026-02/202602242331_package-ownership-refactor/proposal.md) |
| 短链管理侧缓存副作用在事务提交后执行（AFTER_COMMIT） | 避免回滚污染缓存、提交前驱逐导致旧值回填 | [shortlink-cache-after-commit#D001](archive/2026-02/202602251453_shortlink-cache-after-commit/proposal.md) |
| 引入持久化 outbox 兜底缓存刷新/驱逐的最终一致性 | 覆盖 commit 后崩溃/Redis 短暂不可用等异常场景 | [shortlink-cache-outbox#D001](archive/2026-02/202602251605_shortlink-cache-outbox/proposal.md) |

