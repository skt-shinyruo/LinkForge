package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户账户的持久化端口。
 *
 * <p>该端口不自行开启事务，写操作加入调用方当前事务。新增不是幂等操作，主键、全局邮箱等约束冲突
 * 应向上抛出；更新按用户标识覆盖账户状态、密码摘要和令牌版本。所有 {@link LocalDateTime} 均表示
 * UTC 墙上时间，数据库列本身不携带时区。</p>
 */
public interface AccountsUserStore {

    /**
     * 新增用户。只允许持久化密码摘要，不得传入明文密码。
     */
    void insert(UserData user);

    /**
     * 按标识读取用户。
     *
     * @return 记录不存在时返回 {@code null}
     */
    UserData findById(Long userId);

    /**
     * 按登录邮箱读取用户。端口不负责 trim、大小写折叠等规范化，匹配方式沿用持久层列规则；
     * 全局唯一约束保证至多一条记录。
     *
     * @return 记录不存在时返回 {@code null}
     */
    UserData findFirstByEmail(String email);

    /**
     * 查询租户内用户，按创建时间倒序返回。
     *
     * @return 无记录时返回空列表，不返回 {@code null}
     */
    List<UserData> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    /**
     * 按 {@link UserData#id()} 更新用户的可变持久化字段。
     *
     * <p>修改密码或主动注销时，调用方通过递增 {@code tokenVersion} 吊销旧令牌；端口不得自行重置该值。</p>
     */
    void update(UserData user);

    /**
     * 用户持久化快照。
     *
     * <p>{@code passwordHash} 只能是单向摘要。历史读取中的 {@code tokenVersion} 可能为 {@code null}，
     * 应用层将其按 {@code 0} 处理。创建时两个时间字段可为 {@code null} 以使用数据库默认值；读取后均按
     * UTC 解释。</p>
     */
    record UserData(
            Long id,
            Long tenantId,
            String email,
            String passwordHash,
            String status,
            Integer tokenVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
