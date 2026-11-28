package com.ferry.entity;

/** 选座图单元格：某班次一个座位的编号与当前状态 */
public class SeatCell {
    private final String seatNo;
    private final String status;

    public SeatCell(String seatNo, String status) {
        this.seatNo = seatNo;
        this.status = status;
    }

    public String getSeatNo() { return seatNo; }
    public String getStatus() { return status; }

    public boolean isAvailable() { return "可售".equals(status); }

    /** 排号，如 A5 → A */
    public String getRow() { return seatNo.substring(0, 1); }

    /** 排内座号，如 A5 → 5 */
    public String getNumber() {
        return seatNo.length() > 1 ? seatNo.substring(1) : seatNo;
    }
}
