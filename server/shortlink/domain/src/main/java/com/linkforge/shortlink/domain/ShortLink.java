package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.DELETE_REQUIRES_ARCHIVE;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_LINK_ID;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_REDIRECT_STATUS_CODE;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_TENANT_ID;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.NOTE_TOO_LONG;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.UPDATE_NOT_ALLOWED_WHEN_ARCHIVED;

/**
 * 短链聚合根，集中维护短链自身的数据约束、归档规则和待发布领域事件。
 *
 * <p>聚合强制 {@code id > 0}、{@code tenantId > 0}、短码与原始地址非空，备注最长 512 个
 * Java 字符，重定向状态码只能为 301、302 或空。应用、域名的归属关系以及操作者权限属于跨上下文规则，
 * 由应用层在构造聚合前校验；因此 {@code applicationId}、{@code domainId} 在本类型中允许为空且创建后不可变。</p>
 *
 * <p>{@link ShortLinkLifecycleState} 表示发布阶段，{@code archivedAtUtc} 表示可恢复的归档状态，两者彼此独立。
 * 更新用例必须先调用 {@link #requireNotArchivedForUpdate()}；字段级变更方法本身不重复执行归档检查，也不会逐项产生
 * {@link ShortLinkUpdated}。应用层应在一次更新全部落库成功后调用 {@link #markUpdated(LocalDateTime)}，形成一条更新事件。</p>
 *
 * <p>时间字段使用不携带时区的 {@link LocalDateTime}，但业务语义一律为 UTC。领域事件按业务操作发生顺序暂存在聚合内；
 * {@code create} 会记录创建事件，{@code rehydrate} 不会重放历史事件。调用 {@link #pullDomainEvents()} 会以原顺序取出
 * 不可变快照并清空缓冲区，因此发布失败后的重试与事务一致性必须由应用层和 outbox 负责。</p>
 */
public class ShortLink {

    private final long id;
    private final long tenantId;
    private final Long applicationId;
    private final Long domainId;
    private final ShortCode code;
    private ShortLinkLifecycleState lifecycleState;
    private long version;

    private HttpUrl originalUrl;
    private String note;
    private boolean enabled;
    /** 业务语义为 UTC；持久化为不带时区的 MySQL DATETIME。 */
    private LocalDateTime expiresAtUtc;
    /** 业务语义为 UTC；非空同时表示聚合处于已归档状态。 */
    private LocalDateTime archivedAtUtc;
    private Integer redirectStatusCode;
    private boolean previewEnabled;
    private HttpUrl unavailableLandingUrl;
    private QueryForwardMode queryForwardMode;
    private QueryForwardAllowlist queryForwardAllowlist;

    private final long createdBy;
    private final CreatedByType createdByType;
    private LocalDateTime createdAtUtc;
    private LocalDateTime updatedAtUtc;
    private final List<ShortLinkDomainEvent> domainEvents = new ArrayList<>();

    private ShortLink(
            long id,
            long tenantId,
            Long applicationId,
            Long domainId,
            ShortCode code,
            ShortLinkLifecycleState lifecycleState,
            HttpUrl originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAtUtc,
            LocalDateTime archivedAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy,
            long version,
            LocalDateTime createdAtUtc,
            LocalDateTime updatedAtUtc
    ) {
        if (id <= 0) {
            throw new ShortLinkDomainException(INVALID_LINK_ID, "linkId 必须 > 0");
        }
        if (tenantId <= 0) {
            throw new ShortLinkDomainException(INVALID_TENANT_ID, "tenantId 必须 > 0");
        }
        if (code == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_CODE, "短码不能为空");
        }
        if (originalUrl == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.domainId = domainId;
        this.code = code;
        this.lifecycleState = lifecycleState == null ? ShortLinkLifecycleState.ACTIVE : lifecycleState;
        this.originalUrl = originalUrl;
        this.note = normalizeNote(note);
        this.enabled = enabled;
        this.expiresAtUtc = expiresAtUtc;
        this.archivedAtUtc = archivedAtUtc;
        this.redirectStatusCode = validateRedirectStatusCode(redirectStatusCode);
        this.previewEnabled = previewEnabled;
        this.unavailableLandingUrl = unavailableLandingUrl;
        this.queryForwardMode = queryForwardMode;
        this.queryForwardAllowlist = queryForwardAllowlist == null ? QueryForwardAllowlist.empty() : queryForwardAllowlist;
        this.createdBy = createdBy;
        this.createdByType = createdByType == null ? CreatedByType.USER : createdByType;
        this.version = Math.max(version, 0L);
        this.createdAtUtc = createdAtUtc;
        this.updatedAtUtc = updatedAtUtc;
    }

