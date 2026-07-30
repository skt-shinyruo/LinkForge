package com.linkforge.contract.api;

/**
 * API 响应使用的稳定错误码抽象。
 *
 * <p>共享层只规定 code、默认消息和建议 HTTP status；各限界上下文维护自己的枚举，
 * 避免形成全局巨型错误码类。对外发布的实现必须使用非零、全局可观察且不重复的 code；一经发布，
 * code 和默认消息都不得改义、复用或删除。</p>
 */
public interface AppErrorCode {

    /**
     * 返回稳定的数字业务码。
     *
     * @return 非零且在所有已发布 API 中唯一的业务码；客户端可据此分支，不能从默认消息推断语义
     */
    int getCode();

    /**
     * 返回未提供更具体消息时使用的安全默认消息。
     *
     * @return 可公开展示的默认消息，不得包含异常细节、凭据或内部地址
     */
    String getDefaultMessage();

    /**
     * 返回与该业务码对应的建议 HTTP status。
     *
     * <p>实现应返回有效的 HTTP status 值。无法从业务码推导 HTTP 语义的上下文默认返回 400；HTTP
     * 映射层可以为其协议另行决定最终 status，不能反向从 status 推断业务码。</p>
     *
     * @return 建议 HTTP status，默认是 400
     */
    default int getHttpStatus() {
        return 400;
    }
}
