# 任务清单: rename-server-modules

```yaml
@feature: rename-server-modules
@created: 2026-02-25
@status: pending
@mode: R2
```

<!-- LIVE_STATUS_BEGIN -->
状态: pending | 进度: 0/20 (0%) | 更新: 2026-02-25 22:00:50
当前: 1.1 扫描全仓旧模块名引用
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 0 | 0 | 0 | 20 |

---

## 任务列表

### 1. 影响面扫描（引用点清单）

- [ ] 1.1 扫描旧目录名引用：`rg -n "server/(api-app|edge-app|shared|tools)"`（全仓）
- [ ] 1.2 扫描旧 Maven 坐标引用：`rg -n "(linkforge-api-app|linkforge-edge-app|linkforge-shared)" server`
- [ ] 1.3 扫描 README/CI/deploy 引用：`rg -n "(api-app|edge-app|linkforge-api-app|linkforge-edge-app|linkforge-shared)" README.md deploy .github`

### 2. 目录重命名（git mv）

- [ ] 2.1 `git mv server/api-app server/api-service`
- [ ] 2.2 `git mv server/edge-app server/redirect-edge`
- [ ] 2.3 `git mv server/shared server/platform-shared`
- [ ] 2.4 `git mv server/tools server/dev-tools`（仅脚本目录）

### 3. Maven reactor 与坐标更新（POM）

- [ ] 3.1 更新 `server/pom.xml`：`<modules>` 改为 `platform-shared/api-service/redirect-edge`
- [ ] 3.2 更新 `server/api-service/pom.xml`：`artifactId/name` 改为 `linkforge-api-service`；依赖 `linkforge-platform-shared`
  - 依赖: 2.1, 2.3
- [ ] 3.3 更新 `server/redirect-edge/pom.xml`：`artifactId/name` 改为 `linkforge-redirect-edge`；依赖 `linkforge-platform-shared`
  - 依赖: 2.2, 2.3
- [ ] 3.4 更新 `server/platform-shared/pom.xml`：`artifactId/name` 改为 `linkforge-platform-shared`
  - 依赖: 2.3
- [ ] 3.5 全仓确保不再依赖旧 artifactId：`rg -n "linkforge-shared|linkforge-api-app|linkforge-edge-app" server`

### 4. 部署与文档同步

- [ ] 4.1 更新 `README.md`：`mvn -pl api-app/edge-app` 改为 `api-service/redirect-edge`；路径引用 `server/api-service` 等
- [ ] 4.2 更新 `deploy/docker-compose.yml`：build context / Dockerfile 路径引用同步更新
- [ ] 4.3 更新 `.github/workflows/*`（如存在）：构建/测试命令与路径引用同步更新
- [ ] 4.4 更新 `.helloagents/wiki/arch.md` 与相关文档：`server/api-app`→`server/api-service`、`server/edge-app`→`server/redirect-edge`、`server/shared`→`server/platform-shared`

### 5. 验证与收尾

- [ ] 5.1 运行构建与测试：`mvn -f server/pom.xml test`
- [ ] 5.2 旧名清理核对：`rg -n "(api-app|edge-app|linkforge-api-app|linkforge-edge-app|linkforge-shared|server/shared|server/tools)"`（全仓，确认只剩历史文档或无结果）
- [ ] 5.3 更新 `.helloagents/CHANGELOG.md`：记录本次“模块重命名（目录+artifactId）”并附方案包归档链接
- [ ] 5.4 归档方案包：`python -X utf8 /home/feng/.codex/helloagents/scripts/migrate_package.py 202602252150_rename-server-modules --status completed --path /home/feng/code/project/LinkForge`

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|

---

## 执行备注

> 备注：本方案包原计划调用 [RLM:pkg_keeper] 自动填充，但子代理通信中断，已由主代理降级执行并手工维护方案包内容。[降级执行]
