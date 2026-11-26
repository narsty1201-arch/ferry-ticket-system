package com.ferry.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** 船票实体：对应数据库 ticket 表 */
public class Ticket {
    private int id;
    private int orderId;
    private int scheduleId;
    private int seatId;
    private String passengerName;
    private String idCard;
    private String passengerPhone;
    private BigDecimal price;
    private String status;          // 待支付/已支付/已检票/已退票/已取消
    private String qrCode;
    private Timestamp createTime;
    // 联表显示
    private String seatNo;
    private String routeName;
    private String shipName;
    private Timestamp departTime;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }
    public int getSeatId() { return seatId; }
    public void setSeatId(int seatId) { this.seatId = seatId; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public Timestamp getCreateTime() { return createTime; }
    public void setCreateTime(Timestamp createTime) { this.createTime = createTime; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }
    public Timestamp getDepartTime() { return departTime; }
    public void setDepartTime(Timestamp departTime) { this.departTime = departTime; }
}
