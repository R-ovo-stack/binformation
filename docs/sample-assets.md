# 样例数据资产一览

覆盖已定模型中的多类场景，便于一键成图演示。

| ID | 名称 | 编码 | 类型 | 场景 |
|---:|---|---|---|---|
| 1 | 订单文件数据 | `ASSET_ORDER_FILE` | FILE | 接入(通知+拉取) + 跨区同步 + 供给(通知+共享读取) |
| 100 | 外部系统A-主题C消息 | `ASSET_EXT_A_TOPIC_C` | KAFKA_MESSAGE | kdc-forward@idc310 订阅转发；内部Kafka节点 idc301/302/303 |
| 200 | 跨区行情Tick消息 | `ASSET_QUOTE_TICK` | KAFKA_MESSAGE | 生产→灾备跨区同步 |
| 201 | 外部客户增量文件 | `ASSET_CUST_FILE_INCR` | FILE | RocketMQ+HTTP 接入，Kafka+对象目录供给（含辅助流向） |
| 202 | 原始持仓快照文件 | `ASSET_RAW_POSITION` | FILE | 原始文件接入（派生输入） |
| 203 | 原始客户主数据文件 | `ASSET_RAW_CUSTOMER` | FILE | 原始文件接入（派生输入） |
| 204 | 规范持仓数据 | `ASSET_NORM_POSITION` | FILE | 多源派生/加工 + 对外共享 |
| 205 | 应用日志直推 | `ASSET_APP_LOG_DIRECT` | FILE | 目录直推 |
| 300 | GZ故障文件拉取 | `ASSET_FAULT_GZ_PULL` | FILE | idc301 脚本从 idc302:/data/origin/gz 下载到 idc301:/data/sftp/gz |
| 400 | 电网遥信遥测台账链路 | `ASSET_GRID_YX_YC_LEDGER` | FILE | 二区A→二区B Kafka(ABC@cloud)→跨区→三区B Kafka(ABC@idc)→idc305拼接D→SFTP idc306→主题F |

## 建议体验路径

1. 打开资产列表  
2. 分别点开上述资产「一键成图」  
3. 对 201 打开「含辅助」开关，查看通知通道辅助流向  
4. 对 204 查看底部「相关派生/加工」及流向图  

手机隧道示例（若仍有效）：

- 列表：`/ `
- 成图：`/assets/{id}/graph`
