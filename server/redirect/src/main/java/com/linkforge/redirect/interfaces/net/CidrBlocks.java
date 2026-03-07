package com.linkforge.redirect.interfaces.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CIDR 列表工具：用于从配置解析、并进行 contains 判断。
 */
public final class CidrBlocks {

    private CidrBlocks() {
    }

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
