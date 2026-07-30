package com.linkforge.contract.platform;

import java.util.Optional;

/**
 * 在租户边界内完成域名 ID 与 hostname 互查的发布契约。
 *
 * <p>该端口不替调用方做 hostname trim、小写化、去端口或 IDN 规范化；输入规范必须与使用场景一致。
 * {@link Optional#empty()} 只能表示本实现没有给出结果，不能单独证明 hostname 在全局不存在：它还可能表示
 * 跨租户、域名不存在，或默认实现不支持反查。</p>
 */
public interface DomainHostnameLookupPort {

    /**
     * 查找租户可见域名的已规范化 hostname。
     *
     * @param tenantId 当前租户，必须大于 {@code 0}
     * @param domainId 域名 ID，必须大于 {@code 0}
     * @return 已知 hostname；域名缺失、跨租户或实现无法读取时返回空
     */
    Optional<String> findDomainHostname(long tenantId, long domainId);

    /**
     * 在租户范围内反查 hostname 对应的域名 ID。
     *
     * <p>默认实现返回空，明确表示该能力可选且未实现；调用方不能把空值作为“可以创建该 hostname”或
     * “不存在同名域名”的证据。支持实现应接受调用方已规范化的 hostname，并自行维持租户隔离。</p>
     *
     * @param tenantId 当前租户，必须大于 {@code 0}
     * @param hostname 调用方已按其协议规范化的 hostname
     * @return 该租户可见的域名 ID；未命中或不支持反查时返回空
     */
    default Optional<Long> findDomainIdByHostname(long tenantId, String hostname) {
        return Optional.empty();
    }
}
