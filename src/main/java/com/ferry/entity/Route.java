package com.ferry.entity;

/** 航线实体：对应数据库 route 表 */
public class Route {
    private int id;
    private String routeName;
    private String startPort;
    private String endPort;
    private int durationMin;
    private String description;
    private String status;      // active / closed
    private boolean deleted;    // 逻辑删除标记 is_deleted

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    public String getStartPort() { return startPort; }
    public void setStartPort(String startPort) { this.startPort = startPort; }
    public String getEndPort() { return endPort; }
    public void setEndPort(String endPort) { this.endPort = endPort; }
    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
