package com.binformation.ledger.service;

import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataAssetService {

    private final DataAssetMapper dataAssetMapper;

    public DataAssetService(DataAssetMapper dataAssetMapper) {
        this.dataAssetMapper = dataAssetMapper;
    }

    public List<DataAsset> listAll() {
        return dataAssetMapper.selectList(null);
    }

    public DataAsset getById(Long id) {
        DataAsset asset = dataAssetMapper.selectById(id);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + id);
        }
        return asset;
    }
}
