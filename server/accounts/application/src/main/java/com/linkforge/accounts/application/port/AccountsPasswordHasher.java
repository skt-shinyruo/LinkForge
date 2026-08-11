package com.linkforge.accounts.application.port;

/**
 * 账户密码及历史凭据摘要的单向哈希端口。
 *
 * <p>实现必须使用适合用户密码存储、包含随机盐的慢哈希算法，因此同一明文的编码结果不要求相等。
 * 新 API Key 使用独立的 peppered HMAC codec；本端口只负责兼容验证及迁移历史 BCrypt Key。
 * 明文仅可在调用栈内短暂存在，不得记录日志或落库。</p>
 */
public interface AccountsPasswordHasher {

    /**
     * 编码非空明文。该操作不是幂等转换，调用方不得通过比较两个编码结果判断明文是否相同。
     *
     * @return 可持久化的哈希串
     */
    String encode(String raw);

    /**
     * 使用编码串内的算法参数和盐校验明文，避免直接比较哈希字符串。
     *
     * @return 明文与编码串匹配时返回 {@code true}
     */
    boolean matches(String raw, String encoded);
}
