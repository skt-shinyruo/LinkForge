package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;

import java.util.List;

/**
 * 短链 CSV 导入导出用例边界。
 *
 * <p>CSV HTTP 入口属于租户管理操作，调用方必须先校验租户管理员角色。导入不是整批原子事务：
 * 每行在独立新事务中创建，失败行进入结果明细，已经成功的行不会回滚。导出是受分页和最大 offset 约束的
 * 只读快照，不保证与并发写入形成同一时点快照。</p>
 */
public interface ShortLinkCsvUseCase {

    /**
     * 按每行自带的应用/域名作用域导入。
     *
     * @param actor 已认证用户主体；调用方须已确认其租户管理员角色
     * @param rows 已解析的 CSV 行
     * @return 可部分成功的逐行导入汇总
     */
    ImportResult importCsv(UserActor actor, List<ShortLinkCsvImportRow> rows);

    /**
     * 在可选路径应用和域名作用域内导入；批次作用域会覆盖行内值。
     *
     * @param actor 已认证用户主体；调用方须已确认其租户管理员角色
     * @param request 导入行及批次作用域
     * @return 可部分成功的逐行导入汇总
     */
    ImportResult importCsv(UserActor actor, ScopedImportCsvRequest request);

    /**
     * 以可信租户和创建主体直接执行逐行导入。
     *
     * @param tenantId 已授权租户
     * @param createdBy 经可信调用方确认的创建主体
     * @param rows 已解析的 CSV 行
     * @return 可部分成功的逐行导入汇总
     */
    ImportResult importCsv(long tenantId, CreatedBy createdBy, List<ShortLinkCsvImportRow> rows);

    /**
     * 按用户请求作用域导出一页短链。
     *
     * @param actor 已认证用户主体；当前 HTTP 入口要求租户管理员
     * @param request 筛选与分页条件
     * @return CSV 行快照
     */
    ShortLinkCsvExport exportCsvForUser(UserActor actor, BrowseLinksRequest request);

    /**
     * 以可信租户和查询条件直接导出一页短链，不额外执行用户授权。
     *
     * @param tenantId 已授权租户
     * @param query 搜索条件
     * @param pageQuery 分页条件
     * @return CSV 行快照
     */
    ShortLinkCsvExport exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);
}
