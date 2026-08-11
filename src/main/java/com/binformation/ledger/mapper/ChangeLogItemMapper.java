package com.binformation.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.binformation.ledger.entity.ChangeLogItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeLogItemMapper extends BaseMapper<ChangeLogItem> {
}
