# 任务清单: rename-server-modules-minimal

> **@status:** completed | 2026-02-25 23:26

```yaml
@feature: rename-server-modules-minimal
@created: 2026-02-25
@status: completed
@mode: R2
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 21/21 (100%) | 更新: 2026-02-25 23:27:22
当前: 无
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 21 | 0 | 0 | 21 |

---

## 任务列表

### 1. 影响面扫描（引用点清单）

- [√] 1.1 扫描旧模块目录路径引用：`rg -n "server/(api-service|redirect-edge|platform-shared|dev-tools)"`（全仓）
- [√] 1.2 扫描知识库引用（隐藏目录）：`rg -n "server/(api-service|redirect-edge|platform-shared|dev-tools)" --hidden .helloagents`

### 2. 目录重命名（git mv）

- [√] 2.1 `git mv server/api-service server/api`
- [√] 2.2 `git mv server/redirect-edge server/edge`
- [√] 2.3 `git mv server/platform-shared server/platform`
- [√] 2.4 `git mv server/dev-tools server/tooling`（仅脚本目录，非 Maven module）

### 3. 构建与脚本更新

- [√] 3.1 更新 `server/pom.xml`：`<modules>` 改为 `platform/api/edge`
- [√] 3.2 更新 `server/Dockerfile.api`：COPY 路径与 `-pl api`
- [√] 3.3 更新 `server/Dockerfile.edge`：COPY 路径与 `-pl edge`
- [√] 3.4 移动并更新 split package 检测脚本：`server/tooling/check_split_packages.py`（默认 modules=`platform,api,edge`）
- [√] 3.5 更新 `.github/workflows/ci.yml`：split package 检测脚本路径改为 `server/tooling/check_split_packages.py`

### 4. 文档与知识库同步（SSOT）

- [√] 4.1 更新 `README.md`：后端运行命令改为 `-pl api` / `-pl edge`
- [√] 4.2 更新 `.helloagents/project.md`：目录结构与模块名更新
- [√] 4.3 更新 `.helloagents/wiki/overview.md`：模块索引表 `api/edge` + 文档链接更新
- [√] 4.4 重命名并更新 `.helloagents/wiki/modules/api-service.md` → `.helloagents/wiki/modules/api.md`
- [√] 4.5 重命名并更新 `.helloagents/wiki/modules/redirect-edge.md` → `.helloagents/wiki/modules/edge.md`
- [√] 4.6 更新 `.helloagents/wiki/arch.md`：边界表与模块引用更新
- [√] 4.7 更新 `.helloagents/CHANGELOG.md`（Unreleased）：更新引用并补充本次重命名说明

### 5. 验证与收尾

- [√] 5.1 运行后端构建与测试：`mvn -f server/pom.xml test`
- [√] 5.2 旧目录路径清理核对：`rg -n "server/(api-service|redirect-edge|platform-shared|dev-tools)"`（全仓，确认无结果或仅剩历史归档）
- [√] 5.3 归档方案包：`python3 -X utf8 '/home/feng/.codex/helloagents/scripts/migrate_package.py' 202602252307_rename-server-modules-minimal --status completed --path '.'`

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-25 23:12 | 2.1-2.4 目录重命名 | completed | git mv: api/edge/platform/tooling |
| 2026-02-25 23:15 | 3.1-3.5 构建与脚本更新 | completed | 更新 server/pom.xml、Dockerfile、CI、split package 脚本 |
| 2026-02-25 23:17 | 4.1-4.7 文档与知识库同步 | completed | README + .helloagents/wiki/* 全量对齐 |
| 2026-02-25 23:21 | 5.1 后端测试 | completed | mvn -f server/pom.xml test 通过 |
| 2026-02-25 23:26 | 5.3 方案包归档 | completed | 迁移至 `.helloagents/archive/2026-02/` 并更新归档索引 |

---

## 执行备注

> 备注：按规范应调用 [RLM:pkg_keeper] 填充与维护方案包，但本次子代理启动失败（agent thread limit reached），已由主代理降级执行并手工维护方案包内容与任务状态。[降级执行]
