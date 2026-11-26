package com.ferry.entity;

import java.util.Date;

public class User {
    private int id;
    private String username;
    private String password;
    private String role;       // admin / seller / passenger
    private String realName;
    private String phone;
    private String idCard;
    private String status;     // normal / disabled
    private int failCount;
    private Date lockUntil;
    private Date createTime;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public Date getLockUntil() { return lockUntil; }
    public void setLockUntil(Date lockUntil) { this.lockUntil = lockUntil; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
