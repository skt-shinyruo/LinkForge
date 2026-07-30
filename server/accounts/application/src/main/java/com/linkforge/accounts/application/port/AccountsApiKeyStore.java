package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 的持久化端口。
 *
 * <p>该端口不自行划分事务，写操作加入调用方当前事务。新增不是幂等操作，主键或约束冲突应向上抛出；
 * 更新按标识覆盖可变字段。所有 {@link LocalDateTime} 均表示 UTC 墙上时间，数据库列本身不携带时区。</p>
 */
public interface AccountsApiKeyStore {

    /**
     * 新增 API Key。明文 secret 绝不能通过该端口持久化。
     */
    void insert(ApiKey apiKey);

    /**
     * 按标识读取 API Key。
     *
     * @return 记录不存在时返回 {@code null}
     */
    ApiKey findById(Long apiKeyId);

    /**
     * 查询租户下的全部 API Key，按创建时间倒序返回。
     *
     * @return 无记录时返回空列表，不返回 {@code null}
     */
    List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    /**
     * 按 {@link ApiKey#id()} 更新 API Key 的可变持久化字段。
     */
    void update(ApiKey apiKey);

    /**
     * 更新最近使用时间。该审计字段不参与认证正确性，调用方可对写入进行节流并在失败时继续认证。
     *
     * @param lastUsedAt UTC 时间
     */
    void updateLastUsedAt(Long apiKeyId, LocalDateTime lastUsedAt);

    /**
     * API Key 的持久化快照。
     *
     * <p>{@code keyHash} 仅保存不可逆摘要；{@code applicationId} 对历史未绑定记录可为 {@code null}，
     * 此类记录不得通过当前认证。{@code lastUsedAt} 在从未使用时为 {@code null}；创建时
     * {@code createdAt} 可为 {@code null}，由数据库默认值生成。两个时间字段均按 UTC 解释。</p>
     */
    record ApiKey(
            Long id,
            Long tenantId,
            Long applicationId,
            String name,
            String keyHash,
            String status,
            LocalDateTime lastUsedAt,
            LocalDateTime createdAt
    ) {
    }
}
