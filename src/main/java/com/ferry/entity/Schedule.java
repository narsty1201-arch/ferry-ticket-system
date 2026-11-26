package com.ferry.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** 班次实体：对应数据库 schedule 表，附带航线/船舶名称便于列表显示 */
public class Schedule {
    private int id;
    private int routeId;
    private int shipId;
    private Timestamp departTime;
    private Timestamp arriveTime;
    private BigDecimal price;
    private int totalSeats;
    private int soldSeats;
    private String status;      // 待发布 / 售票中 / 已发船 / 已完成 / 已取消
    private boolean deleted;
    private Timestamp createTime;
    // 联表查询带出的显示字段
    private String routeName;
    private String startPort;
    private String endPort;
    private String shipName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getRouteId() { return routeId; }
    public void setRouteId(int routeId) { this.routeId = routeId; }
    public int getShipId() { return shipId; }
    public void setShipId(int shipId) { this.shipId = shipId; }
    public Timestamp getDepartTime() { return departTime; }
    public void setDepartTime(Timestamp departTime) { this.departTime = departTime; }
    public Timestamp getArriveTime() { return arriveTime; }
    public void setArriveTime(Timestamp arriveTime) { this.arriveTime = arriveTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public int getSoldSeats() { return soldSeats; }
    public void setSoldSeats(int soldSeats) { this.soldSeats = soldSeats; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Timestamp getCreateTime() { return createTime; }
    public void setCreateTime(Timestamp createTime) { this.createTime = createTime; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getStartPort() { return startPort; }
    public void setStartPort(String startPort) { this.startPort = startPort; }
    public String getEndPort() { return endPort; }
    public void setEndPort(String endPort) { this.endPort = endPort; }
    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }

    /** 剩余座位 = 总数 - 已售 */
    public int getRemainSeats() { return totalSeats - soldSeats; }
}
