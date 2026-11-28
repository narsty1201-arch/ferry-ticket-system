package com.ferry;

import com.ferry.util.ExcelExportUtil;
import com.ferry.util.XlsxWriter;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XlsxWriterTest {

    @Test
    public void writesValidMinimalWorkbookWithMultipleSheetsAndEscaping() throws Exception {
        XlsxWriter workbook = new XlsxWriter();
        workbook.addSheet("订单/明细")
                .header("订单号", "金额", "备注")
                .row("FD<001>&", 128.50, "第一行\n第二行")
                .row("=HYPERLINK(\"x\")", 0, "公式样式文本按普通字符串输出");
        workbook.addSheet("订单_明细")
                .header("名称", "数量")
                .row("测试", 2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.writeTo(out);

        Map<String, String> entries = unzip(out.toByteArray());
        assertTrue(entries.containsKey("[Content_Types].xml"));
        assertTrue(entries.containsKey("xl/workbook.xml"));
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"));
        assertTrue(entries.containsKey("xl/styles.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(entries.get("xl/workbook.xml").contains("订单_明细"));
        assertTrue(entries.get("xl/workbook.xml").contains("订单_明细(2)"));
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("FD&lt;001&gt;&amp;"));
        assertTrue(sheet.contains("<v>128.5</v>"));
        assertTrue(sheet.contains("t=\"inlineStr\""));
    }

    @Test
    public void exportTimeFormatterUsesReadableDateTime() {
        LocalDateTime local = LocalDateTime.of(2026, 11, 29, 8, 5);
        Date date = Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
        assertEquals("2026-11-29 08:05", ExcelExportUtil.formatTime(date));
    }

    private Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = zip.read(buffer)) >= 0) out.write(buffer, 0, read);
                entries.put(entry.getName(), new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
