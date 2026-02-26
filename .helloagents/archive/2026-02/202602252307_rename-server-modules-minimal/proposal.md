# 变更提案: rename-server-modules-minimal

## 元信息
```yaml
类型: 重构
方案类型: implementation
优先级: P1
状态: 定稿
创建: 2026-02-25
```

---

## 1. 需求

### 背景
当前后端 `server/` 已按职责拆分为多模块（API/Edge/shared/tools），但目录命名仍偏长：`api-service`、`redirect-edge`、`platform-shared`、`dev-tools`。  
在日常操作（`mvn -pl ...`、Docker build context、CI 脚本路径、文档示例命令）中，这些名称会被频繁输入与引用；用户明确希望进一步“极简化模块命名”，减少输入成本并提升可读性。

本变更属于**结构性重命名**：不改变业务行为、不修改 Java package（`com.linkforge.api.*` / `com.linkforge.edge.*` / `com.linkforge.platform.*`），仅调整模块目录名与引用点。

### 目标
- 将 `server/` 下模块目录改为极简命名：
  - `server/api-service` → `server/api`
  - `server/redirect-edge` → `server/edge`
  - `server/platform-shared` → `server/platform`
  - `server/dev-tools` → `server/tooling`（脚本目录，非 Maven module）
- 同步更新所有引用旧目录名的位置（POM、Dockerfile、CI、README、知识库文档）。
- **默认不修改 Maven 坐标（artifactId/name/version）**，避免坐标/产物名额外漂移：
  - `linkforge-api-service` / `linkforge-redirect-edge` / `linkforge-platform-shared` 保持不变（仅目录名变化）。
- 验证构建与测试可通过：`mvn -f server/pom.xml test`。

### 约束条件
```yaml
时间约束: 一次性完成重命名并通过测试，避免出现“半改状态”导致团队无法构建。
性能约束: 不涉及运行时性能改动（仅目录/路径重命名）。
兼容性约束: 不修改 Java package 与对外 API 行为；Maven artifactId 保持不变（目录名变化通过 -pl <modulePath> 选择模块）。
业务约束: 不修改生产环境配置与运行逻辑。
```

### 验收标准
- [ ] `server/` 下目录存在：`api/`、`edge/`、`platform/`、`tooling/`；旧目录不存在。
- [ ] `server/pom.xml` 的 `<modules>` 与新目录一致（`platform`、`api`、`edge`）。
- [ ] `README.md`、CI、Dockerfile、脚本路径已同步更新，不再引用旧目录名。
- [ ] `mvn -f server/pom.xml test` 通过。
- [ ] 全仓不再引用旧模块目录路径（如 `server/api-service`、`server/redirect-edge`、`server/platform-shared`、`server/dev-tools`）；允许 Maven `artifactId` 中保留历史命名（如 `linkforge-api-service`）。

---

## 2. 方案

### 技术方案
采用“全仓引用扫描 → git mv 重命名 → 构建/脚本/文档引用替换 → 构建验证”的方式，确保遗漏点可被快速定位：

- 使用 `git mv` 保留历史、降低误删风险。
- 更新 `server/pom.xml` 的 `<modules>` 以匹配新目录名。
- 更新 `server/Dockerfile.*` 中的 `COPY` 路径与 `mvn -pl ...` 选择参数（改为模块路径 `api` / `edge`）。
- 更新 `server/tooling/check_split_packages.py` 的默认模块清单（`platform,api,edge`）与 CI 入口路径。
- 更新 `README.md` 与 `.helloagents/wiki/*`，确保文档与代码一致（SSOT）。

### 影响范围
```yaml
涉及模块:
  - server/pom.xml: reactor modules 列表更新
  - server/api: 模块目录重命名（原 api-service）
  - server/edge: 模块目录重命名（原 redirect-edge）
  - server/platform: 模块目录重命名（原 platform-shared）
  - server/tooling: 脚本目录重命名（原 dev-tools）
  - server/Dockerfile.api, server/Dockerfile.edge: COPY 路径与 -pl 参数更新
  - .github/workflows/ci.yml: split package 检测脚本路径更新
  - README.md + .helloagents/wiki/*: 文档同步
预计变更文件: 10~30（取决于知识库引用点数量）
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| 漏改路径引用导致 CI/Docker/build 失败 | 中 | 全仓 `rg` 搜索旧目录名；修改后运行 `mvn -f server/pom.xml test`；必要时补齐引用点清单 |
| `mvn -pl` 选择不再与 artifactId 同名导致误用 | 低 | README 明确使用 `-pl api/edge`（模块路径），必要时补充 `-pl com.linkforge:linkforge-api-service` 用法 |
| IDE 导入/运行配置失效 | 低 | 提示开发者重新导入 Maven reactor 或更新 Run Configuration 工作目录 |

---

## 3. 技术设计（可选）

本变更不涉及架构/API/数据模型变更，跳过。

---

## 5. 技术决策

> 本方案涉及的技术决策，归档后成为决策的唯一完整记录

### rename-server-modules-minimal#D001: 是否同步重命名 Maven artifactId
**日期**: 2026-02-25
**状态**: ✅采纳
**背景**: 目录名极简化后，Maven `artifactId` 是否也需要同步变短存在取舍：artifactId 影响产物名与坐标，变更面更大。
**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: 仅改目录名（选择） | 改动面更小；坐标与 jar 名保持稳定；降低遗漏风险 | `-pl` 推荐使用模块路径而非 artifactId，同名一致性下降 |
| B: 目录名 + artifactId 同步改短 | 坐标更极简（如 `linkforge-api`） | 影响面更大（依赖引用、jar 名、Dockerfile、文档）；后续发布/依赖消费更易破坏 |
**决策**: 选择方案 A。  
**理由**: 当前目标是“命令与路径更好用”；保持 artifactId 稳定可以降低一次性重命名的风险与破坏面。后续如确有“坐标也要极简”的需求，可另起方案包执行。  
**影响**: Maven reactor 模块路径变化，但模块间依赖坐标保持不变；Dockerfile 仅需更新目录路径，jar 文件名不变。  
