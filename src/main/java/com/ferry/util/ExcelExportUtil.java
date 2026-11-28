package com.ferry.util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.Date;

/** 统一设置 XLSX 下载响应头，兼容中文文件名。 */
public final class ExcelExportUtil {
    private ExcelExportUtil() {
    }

    public static void send(HttpServletResponse resp, String baseName, XlsxWriter workbook)
            throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = baseName + "_" + stamp + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"ferry-export-" + stamp + ".xlsx\"; filename*=UTF-8''" + encoded);
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        workbook.writeTo(resp.getOutputStream());
    }

    public static String formatTime(Object value) {
        if (value == null) return "";
        if (value instanceof Date) {
            return new SimpleDateFormat("yyyy\u002dMM\u002ddd HH:mm").format((Date) value);
        }
        return String.valueOf(value);
    }

    /** 聚合 SQL 结果通常以字符串返回，导出时把纯数字恢复成真正的 Excel 数值。 */
    public static Object number(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).trim();
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        return text;
    }
}
