package com.linkforge.foundation.util;

/**
 * 将非负 {@code long} 编码为固定字母表的 Base62 字符串。
 *
 * <p>字母表顺序固定为 {@code 0-9A-Za-z}，因此输出适合短码组成部分且对同一输入稳定。该类没有 decode
 * 方法，也不承诺字符串字典序与数值顺序一致。</p>
 */
public final class Base62 {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ALPHABET.length;

    private Base62() {
    }

    /**
     * 编码非负数；{@code 0} 编码为 {@code "0"}。
     *
     * @throws IllegalArgumentException value 为负数时抛出
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            int rem = (int) (v % BASE);
            sb.append(ALPHABET[rem]);
            v /= BASE;
        }
        return sb.reverse().toString();
    }
}