    /**
     * 创建不绑定应用和自定义域名的短链。
     *
     * <p>该便捷入口委托给完整创建方法，生命周期默认为 {@link ShortLinkLifecycleState#ACTIVE}，并记录一条创建事件。</p>
     */
    public static ShortLink create(
            long id,
            long tenantId,
            ShortCode code,
            HttpUrl originalUrl,
            String note,
            Boolean enabled,
            LocalDateTime expiresAtUtc,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy
    ) {
        return create(
                id,
                tenantId,
                null,
                null,
                code,
                ShortLinkLifecycleState.ACTIVE,
                originalUrl,
                note,
                enabled,
                expiresAtUtc,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy
        );
    }

    /**
     * 创建新的短链聚合并记录 {@link ShortLinkCreated}。
     *
     * <p>{@code enabled == null} 时默认为启用，{@code previewEnabled == null} 时默认为关闭；生命周期为空时默认为
     * {@link ShortLinkLifecycleState#ACTIVE}，查询透传白名单为空时归一化为空白名单。版本从 0 开始，创建/更新时间由
     * 持久化编排在后续设置。该方法不校验应用与域名是否匹配，也不检查短码唯一性。</p>
     */
    public static ShortLink create(
            long id,
            long tenantId,
            Long applicationId,
            Long domainId,
            ShortCode code,
            ShortLinkLifecycleState lifecycleState,
            HttpUrl originalUrl,
            String note,
            Boolean enabled,
            LocalDateTime expiresAtUtc,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy
    ) {
        boolean en = enabled == null || enabled;
        boolean preview = previewEnabled != null && previewEnabled;
        ShortLink link = new ShortLink(
                id,
                tenantId,
                applicationId,
                domainId,
                code,
                lifecycleState,
                originalUrl,
                note,
                en,
                expiresAtUtc,
                null,
                redirectStatusCode,
                preview,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                0L,
                null,
                null
        );
        link.recordDomainEvent(new ShortLinkCreated(link.id, link.tenantId, link.domainId, link.code.value()));
        return link;
    }

    /**
     * 从旧版、不包含应用与域名范围的持久化记录恢复聚合。
     *
     * <p>恢复只重建当前状态，不产生创建或更新事件；缺失的生命周期按 {@code ACTIVE} 处理。</p>
     */
    public static ShortLink rehydrate(
            long id,
            long tenantId,
            ShortCode code,
            HttpUrl originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAtUtc,
            LocalDateTime archivedAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy,
            long version,
            LocalDateTime createdAtUtc,
            LocalDateTime updatedAtUtc
    ) {
        return rehydrate(
                id,
                tenantId,
                null,
                null,
                code,
                ShortLinkLifecycleState.ACTIVE,
                originalUrl,
                note,
                enabled,
                expiresAtUtc,
                archivedAtUtc,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                version,
                createdAtUtc,
                updatedAtUtc
        );
    }

