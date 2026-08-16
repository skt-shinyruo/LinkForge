package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.ApplicationPolicy;

/**
 * 应用默认策略的写侧持久化端口。
 *
 * <p>当前策略与应用一一对应，并与应用开通处于同一外层事务。端口实现不得把写入拆成独立提交，
 * 以免留下已创建应用但缺少默认策略的中间状态。</p>
 */
public interface ApplicationPolicyRepository {

    /**
     * 插入应用策略；同一应用重复插入时传播唯一约束异常。
     */
    void insert(ApplicationPolicy policy);

}
