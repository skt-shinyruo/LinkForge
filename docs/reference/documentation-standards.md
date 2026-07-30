# 核心逻辑文档规范

本文规定哪些逻辑必须说明、说明放在哪里，以及机器门禁能和不能保证什么。

## 适用范围

以下内容属于核心逻辑，必须同时指向源码和权威文档：

- 聚合、值对象和业务状态机；
- 应用服务、命令处理器、授权/作用域策略；
- 跨上下文发布端口、事件和 payload；
- 事务、乐观锁、named lock、幂等和重试；
- Redis 缓存三态、key/member、Stream、outbox 和降级；
- 配额、统计口径、采样、HLL 和关键 SQL；
- 认证、CSRF、可信代理、隐私与安全失败策略；
- 前端路由守卫、auth store、统一 HTTP 契约及页面并发编排。

简单 DTO、实体 getter/setter、机械 mapper、Spring 装配、测试夹具和生成物不要求逐项注释。它们若承载 null、时间、敏感值或兼容性语义，仍应在类型级契约或专题文档中说明。

## 文档层次

- `core-logic.md` 是地图，只说明上下文职责、主要链路和专题入口。
- 业务专题是规则、权限、状态决策、失败和排障的唯一详细事实源。
- `foundation-and-configuration.md` 是横切运行时和关键配置事实源。
- `published-contracts.md` 是跨上下文 Java/JSON/Redis 契约事实源。
- 源码 Javadoc/TSDoc 解释局部职责、不变量、参数边界、副作用和失败策略。

同一详细规则不要在多页复制。地图使用链接和一句摘要；若事实变化，只需修改一个专题和必要的代码契约。

## Javadoc 与 TSDoc 内容

核心类型的注释至少回答与其相关的问题：

- 它拥有什么职责，不拥有什么职责？
- 输入的 null、空值、长度、单位和时区是什么？
- 成功后修改了哪些状态，产生哪些事件、缓存或外部副作用？
- 是否要求现有事务、锁、认证角色或 tenant scope？
- 重试是否安全，是否可能重复，幂等键是什么？
- Redis/DB/执行器失败时 fail-open、fail-closed、重试还是 best-effort？
- 调用方应处理哪些稳定异常或三态结果？

不要把代码逐行翻译成自然语言，也不要为显然的 getter 写“返回字段值”。注释应解释代码无法直接表达的原因、约束和操作后果。

新增注释使用中文；标识符、配置 key、协议字段、错误码和标准术语保持英文。已存在的英文公共库说明可以在触及对应核心逻辑时改为中文。

## 图表与决策表

当存在三个以上状态、角色或失败分支时，专题文档优先使用表格：

- 权限矩阵：主体 × 资源范围 × 操作；
- 状态决策：缓存/聚合/审批状态 × 输出；
- 配置表：key × 默认值 × 单位 × `0/null` 语义；
- 一致性时序：事务写入、提交后快速路径、worker 重试、消费者 ACK。

图表必须与正文同时保留可搜索的协议名和源码入口，不能只靠图片表达规则。

## 核心目录

`core-logic-catalog.json` 使用以下 schema：

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "id": "shortlink.create",
      "context": "shortlink",
      "sources": ["server/shortlink/.../CreateShortLinkCommandHandler.java"],
      "document": "docs/reference/shortlink-management.md",
      "heading": "创建短链"
    }
  ]
}
```

约束：

- `id` 全局唯一且稳定，使用小写点分命名；重命名流程时优先保留 ID。
- `context` 是拥有该逻辑的上下文。当前允许值为 `accounts`、`platform`、`shortlink`、`redirect`、`analytics`、`governance`、`foundation`、`contracts`、`cross-context` 和 `frontend`；`contracts` 用于已发布的跨上下文类型，不能借此绕过业务上下文归属。
- `sources` 至少一个仓库相对路径，必须存在；只登记拥有核心行为的文件，不罗列所有 DTO。
- `document` 必须是权威专题，不能指向地图页中的重复描述。
- `heading` 必须在 Markdown 中真实存在且唯一到足以定位。

目录门禁校验 schema、唯一 ID、合法 context、路径、唯一标题、公开 contracts 登记和核心符号前的文档注释。它只能保证覆盖和可定位，不能判断说明是否正确。

## 评审清单

核心逻辑变更的评审至少检查：

1. 当前可测试实现是否仍与专题文档一致；
2. HTTP 方法、路由、错误码、JSON 和 Redis wire shape 是否被契约测试锁定；
3. 事务提交、回滚、重复投递和部分失败是否有明确语义；
4. 缓存故障是否与真实不存在区分；
5. 配置默认值、单位及 `0/null/负值` 是否明确；
6. 安全边界是否包含租户隔离、认证状态、CSRF、代理头和敏感字段；
7. 当前限制是否被如实记录，而不是用 exactly-once、实时或精确 UV 等过强措辞；
8. catalog、专题链接和源码注释是否同步。

## 校验命令

常规验收运行：

```bash
cd server
mvn test

cd ../web
npm test
npm run build
```

Docker 可用时再运行集成测试 profile。Markdown 相对链接、catalog 和 contract 覆盖由 `server/app` 的文档架构测试随 Maven 测试执行。
