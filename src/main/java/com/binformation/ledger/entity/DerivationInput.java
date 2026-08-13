package com.binformation.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("derivation_input")
public class DerivationInput {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long derivationId;
    private Long inputAssetId;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDerivationId() { return derivationId; }
    public void setDerivationId(Long derivationId) { this.derivationId = derivationId; }
    public Long getInputAssetId() { return inputAssetId; }
    public void setInputAssetId(Long inputAssetId) { this.inputAssetId = inputAssetId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
