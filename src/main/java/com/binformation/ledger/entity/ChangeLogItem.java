package com.binformation.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("change_log_item")
public class ChangeLogItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long changeLogId;
    private String fieldName;
    private String oldValue;
    private String newValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChangeLogId() { return changeLogId; }
    public void setChangeLogId(Long changeLogId) { this.changeLogId = changeLogId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
}
