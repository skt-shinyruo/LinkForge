package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkDomainEvent;
import com.linkforge.shortlink.domain.event.ShortLinkOwnershipChanged;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * 由应用层在构造聚合前校验；因此 {@code applicationId}、{@code domainId} 在本类型中允许为空，后续只能通过
 * 命名 ownership reconciliation 行为成对改变。</p>
 *
 * <p>{@link ShortLinkLifecycleState} 表示发布阶段，{@code archivedAtUtc} 表示可恢复的归档状态，两者彼此独立。
 * 更新、审批、归档、恢复、删除和 ownership reconciliation 的命名行为统一拥有状态守卫、单次版本推进与领域事件；
 * 字段赋值、版本推进和守卫均为聚合内部实现，不对应用层暴露直接 mutation。</p>
 *
 * <p>时间字段使用不携带时区的 {@link LocalDateTime}，但业务语义一律为 UTC。领域事件按业务操作发生顺序暂存在聚合内；
 * {@code create} 会记录创建事件，{@code rehydrate} 不会重放历史事件。调用 {@link #pullDomainEvents()} 会以原顺序取出
 * 不可变快照并清空缓冲区，因此发布失败后的重试与事务一致性必须由应用层和 outbox 负责。</p>
 */
public class ShortLink {

    private final long id;
    private final long tenantId;
    private Long applicationId;
    private Long domainId;
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
    private boolean deletionRequested;

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
        advanceVersion();
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
        advanceVersion();
        recordDomainEvent(new ShortLinkRestored(id, tenantId, domainId, code.value()));
        return true;
    }

    private void requireNotArchivedForUpdate() {
        if (archivedAtUtc != null) {
            throw new ShortLinkDomainException(UPDATE_NOT_ALLOWED_WHEN_ARCHIVED, "短链已归档，请先恢复后再编辑");
        }
    }

    private void requireArchivedBeforeDelete() {
        if (archivedAtUtc == null) {
            throw new ShortLinkDomainException(DELETE_REQUIRES_ARCHIVE, "删除前请先归档（可避免误删）");
        }
    }

    /**
     * 记录已归档短链的删除意图，并只在首次调用时推进版本和产生事件。
     *
     * <p>该行为不直接删除持久化行；仓储使用变化前版本完成 CAS 删除。重复调用同一内存聚合返回
     * {@code false}，不会重复推进版本或产生事件。</p>
     */
    public boolean delete(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        requireArchivedBeforeDelete();
        if (deletionRequested) {
            return false;
        }
        deletionRequested = true;
        advanceVersion();
        recordDomainEvent(new ShortLinkDeleted(id, tenantId, domainId, code.value(), nowUtc));
        return true;
    }

    /**
     * 校验并比较规范化 patch，不修改聚合。
     *
     * <p>审批分支和普通更新都使用该结果，避免分别维护两套“是否真的变化”规则。</p>
     */
    public ShortLinkChangeSet planPatch(ShortLinkPatch patch) {
        Objects.requireNonNull(patch, "patch");
        requireNotArchivedForUpdate();
        EnumSet<ShortLinkChangeSet.Field> changes = EnumSet.noneOf(ShortLinkChangeSet.Field.class);

        if (patch.originalUrl().isClear()) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        if (patch.originalUrl().isSet() && !Objects.equals(originalUrl, patch.originalUrl().value())) {
            changes.add(ShortLinkChangeSet.Field.ORIGINAL_URL);
        }

        if (!patch.note().isUnchanged()) {
            String requested = patch.note().isClear() ? null : normalizeNote(patch.note().value());
            if (!Objects.equals(note, requested)) {
                changes.add(ShortLinkChangeSet.Field.NOTE);
            }
        }
        if (patch.enabled().isClear()) {
            throw new IllegalArgumentException("enabled cannot be cleared");
        }
        if (patch.enabled().isSet() && enabled != patch.enabled().value()) {
            changes.add(ShortLinkChangeSet.Field.ENABLED);
        }
        if (!patch.expiresAtUtc().isUnchanged()) {
            LocalDateTime requested = patch.expiresAtUtc().isClear() ? null : patch.expiresAtUtc().value();
            if (!Objects.equals(expiresAtUtc, requested)) {
                changes.add(ShortLinkChangeSet.Field.EXPIRES_AT);
            }
        }
        if (!patch.redirectStatusCode().isUnchanged()) {
            Integer requested = patch.redirectStatusCode().isClear()
                    ? null
                    : validateRedirectStatusCode(patch.redirectStatusCode().value());
            if (!Objects.equals(redirectStatusCode, requested)) {
                changes.add(ShortLinkChangeSet.Field.REDIRECT_STATUS_CODE);
            }
        }
        if (patch.previewEnabled().isClear()) {
            throw new IllegalArgumentException("previewEnabled cannot be cleared");
        }
        if (patch.previewEnabled().isSet() && previewEnabled != patch.previewEnabled().value()) {
            changes.add(ShortLinkChangeSet.Field.PREVIEW_ENABLED);
        }
        if (!patch.unavailableLandingUrl().isUnchanged()) {
            HttpUrl requested = patch.unavailableLandingUrl().isClear() ? null : patch.unavailableLandingUrl().value();
            if (!Objects.equals(unavailableLandingUrl, requested)) {
                changes.add(ShortLinkChangeSet.Field.UNAVAILABLE_LANDING_URL);
            }
        }
        if (!patch.queryForwardMode().isUnchanged()) {
            QueryForwardMode requested = patch.queryForwardMode().isClear() ? null : patch.queryForwardMode().value();
            if (queryForwardMode != requested) {
                changes.add(ShortLinkChangeSet.Field.QUERY_FORWARD_MODE);
            }
        }
        if (!patch.queryForwardAllowlist().isUnchanged()) {
            QueryForwardAllowlist requested = patch.queryForwardAllowlist().isClear()
                    ? QueryForwardAllowlist.empty()
                    : patch.queryForwardAllowlist().value();
            if (!queryForwardAllowlist.values().equals(requested.values())) {
                changes.add(ShortLinkChangeSet.Field.QUERY_FORWARD_ALLOWLIST);
            }
        }
        if (!patch.lifecycleState().isUnchanged()) {
            ShortLinkLifecycleState requested = patch.lifecycleState().isClear()
                    ? ShortLinkLifecycleState.ACTIVE
                    : patch.lifecycleState().value();
            if (lifecycleState != requested) {
                changes.add(ShortLinkChangeSet.Field.LIFECYCLE_STATE);
            }
        }
        return new ShortLinkChangeSet(changes);
    }

    /**
     * 应用一次完整编辑，并由聚合统一推进版本、更新时间和单条更新事件。
     *
     * <p>{@code relatedStateChanged} 用于标签等与短链一起提交、但由独立持久化端口保存的关联状态。字段和关联状态都
     * 没有变化时，本方法幂等返回，不要求时间且不产生任何副作用。</p>
     */
    public ShortLinkChangeSet applyUpdate(
            ShortLinkPatch patch,
            boolean relatedStateChanged,
            LocalDateTime updatedAtUtc
    ) {
        ShortLinkChangeSet changes = planPatch(patch);
        if (!changes.hasChanges() && !relatedStateChanged) {
            return changes;
        }
        Objects.requireNonNull(updatedAtUtc, "updatedAtUtc must be provided in UTC");
        applyPlannedPatch(patch, changes);
        completeUpdatedMutation(updatedAtUtc);
        return changes;
    }

    /**
     * 执行已批准的目标地址变更。
     *
     * <p>只有未归档、绑定 domain 且处于 ACTIVE 发布阶段的短链可以执行审批。目标未变化时幂等返回；成功时只推进
     * 一次版本并产生一条更新事件。</p>
     */
    public boolean approveDestinationChange(HttpUrl approvedUrl, LocalDateTime changedAtUtc) {
        requireNotArchivedForUpdate();
        if (domainId == null || lifecycleState != ShortLinkLifecycleState.ACTIVE) {
            throw new ShortLinkDomainException(
                    ShortLinkDomainException.Reason.APPROVAL_REQUIRES_ACTIVE_SCOPED_LINK,
                    "目标地址审批要求短链绑定域名且处于 ACTIVE 状态"
            );
        }
        if (approvedUrl == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        if (Objects.equals(originalUrl, approvedUrl)) {
            return false;
        }
        Objects.requireNonNull(changedAtUtc, "changedAtUtc must be provided in UTC");
        originalUrl = approvedUrl;
        completeUpdatedMutation(changedAtUtc);
        return true;
    }

    /**
     * 把 legacy ownership 调整为给定 application/domain scope。
     *
     * <p>该 expand 行为只把 application/domain 都为空的 legacy link 绑定到一对正数 ID，不允许清空或换绑
     * 已有 ownership。相同已绑定 scope 的重复 reconciliation 幂等返回；成功时事件同时捕获旧、新 scope，
     * 供应用层可靠失效两套路由身份。</p>
     */
    public boolean reconcileOwnership(Long newApplicationId, Long newDomainId, LocalDateTime changedAtUtc) {
        validateOwnershipTarget(newApplicationId, newDomainId);
        if (Objects.equals(applicationId, newApplicationId) && Objects.equals(domainId, newDomainId)) {
            return false;
        }
        if (applicationId != null || domainId != null) {
            throw new ShortLinkDomainException(
                    ShortLinkDomainException.Reason.INVALID_OWNERSHIP_SCOPE,
                    "已有 ownership 不能重新绑定"
            );
        }
        Objects.requireNonNull(changedAtUtc, "changedAtUtc must be provided in UTC");
        Long previousApplicationId = applicationId;
        Long previousDomainId = domainId;
        applicationId = newApplicationId;
        domainId = newDomainId;
        updatedAtUtc = changedAtUtc;
        advanceVersion();
        recordDomainEvent(new ShortLinkOwnershipChanged(
                id,
                tenantId,
                previousApplicationId,
                previousDomainId,
                applicationId,
                domainId,
                code.value(),
                changedAtUtc
        ));
        return true;
    }

    private void applyPlannedPatch(ShortLinkPatch patch, ShortLinkChangeSet changes) {
        if (changes.changed(ShortLinkChangeSet.Field.ORIGINAL_URL)) {
            changeOriginalUrl(patch.originalUrl().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.NOTE)) {
            changeNote(patch.note().isClear() ? null : patch.note().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.ENABLED)) {
            setEnabled(patch.enabled().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.EXPIRES_AT)) {
            setExpiresAtUtc(patch.expiresAtUtc().isClear() ? null : patch.expiresAtUtc().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.REDIRECT_STATUS_CODE)) {
            setRedirectStatusCode(patch.redirectStatusCode().isClear() ? null : patch.redirectStatusCode().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.PREVIEW_ENABLED)) {
            setPreviewEnabled(patch.previewEnabled().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.UNAVAILABLE_LANDING_URL)) {
            setUnavailableLandingUrl(patch.unavailableLandingUrl().isClear() ? null : patch.unavailableLandingUrl().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.QUERY_FORWARD_MODE)) {
            setQueryForwardMode(patch.queryForwardMode().isClear() ? null : patch.queryForwardMode().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.QUERY_FORWARD_ALLOWLIST)) {
            setQueryForwardAllowlist(patch.queryForwardAllowlist().isClear()
                    ? QueryForwardAllowlist.empty()
                    : patch.queryForwardAllowlist().value());
        }
        if (changes.changed(ShortLinkChangeSet.Field.LIFECYCLE_STATE)) {
            setLifecycleState(patch.lifecycleState().isClear() ? null : patch.lifecycleState().value());
        }
    }

    private void changeOriginalUrl(HttpUrl newUrl) {
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
    private void setLifecycleState(ShortLinkLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState == null ? ShortLinkLifecycleState.ACTIVE : lifecycleState;
    }

    private void changeNote(String note) {
        this.note = normalizeNote(note);
    }

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void setExpiresAtUtc(LocalDateTime expiresAtUtc) {
        this.expiresAtUtc = expiresAtUtc;
    }

    private void setRedirectStatusCode(Integer redirectStatusCode) {
        this.redirectStatusCode = validateRedirectStatusCode(redirectStatusCode);
    }

    private void setPreviewEnabled(boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    private void setUnavailableLandingUrl(HttpUrl url) {
        this.unavailableLandingUrl = url;
    }

    /**
     * 设置短链级查询参数透传模式。
     *
     * <p>空值表示未设置短链级覆盖，重定向链路会继续采用全局配置；它与显式 {@link QueryForwardMode#OFF} 含义不同。</p>
     */
    private void setQueryForwardMode(QueryForwardMode mode) {
        this.queryForwardMode = mode;
    }

    private void setQueryForwardAllowlist(QueryForwardAllowlist allowlist) {
        this.queryForwardAllowlist = allowlist == null ? QueryForwardAllowlist.empty() : allowlist;
    }

    private void completeUpdatedMutation(LocalDateTime occurredAtUtc) {
        updatedAtUtc = occurredAtUtc;
        advanceVersion();
        recordDomainEvent(new ShortLinkUpdated(id, tenantId, domainId, code.value(), occurredAtUtc));
    }

    private void advanceVersion() {
        version++;
    }

    private static void validateOwnershipTarget(Long applicationId, Long domainId) {
        boolean bothPositive = applicationId != null && applicationId > 0 && domainId != null && domainId > 0;
        if (!bothPositive) {
            throw new ShortLinkDomainException(
                    ShortLinkDomainException.Reason.INVALID_OWNERSHIP_SCOPE,
                    "目标 applicationId 与 domainId 必须同时存在且为正数"
            );
        }
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
