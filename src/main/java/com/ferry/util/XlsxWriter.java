package com.ferry.util;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 无第三方依赖的最小 XLSX 生成器。
 * 支持多工作表、表头样式、内联字符串、数字/布尔单元格和首行冻结。
 */
public final class XlsxWriter {
    private final List<Sheet> sheets = new ArrayList<Sheet>();
    private final Set<String> sheetNames = new HashSet<String>();

    public Sheet addSheet(String requestedName) {
        String name = uniqueSheetName(requestedName);
        Sheet sheet = new Sheet(name);
        sheets.add(sheet);
        return sheet;
    }

    public void writeTo(OutputStream output) throws IOException {
        if (sheets.isEmpty()) addSheet("Sheet1");
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
        put(zip, "[Content_Types].xml", contentTypes());
        put(zip, "_rels/.rels", rootRelationships());
        put(zip, "xl/workbook.xml", workbook());
        put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
        put(zip, "xl/styles.xml", styles());
        for (int i = 0; i < sheets.size(); i++) {
            put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", worksheet(sheets.get(i)));
        }
        zip.finish();
        zip.flush();
    }

    public static final class Sheet {
        private final String name;
        private final List<Row> rows = new ArrayList<Row>();

        private Sheet(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Sheet header(Object... values) {
            rows.add(new Row(values, true));
            return this;
        }

        public Sheet row(Object... values) {
            rows.add(new Row(values, false));
            return this;
        }
    }

    private static final class Row {
        private final Object[] values;
        private final boolean header;

        private Row(Object[] values, boolean header) {
            this.values = values == null ? new Object[0] : values;
            this.header = header;
        }
    }

    private String contentTypes() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
           .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
           .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
           .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
           .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
           .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheets.size(); i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
               .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
             + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
             + "<Relationship Id=\"rId1\" "
             + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
             + "Target=\"xl/workbook.xml\"/>"
             + "</Relationships>";
    }

    private String workbook() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
           .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
           .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
           .append("<bookViews><workbookView activeTab=\"0\"/></bookViews><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            xml.append("<sheet name=\"").append(xmlAttr(sheets.get(i).name))
               .append("\" sheetId=\"").append(i + 1)
               .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private String workbookRelationships() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
           .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheets.size(); i++) {
            xml.append("<Relationship Id=\"rId").append(i)
               .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
               .append("Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        int styleId = sheets.size() + 1;
        xml.append("<Relationship Id=\"rId").append(styleId)
           .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" ")
           .append("Target=\"styles.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
             + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
             + "<fonts count=\"2\">"
             + "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
             + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
             + "</fonts>"
             + "<fills count=\"3\">"
             + "<fill><patternFill patternType=\"none\"/></fill>"
             + "<fill><patternFill patternType=\"gray125\"/></fill>"
             + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E78\"/><bgColor indexed=\"64\"/></patternFill></fill>"
             + "</fills>"
             + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
             + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
             + "<cellXfs count=\"2\">"
             + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
             + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
             + "</cellXfs>"
             + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
             + "</styleSheet>";
    }

    private String worksheet(Sheet sheet) {
        int maxColumns = 1;
        for (Row row : sheet.rows) maxColumns = Math.max(maxColumns, row.values.length);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
           .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
           .append("<sheetViews><sheetView workbookViewId=\"0\">");
        if (!sheet.rows.isEmpty()) {
            xml.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        }
        xml.append("</sheetView></sheetViews><sheetData>");
        for (int r = 0; r < sheet.rows.size(); r++) {
            Row row = sheet.rows.get(r);
            int rowNumber = r + 1;
            xml.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < row.values.length; c++) {
                appendCell(xml, cellRef(c, rowNumber), row.values[c], row.header);
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        if (!sheet.rows.isEmpty()) {
            xml.append("<autoFilter ref=\"A1:").append(cellRef(maxColumns - 1, 1)).append("\"/>");
        }
        return xml.append("</worksheet>").toString();
    }

    private void appendCell(StringBuilder xml, String ref, Object value, boolean header) {
        if (value == null) {
            xml.append("<c r=\"").append(ref).append("\"");
            if (header) xml.append(" s=\"1\"");
            xml.append("/>");
            return;
        }
        if (!header && isNumber(value)) {
            xml.append("<c r=\"").append(ref).append("\"><v>")
               .append(value.toString()).append("</v></c>");
            return;
        }
        if (!header && value instanceof Boolean) {
            xml.append("<c r=\"").append(ref).append("\" t=\"b\"><v>")
               .append(((Boolean) value) ? "1" : "0").append("</v></c>");
            return;
        }
        String text = sanitizeText(String.valueOf(value));
        xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
        if (header) xml.append(" s=\"1\"");
        xml.append("><is><t xml:space=\"preserve\">")
           .append(xmlText(text)).append("</t></is></c>");
    }

    private boolean isNumber(Object value) {
        if (!(value instanceof Number)) return false;
        if (value instanceof BigDecimal) return true;
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }
        return true;
    }

    private String uniqueSheetName(String requested) {
        String base = requested == null ? "" : requested.trim();
        base = base.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (base.isEmpty()) base = "Sheet";
        if (base.length() > 31) base = base.substring(0, 31);
        String name = base;
        int suffix = 2;
        while (sheetNames.contains(name.toLowerCase(Locale.ROOT))) {
            String tail = "(" + suffix++ + ")";
            int keep = Math.min(base.length(), 31 - tail.length());
            name = base.substring(0, keep) + tail;
        }
        sheetNames.add(name.toLowerCase(Locale.ROOT));
        return name;
    }

    private String cellRef(int colIndex, int rowNumber) {
        StringBuilder col = new StringBuilder();
        int n = colIndex;
        do {
            col.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return col.append(rowNumber).toString();
    }

    private String sanitizeText(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) safe.append(c);
        }
        return safe.toString();
    }

    private String xmlText(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    private String xmlAttr(String value) {
        return xmlText(value)
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
