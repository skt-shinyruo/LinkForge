package com.linkforge.foundation.web;

/**
 * 当前线程正在处理的请求关联 ID。
 *
 * <p>仅供 HTTP 运行时和同线程内的日志/错误响应读取。值由 {@code RequestIdFilter} 设置并负责清理，
 * 不会跨线程传播；异步任务需要显式传递所需的关联信息。</p>
 */
public final class RequestId {

    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    private RequestId() {
    }

    /** 返回当前线程的请求 ID；非 HTTP 线程或过滤器执行前后可能为 {@code null}。 */
    public static String get() {
        return TL.get();
    }

    /**
     * 设置当前线程的请求 ID。
     *
     * <p>调用方必须在同一逻辑作用域内调用 {@link #clear()}；通常不应绕过运行时过滤器直接调用。</p>
     */
    public static void set(String requestId) {
        TL.set(requestId);
    }

    /** 清理线程局部值，防止容器工作线程复用时泄漏到后续请求。 */
    public static void clear() {
        TL.remove();
    }
}
