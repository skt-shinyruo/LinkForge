package com.linkforge.foundation.web;

import org.slf4j.MDC;

/**
 * 当前线程正在处理的请求关联 ID。
 *
 * <p>仅供 HTTP 运行时和同线程内的日志/错误响应读取。值由 {@code RequestIdFilter} 写入 MDC 并负责清理，
 * 不会跨线程传播；异步任务需要显式传递所需的关联信息。</p>
 */
public final class RequestId {

    public static final String MDC_KEY = "requestId";

    private RequestId() {
    }

    /** 返回当前线程的请求 ID；非 HTTP 线程或过滤器执行前后可能为 {@code null}。 */
    public static String get() {
        return MDC.get(MDC_KEY);
    }
}
