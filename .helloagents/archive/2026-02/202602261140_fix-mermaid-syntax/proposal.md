# 变更提案: fix-mermaid-syntax

## 元信息
```yaml
类型: 修复（文档/知识库）
方案类型: implementation
优先级: P2
状态: 草稿
创建: 2026-02-26 11:40
目标版本: Mermaid v10.9.5
范围: 全仓库（包含 .helloagents/**）
```

---

## 1. 需求

### 背景
- 仓库内部分 Mermaid 图在 Mermaid v10.9.5 下存在语法不兼容，导致解析失败（渲染链路/校验脚本/CI 集成时易阻断）。
- 已定位（不包含本方案包当前模板文件）共有 **9 个** Mermaid fenced code block（**7 个文件**），其中 3 处风险点已明确：
  - flowchart 虚线边带文字的分词/空格问题
  - 连接线指向 subgraph id 的兼容性问题
  - 节点 label 使用 `\\n` 的解析/渲染一致性问题

### 目标
- 以 Mermaid v10.9.5 为基线，修复仓库内所有 Mermaid 语法错误，使所有 Mermaid 代码块 `mermaid.parse()` 通过（parse-only）。
- 仅做语法/兼容性修复，**不改变图表达的业务含义**；如为语法需要引入“锚点节点”，需保持语义等价并尽量降低视觉干扰。

### 约束条件
```yaml
依赖约束: 不在仓库内引入/提交新的 Node 依赖；校验环境仅在临时目录创建
变更边界: 仅修改 Markdown 中的 Mermaid 代码块内容（尽量不动正文）
兼容性基线: Mermaid v10.9.5（parse-only 验证）
```

### 验收标准
- [ ] 在临时目录安装 `mermaid@10.9.5`，对仓库内所有 Mermaid 代码块执行 `mermaid.parse()` 均通过（0 语法错误）
- [ ] `.helloagents/wiki/arch.md`（约 `#L7` 的 flowchart）中 `Redis -.可选扩展.-> MQ` / `MQ -.可选扩展.-> OLAP` 修复后可解析
- [ ] `.helloagents/archive/2026-02/202602242331_package-ownership-refactor/proposal.md`（约 `#L112`）避免 `API --> Shared` / `Edge --> Shared` 指向 subgraph id 后可解析
- [ ] `.helloagents/archive/2026-02/202602252042_shortlink-core-gap-analysis/proposal.md`（约 `#L121`）节点 label 中 `\\n` 统一替换为 `<br/>` 后可解析
- [ ] 其余 Mermaid 代码块仅在校验失败时做“最小语法修复”，不做无关重绘/重排

---

## 2. 方案

### 技术方案
- 盘点：使用 `rg --hidden -uuu -n` 列出所有 Mermaid 代码块所在文件与起始行。
- 校验：在临时目录安装 `mermaid@10.9.5`，用 Node 脚本提取每个 Markdown 的 Mermaid 代码块并执行 `mermaid.parse()`（parse-only）。
- 修复策略（按已知风险优先）：
  - **连接线带文字**：统一改为 Mermaid v10 兼容的“带空格”写法 `A -. 文本 .-> B`（如仍不兼容，再改为 parse 通过的等价写法）。
  - **subgraph 连接**：避免把边直接指向 subgraph id；改为连接子图内显式“锚点节点”（例如 `ApiHub/EdgeHub/SharedHub`），或连接子图内代表节点。
  - **节点换行**：统一使用 `<br/>`（与仓库内既有写法一致），避免 `\\n` 造成解析/渲染差异。
- 回归：修复后再次运行 parse-only 校验，直到 0 错误。

### 影响范围
```yaml
已发现 Mermaid 代码块（9 块 / 7 文件；不含本方案包当前模板文件）:
  - .helloagents/wiki/arch.md: #L7 (flowchart), #L60 (sequence), #L75 (sequence)
  - .helloagents/history/2026-02/202602191426_edge_api_split_refactor/how.md: #L26
  - .helloagents/history/2026-02/202602182227_shortlink_system_mvp/how.md: #L47
  - .helloagents/history/2026-02/202602201217_analytics_visit_events_dims/how.md: #L44
  - .helloagents/archive/2026-02/202602242331_package-ownership-refactor/proposal.md: #L112
  - .helloagents/archive/2026-02/202602252042_shortlink-core-gap-analysis/proposal.md: #L121
  - .helloagents/plan/202602252150_rename-server-modules/proposal.md: #L97

重点修改点（预计）:
  - .helloagents/wiki/arch.md: #L23-24 附近（虚线边带文本）
  - .helloagents/archive/2026-02/202602242331_package-ownership-refactor/proposal.md: #L128-129 附近（subgraph 连接）
  - .helloagents/archive/2026-02/202602252042_shortlink-core-gap-analysis/proposal.md: #L124-126 附近（label 换行）

预计变更文件: 3~7（以 parse-only 结果为准）
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| subgraph 连接修复引入锚点节点，可能导致图形外观轻微变化 | 中 | 采用最小/中性锚点命名与形状，保持语义等价 |
| `mermaid.parse()` 通过但不同渲染器仍存在差异 | 低 | 以 v10.9.5 parse-only 作为硬验收；必要时抽样渲染复核 |
| 修复 Mermaid 代码块时误改图意 | 低 | 仅改语法；变更需能解释“语义等价” |

---

## 3. 技术设计（可选）
N/A（纯文档 Mermaid 语法兼容修复，无 API/数据模型变更）

---

## 4. 核心场景

### 场景: Mermaid 语法校验（parse-only）通过
**模块**: 文档 / `.helloagents/**`  
**条件**: 使用 `mermaid@10.9.5` 运行校验脚本  
**行为**: 遍历所有 Markdown 的 Mermaid 代码块并执行 `mermaid.parse()`  
**结果**: 0 语法错误；失败时输出文件 + 代码块序号 + 错误信息

---

## 5. 技术决策（可选）

### fix-mermaid-syntax#D001: Flowchart 边标签统一使用“带空格”的语法
**日期**: 2026-02-26  
**状态**: ✅采纳  
**决策**: 将 `A -.文本.-> B` 统一改为 `A -. 文本 .-> B`（必要时改为 parse 通过的等价写法）  
**理由**: Mermaid v10.9.5 对分词更严格，缺失空格易解析失败；统一写法便于自动校验

### fix-mermaid-syntax#D002: 禁止边直接指向 subgraph id，改用子图内锚点节点
**日期**: 2026-02-26  
**状态**: ✅采纳  
**决策**: 将 `API --> Shared` 这类连接改为“节点→节点”（锚点节点位于各自 subgraph 内）  
**理由**: 避免 subgraph id 与 node id 冲突/不被允许的连接方式，提升 Mermaid 版本兼容性

### 场景: {场景名称}
**模块**: {所属模块}
**条件**: {前置条件}
**行为**: {操作描述}
**结果**: {预期结果}

---

## 5. 技术决策

> 本方案涉及的技术决策，归档后成为决策的唯一完整记录

### fix-mermaid-syntax#D001: {决策标题}
**日期**: 2026-02-26
**状态**: ✅采纳 / ❌废弃 / ⏸搁置
**背景**: {为什么需要这个决策}
**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: {方案A} | {优点} | {缺点} |
| B: {方案B} | {优点} | {缺点} |
**决策**: 选择方案{X}
**理由**: {详细理由}
**影响**: {对哪些模块有影响}
