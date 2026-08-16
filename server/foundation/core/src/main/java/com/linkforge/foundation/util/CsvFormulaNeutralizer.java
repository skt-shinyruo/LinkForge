package com.linkforge.foundation.util;

/**
 * 中和电子表格会解释为公式的用户可控 CSV 文本。
 *
 * <p>CSV quoting 只保证语法正确，不能阻止表格软件执行以 {@code = + - @} 开头的单元格。本工具
 * 保留原始空白和文本，只在首个非空白字符为公式标记时在单元格最前添加单引号。数值、布尔值和时间等
 * 系统生成字段应继续以原始类型交给 CSV 编码器，不经过本工具。</p>
 */
public final class CsvFormulaNeutralizer {

    private CsvFormulaNeutralizer() {
    }

    public static String neutralizeUserText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (!Character.isWhitespace(current)
                    && !Character.isSpaceChar(current)
                    && current != '\uFEFF') {
                break;
            }
            index++;
        }
        if (index >= value.length()) {
            return value;
        }
        char firstContent = value.charAt(index);
        return firstContent == '=' || firstContent == '+' || firstContent == '-' || firstContent == '@'
                ? "'" + value
                : value;
    }
}
