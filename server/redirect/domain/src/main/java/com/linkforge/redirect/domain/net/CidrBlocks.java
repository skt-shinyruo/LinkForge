package com.linkforge.redirect.domain.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CIDR 列表工具：用于从配置解析、并进行 contains 判断。
 */
public final class CidrBlocks {

    private CidrBlocks() {
    }

    /**
     * 将配置列表解析成不可变 CIDR 集合。
     *
     * <p>空元素被忽略，任一非空非法项会带上配置字段名抛出异常；不能静默跳过，否则 trusted proxy 或
     * denylist 会在配置错误时悄然缩小。</p>
     */
    public static List<CidrBlock> parseList(List<String> raw, String fieldName) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<CidrBlock> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s == null || s.trim().isBlank()) {
                continue;
            }
            try {
                out.add(CidrBlock.parse(s));
            } catch (IllegalArgumentException e) {
                String name = fieldName == null ? "cidrList" : fieldName;
                throw new IllegalArgumentException("配置 " + name + " 包含不合法值: " + s + " (" + e.getMessage() + ")");
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 判断地址是否匹配任一网段；空列表按不匹配处理。
     */
    public static boolean containsAny(List<CidrBlock> blocks, String ip) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        for (CidrBlock b : blocks) {
            if (b.contains(ip)) {
                return true;
            }
        }
        return false;
    }
}