    /**
     * 从持久化快照恢复完整聚合。
     *
     * <p>该入口与创建入口共享值约束和空值默认规则，但不会记录领域事件。负版本会归一化为 0；数据库行版本的合法性
     * 与乐观锁冲突仍由仓储负责。</p>
     */
    public static ShortLink rehydrate(
            long id,
            long tenantId,
            Long applicationId,
            Long domainId,
            ShortCode code,
            ShortLinkLifecycleState lifecycleState,
            HttpUrl originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAtUtc,
            LocalDateTime archivedAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy,
            long version,
            LocalDateTime createdAtUtc,
            LocalDateTime updatedAtUtc
    ) {
        return new ShortLink(
                id,
                tenantId,
                applicationId,
                domainId,
                code,
                lifecycleState,
                originalUrl,
                note,
                enabled,
                expiresAtUtc,
                archivedAtUtc,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                version,
                createdAtUtc,
                updatedAtUtc
        );
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public Long applicationId() {
        return applicationId;
    }

    public Long domainId() {
        return domainId;
    }

    public ShortCode code() {
        return code;
    }

    public ShortLinkLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public HttpUrl originalUrl() {
        return originalUrl;
    }

    public String note() {
        return note;
    }

    public boolean enabled() {
        return enabled;
    }

    public LocalDateTime expiresAtUtc() {
        return expiresAtUtc;
    }

    public LocalDateTime archivedAtUtc() {
        return archivedAtUtc;
    }

    public Integer redirectStatusCode() {
        return redirectStatusCode;
    }

    public boolean previewEnabled() {
        return previewEnabled;
    }

    public HttpUrl unavailableLandingUrl() {
        return unavailableLandingUrl;
    }

    public QueryForwardMode queryForwardMode() {
        return queryForwardMode;
    }

    public QueryForwardAllowlist queryForwardAllowlist() {
        return queryForwardAllowlist;
    }

    public long createdBy() {
        return createdBy;
    }

    public long version() {
        return version;
    }

    public CreatedByType createdByType() {
        return createdByType;
    }

    public LocalDateTime createdAtUtc() {
        return createdAtUtc;
    }

    public LocalDateTime updatedAtUtc() {
        return updatedAtUtc;
    }

    /**
     * 按记录顺序取出当前待发布事件，并清空聚合内的事件缓冲区。
     *
     * <p>返回列表是不可变快照。该操作具有破坏性：再次调用会得到空列表，除非期间发生了新的领域操作。</p>
     *
     * @return 按业务操作顺序排列的不可变事件列表
     */
    public List<ShortLinkDomainEvent> pullDomainEvents() {
        List<ShortLinkDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void recordDomainEvent(ShortLinkDomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "event"));
    }

    /**
     * 将短链归档，并在状态首次变化时记录归档事件。
     *
     * <p>重复归档是幂等操作：保留第一次的归档时间，不追加事件并返回 {@code false}。归档不修改
     * {@link #lifecycleState()} 或 {@link #enabled()}。</p>
     *
     * @param nowUtc 归档发生时间，调用方必须传入 UTC 语义的非空时间
     * @return 本次是否实际从未归档变为已归档
     */
    public boolean archive(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        if (archivedAtUtc != null) {
            return false;
        }
        archivedAtUtc = nowUtc;
        recordDomainEvent(new ShortLinkArchived(id, tenantId, domainId, code.value(), nowUtc));
        return true;
    }

    /**
     * 恢复已归档短链，并在状态实际变化时记录恢复事件。
     *
     * <p>未归档时调用是幂等的，不追加事件并返回 {@code false}。恢复只清除归档标记，不改变发布阶段与启用标记。</p>
     *
     * @return 本次是否实际清除了归档状态
     */
    public boolean restore() {
        if (archivedAtUtc == null) {
            return false;
        }
        archivedAtUtc = null;
        recordDomainEvent(new ShortLinkRestored(id, tenantId, domainId, code.value()));
        return true;
    }

    /**
     * 保护普通编辑入口，拒绝直接修改已归档短链。
     *
     * <p>应用层应在执行任何字段变更前调用本方法；恢复命令不受此限制。</p>
     */
    public void requireNotArchivedForUpdate() {
        if (archivedAtUtc != null) {
            throw new ShortLinkDomainException(UPDATE_NOT_ALLOWED_WHEN_ARCHIVED, "短链已归档，请先恢复后再编辑");
        }
    }

    /**
     * 保护物理删除入口，要求先完成可审计、可恢复的归档步骤。
     */
    public void requireArchivedBeforeDelete() {
        if (archivedAtUtc == null) {
            throw new ShortLinkDomainException(DELETE_REQUIRES_ARCHIVE, "删除前请先归档（可避免误删）");
        }
    }

