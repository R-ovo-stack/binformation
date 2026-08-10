package com.binformation.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("flow_layout")
public class FlowLayout {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private Long endpointId;
    private Double layoutX;
    private Double layoutY;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }
    public Double getLayoutX() { return layoutX; }
    public void setLayoutX(Double layoutX) { this.layoutX = layoutX; }
    public Double getLayoutY() { return layoutY; }
    public void setLayoutY(Double layoutY) { this.layoutY = layoutY; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
