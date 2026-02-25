# 任务清单：P1 生命周期闭环（Link/User/API Key）+ Links UI 对齐

Directory: `.helloagents/history/2026-02/202602201407_lifecycle_governance_closure/`

---

## 1. DB 迁移（short_links）
- [√] 1.1 新增 `archived_at` 字段与必要索引：`server/api-app/src/main/resources/db/migration/V5__add_lifecycle_fields.sql`

## 2. 后端：短链生命周期（API Service）
- [√] 2.1 扩展 `ShortLinkEntity`：增加 `archivedAt`：`server/api-app/src/main/java/com/linkforge/shortlink/entity/ShortLinkEntity.java`
- [√] 2.2 扩展 `ShortLinkRepository.search`：默认排除归档，支持 archived 参数：`server/api-app/src/main/java/com/linkforge/shortlink/repo/ShortLinkRepository.java`
- [√] 2.3 扩展 `ShortLinkService`：archive/restore/delete + 清理关联数据 + cache eviction：`server/api-app/src/main/java/com/linkforge/shortlink/service/ShortLinkService.java`
- [√] 2.4 扩展 `ShortLinkController`：新增归档/恢复/删除端点 + list archived 过滤：`server/api-app/src/main/java/com/linkforge/shortlink/web/ShortLinkController.java`

## 3. 后端：用户治理（API Service）
- [√] 3.1 扩展 `UserAdminService`：disable/enable/reset password：`server/api-app/src/main/java/com/linkforge/iam/service/UserAdminService.java`
- [√] 3.2 扩展 `UserAdminController`：新增端点：`server/api-app/src/main/java/com/linkforge/iam/web/UserAdminController.java`

## 4. 后端：API Key 治理（API Service）
- [√] 4.1 扩展 `ApiKeyService`：disable/enable/rotate：`server/api-app/src/main/java/com/linkforge/iam/api/ApiKeyService.java`
- [√] 4.2 扩展 `ApiKeyAdminController`：新增端点：`server/api-app/src/main/java/com/linkforge/iam/web/ApiKeyAdminController.java`

## 5. Edge 侧一致性（Redirect Edge）
- [√] 5.1 ShortLink 回源查询过滤 `archived_at`（滚动升级兼容）：`server/edge-app/src/main/java/com/linkforge/redirect/service/ShortLinkLookupRepository.java`

## 6. 前端：Links 管理页能力对齐（Admin UI）
- [√] 6.1 Links 创建补齐：customCode/expiresAt/tags：`web/src/views/LinksView.vue`
- [√] 6.2 Links 编辑补齐：originalUrl/note/expiresAt/tags：`web/src/views/LinksView.vue`
- [√] 6.3 Links 生命周期操作：归档/恢复/删除 + 归档筛选：`web/src/views/LinksView.vue`
- [√] 6.4 前端类型同步：补齐 `archivedAt`：`web/src/services/types.ts`

## 7. 测试
- [√] 7.1 API 集成测试覆盖：归档/恢复/删除、API Key rotate/disable、用户禁用/启用：`server/api-app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
- [√] 7.2 Edge 集成测试覆盖：归档链接 redirect 不可达：`server/edge-app/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java`

## 8. 文档同步（知识库）
- [√] 8.1 更新数据模型文档：`.helloagents/wiki/data.md`
- [√] 8.2 更新 shortlink 模块文档：`.helloagents/wiki/modules/shortlink.md`
- [√] 8.3 更新 iam 模块文档：`.helloagents/wiki/modules/iam.md`
- [√] 8.4 更新变更日志：`.helloagents/CHANGELOG.md`
- [√] 8.5 迁移方案包到 history 并更新索引：`.helloagents/history/index.md`
