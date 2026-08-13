package com.binformation.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.binformation.ledger.entity.ChangeLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeLogMapper extends BaseMapper<ChangeLog> {
}
