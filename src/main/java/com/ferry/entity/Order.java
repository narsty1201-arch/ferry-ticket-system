package com.ferry.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** 订单实体：对应数据库 [order] 表 */
public class Order {
    private int id;
    private String orderNo;
    private int scheduleId;
    private Integer passengerId;
    private Integer sellerId;
    private BigDecimal totalAmount;
    private String status;          // 待支付/已支付/已检票/已完成/已取消/已退票
    private Timestamp createTime;
    private Timestamp payTime;
    private Timestamp cancelTime;
    private BigDecimal refundAmount;
    private String refundReason;
    private Timestamp refundTime;
    private Timestamp refundEta;    // 预计退款到账时间（退票后3个工作日）
    // 联表显示用
    private String routeName;
    private String shipName;
    private String startPort;
    private String endPort;
    private Timestamp departTime;
    private String seatNo;
    private String passengerName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }
    public Integer getPassengerId() { return passengerId; }
    public void setPassengerId(Integer passengerId) { this.passengerId = passengerId; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreateTime() { return createTime; }
    public void setCreateTime(Timestamp createTime) { this.createTime = createTime; }
    public Timestamp getPayTime() { return payTime; }
    public void setPayTime(Timestamp payTime) { this.payTime = payTime; }
    public Timestamp getCancelTime() { return cancelTime; }
    public void setCancelTime(Timestamp cancelTime) { this.cancelTime = cancelTime; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public Timestamp getRefundTime() { return refundTime; }
    public void setRefundTime(Timestamp refundTime) { this.refundTime = refundTime; }
    public Timestamp getRefundEta() { return refundEta; }
    public void setRefundEta(Timestamp refundEta) { this.refundEta = refundEta; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }
    public String getStartPort() { return startPort; }
    public void setStartPort(String startPort) { this.startPort = startPort; }
    public String getEndPort() { return endPort; }
    public void setEndPort(String endPort) { this.endPort = endPort; }
    public Timestamp getDepartTime() { return departTime; }
    public void setDepartTime(Timestamp departTime) { this.departTime = departTime; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
}
