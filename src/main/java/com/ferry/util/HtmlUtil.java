package com.ferry.util;

/** JSP 输出数据库文本前的最小 HTML 转义，避免用户输入直接进入页面结构。 */
public final class HtmlUtil {
    private HtmlUtil() {
    }

    public static String escape(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}
