package com.linkforge.foundation.runtime.startup;

import java.util.List;

/**
 * 由各上下文贡献给应用启动门禁的配置检查。
 *
 * <p>实现只向 {@code errors} 追加可读错误，不应自行终止 JVM 或吞掉配置错误；应用组合根在收集完所有
 * 检查结果后统一失败。{@code strict} 通常由 prod profile 或 {@code app.strict-config} 决定。</p>
 */
public interface StartupCheck {

    /** 执行纯配置校验并把所有发现的问题追加到 {@code errors}。 */
    void validate(boolean strict, List<String> errors);
}