    /**
     * 在聚合已归档的前提下记录删除意图。
     *
     * <p>本方法不会从仓储删除记录，也不会在聚合内维护“已删除”标记；应用层应在同一事务中完成关联数据和聚合行删除，
     * 再发布该事件。</p>
     *
     * @param nowUtc 删除发生时间，调用方必须传入 UTC 语义的非空时间
     */
    public void markDeleted(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        requireArchivedBeforeDelete();
        recordDomainEvent(new ShortLinkDeleted(id, tenantId, domainId, code.value(), nowUtc));
    }

    /**
     * 标记一次完整业务更新完成，同时刷新更新时间并记录单条更新事件。
     *
     * <p>字段级修改方法不自动发事件，以免一次请求产生多个中间态事件。调用方应在所有字段及关联数据持久化成功后调用
     * 本方法；事件仅保存路由身份和发生时间，外发快照由应用层读取此时的聚合最终状态构造。</p>
     *
     * @param updatedAtUtc 更新完成时间，调用方必须传入 UTC 语义的非空时间
     */
    public void markUpdated(LocalDateTime updatedAtUtc) {
        Objects.requireNonNull(updatedAtUtc, "updatedAtUtc must be provided in UTC");
        this.updatedAtUtc = updatedAtUtc;
        recordDomainEvent(new ShortLinkUpdated(id, tenantId, domainId, code.value(), updatedAtUtc));
    }

    public void changeOriginalUrl(HttpUrl newUrl) {
        if (newUrl == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        this.originalUrl = newUrl;
    }

    /**
     * 设置发布阶段；空值归一化为 {@link ShortLinkLifecycleState#ACTIVE}。
     *
     * <p>领域层当前不限制阶段之间的转换路径。该阶段不等价于归档状态，重定向链路仅将 {@code ACTIVE} 视为可用阶段。</p>
     */
    public void setLifecycleState(ShortLinkLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState == null ? ShortLinkLifecycleState.ACTIVE : lifecycleState;
    }

    public void changeNote(String note) {
        this.note = normalizeNote(note);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setExpiresAtUtc(LocalDateTime expiresAtUtc) {
        this.expiresAtUtc = expiresAtUtc;
    }

    public void clearExpiresAtUtc() {
        this.expiresAtUtc = null;
    }

    public void setRedirectStatusCode(Integer redirectStatusCode) {
        this.redirectStatusCode = validateRedirectStatusCode(redirectStatusCode);
    }

    public void clearRedirectStatusCode() {
        this.redirectStatusCode = null;
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    public void setUnavailableLandingUrl(HttpUrl url) {
        this.unavailableLandingUrl = url;
    }

    public void clearUnavailableLandingUrl() {
        this.unavailableLandingUrl = null;
    }

    /**
     * 设置短链级查询参数透传模式。
     *
     * <p>空值表示未设置短链级覆盖，重定向链路会继续采用全局配置；它与显式 {@link QueryForwardMode#OFF} 含义不同。</p>
     */
    public void setQueryForwardMode(QueryForwardMode mode) {
        this.queryForwardMode = mode;
    }

    public void clearQueryForwardMode() {
        this.queryForwardMode = null;
    }

    public void setQueryForwardAllowlist(QueryForwardAllowlist allowlist) {
        this.queryForwardAllowlist = allowlist == null ? QueryForwardAllowlist.empty() : allowlist;
    }

    public void setCreatedAtUtc(LocalDateTime createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }

    /**
     * 在仓储乐观锁更新成功后推进聚合内版本，使返回 DTO 与已提交行版本一致。
     *
     * <p>该方法本身不执行并发校验，调用方不得在仓储更新失败时推进版本。</p>
     */
    public void incrementVersion() {
        this.version++;
    }

    private static Integer validateRedirectStatusCode(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != 301 && status != 302) {
            throw new ShortLinkDomainException(INVALID_REDIRECT_STATUS_CODE, "redirectStatusCode 仅支持 301/302");
        }
        return status;
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        if (note.length() > 512) {
            throw new ShortLinkDomainException(NOTE_TOO_LONG, "备注过长");
        }
        return note;
    }
}
