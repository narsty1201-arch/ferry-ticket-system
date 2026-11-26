package com.ferry.entity;

/** 船舶实体：对应数据库 ship 表 */
public class Ship {
    private int id;
    private String shipName;
    private int totalSeats;
    private String shipType;    // 普通船 / 高速船 / 豪华船
    private String description;
    private String status;      // active / disabled
    private boolean deleted;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public String getShipType() { return shipType; }
    public void setShipType(String shipType) { this.shipType = shipType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
