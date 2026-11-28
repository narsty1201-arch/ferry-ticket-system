package com.ferry.util;

import java.util.Collections;
import java.util.List;

/** 通用分页结果：items 为当前页数据，total 为满足筛选条件的总记录数。 */
public class Page<T> {
    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int total;
    private final int totalPages;

    public Page(List<T> items, int page, int pageSize, int total) {
        this.items = items == null ? Collections.<T>emptyList() : items;
        this.pageSize = Math.max(1, pageSize);
        this.total = Math.max(0, total);
        this.totalPages = Math.max(1, (this.total + this.pageSize - 1) / this.pageSize);
        this.page = Math.min(Math.max(1, page), this.totalPages);
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public int getTotal() { return total; }
    public int getTotalPages() { return totalPages; }
    public boolean isHasPrev() { return page > 1; }
    public boolean isHasNext() { return page < totalPages; }

    public static int parse(String value, int defaultValue) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int clamp(int page, int total, int pageSize) {
        int size = Math.max(1, pageSize);
        int pages = Math.max(1, (Math.max(0, total) + size - 1) / size);
        return Math.min(Math.max(1, page), pages);
    }

    public static <T> Page<T> empty(int page, int pageSize) {
        return new Page<T>(Collections.<T>emptyList(), page, pageSize, 0);
    }
}
