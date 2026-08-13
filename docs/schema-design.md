# 数据中心台账 · H2 库表字段设计

> 基于已确认的领域模型，数据库：**H2 文件持久化模式**  
> 字符集建议：UTF-8；时间字段统一 `TIMESTAMP`，应用层使用 UTC 或固定时区

---

## 一、枚举值约定

### endpoint.type（落点类型）

| 值 | 说明 |
|---|---|
| `SECURITY_ZONE` | 安全区（顶层） |
| `SYSTEM` | 系统 |
| `KAFKA` | Kafka 集群 |
| `ROCKETMQ` | RocketMQ 集群 |
| `OBJECT_STORAGE` | 对象存储 |
| `HOST` | 主机 |
| `KAFKA_TOPIC` | Kafka 主题 |
| `ROCKETMQ_TOPIC` | RocketMQ 主题 |
| `OBJECT_BUCKET` | 对象桶 |
| `OBJECT_PREFIX` | 对象目录/前缀 |
| `DIRECTORY` | 主机磁盘目录 |
| `HTTP_API` | HTTP 接口 |

### data_asset.data_type

| 值 | 说明 |
|---|---|
| `FILE` | 文件 |
| `KAFKA_MESSAGE` | Kafka 消息 |

### executor.kind

| 值 | 说明 |
|---|---|
| `PROGRAM` | 程序 |
| `SCRIPT` | 脚本 |

### flow.purpose

| 值 | 说明 |
|---|---|
| `INGEST` | 接入 |
| `SHARE` | 共享/供给 |
| `SYNC` | 同步 |
| `FORWARD` | 转发 |
| `AUX` | 辅助流向 |

### flow_step.method

| 值 | 说明 |
|---|---|
| `DIRECT_PUSH` | 直推 |
| `CROSS_ZONE_PUSH` | 跨区隔离推送 |
| `CROSS_ZONE_SEND` | 跨区发送（发送端） |
| `CROSS_ZONE_RECV` | 跨区接收（接收端） |
| `KAFKA_SUBSCRIBE_FORWARD` | 订阅外部 Kafka 转发 |
| `NOTIFY_THEN_PULL` | 通知+拉取 |
| `NOTIFY_THEN_SHARED_READ` | 通知+共享读取 |
| `OTHER` | 其他 |

### 通用 status

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿 |
| `ACTIVE` | 启用 |
| `INACTIVE` | 停用 |
| `DEPRECATED` | 废弃 |

### change_log.entity_type

`DATA_ASSET` | `ENDPOINT` | `EXECUTOR` | `FLOW` | `FLOW_PATH` | `FLOW_STEP` | `DERIVATION` | `DERIVATION_INPUT`

### change_log.action

`CREATE` | `UPDATE` | `DELETE` | `ENABLE` | `DISABLE`

---

## 二、表清单

| 表名 | 说明 |
|---|---|
| `endpoint` | 落点（统一实体，树形所属） |
| `data_asset` | 数据资产 |
| `executor` | 程序/脚本 |
| `flow` | 流向（单源→单目标） |
| `flow_path` | 路径 |
| `flow_step` | 步骤（有序） |
| `derivation` | 派生/加工 |
| `derivation_input` | 派生输入资产 |
| `flow_layout` | 流向图节点布局坐标 |
| `change_log` | 变更单 |
| `change_log_item` | 变更明细 |

---

## 三、表字段明细

### 1. endpoint（落点）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| type | VARCHAR(32) | N | | 落点类型，见枚举 |
| name | VARCHAR(128) | N | | 显示名称 |
| code | VARCHAR(64) | Y | | 可选编码 |
| parent_id | BIGINT | Y | | 父落点 ID；安全区为 NULL |
| zone_id | BIGINT | Y | | 所属安全区 ID（冗余，便于查询） |
| attrs | CLOB | Y | | JSON 扩展属性 |
| status | VARCHAR(16) | N | ACTIVE | 状态 |
| owner | VARCHAR(64) | Y | | 责任人 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | 创建时间 |
| updated_at | TIMESTAMP | N | NOW | 更新时间 |
| created_by | VARCHAR(64) | Y | | 创建人 |
| updated_by | VARCHAR(64) | Y | | 更新人 |

**索引与约束**

- PK：`id`
- UK：`uk_endpoint_parent_type_name (parent_id, type, name)`
- IDX：`idx_endpoint_parent (parent_id)`、`idx_endpoint_zone (zone_id)`、`idx_endpoint_type (type)`
- FK：`parent_id → endpoint(id)`，`zone_id → endpoint(id)`

**说明**

- `parent_id` 为 NULL 时仅允许 `type = SECURITY_ZONE`
- 顶层安全区 `(type, name)` 需在应用层额外保证唯一（H2 唯一索引对 NULL 不生效）
- `zone_id` 写入/更新时由应用层沿父链解析并冗余
- `attrs` 示例：`{"topicName":"order","path":"/data/in","url":"http://..."}`

**层级约束（应用层校验）**

```
SECURITY_ZONE
  └─ SYSTEM
        ├─ KAFKA → KAFKA_TOPIC
        ├─ ROCKETMQ → ROCKETMQ_TOPIC
        ├─ OBJECT_STORAGE → OBJECT_BUCKET → OBJECT_PREFIX
        ├─ HOST → DIRECTORY
        └─ HTTP_API
```

---

### 2. data_asset（数据资产）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| name | VARCHAR(128) | N | | 资产名称 |
| code | VARCHAR(64) | N | | 资产编码，全局唯一 |
| data_type | VARCHAR(32) | N | | FILE / KAFKA_MESSAGE |
| status | VARCHAR(16) | N | ACTIVE | 状态 |
| owner | VARCHAR(64) | Y | | 责任人 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |
| created_by | VARCHAR(64) | Y | | |
| updated_by | VARCHAR(64) | Y | | |

**索引与约束**

- UK：`uk_data_asset_code (code)`
- IDX：`idx_data_asset_type (data_type)`、`idx_data_asset_status (status)`

---

### 3. executor（程序/脚本）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| name | VARCHAR(128) | N | | 名称 |
| code | VARCHAR(64) | N | | 编码，全局唯一 |
| kind | VARCHAR(16) | N | | PROGRAM / SCRIPT |
| default_host_id | BIGINT | Y | | 默认部署主机（endpoint.id） |
| status | VARCHAR(16) | N | ACTIVE | 状态 |
| owner | VARCHAR(64) | Y | | 责任人 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |
| created_by | VARCHAR(64) | Y | | |
| updated_by | VARCHAR(64) | Y | | |

**索引与约束**

- UK：`uk_executor_code (code)`
- FK：`default_host_id → endpoint(id)`
- IDX：`idx_executor_kind (kind)`

---

### 4. flow（流向）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| asset_id | BIGINT | N | | 所属数据资产 |
| source_endpoint_id | BIGINT | N | | 源落点 |
| target_endpoint_id | BIGINT | N | | 目标落点 |
| purpose | VARCHAR(16) | N | | 用途枚举 |
| is_primary | BOOLEAN | N | TRUE | 是否主流向 |
| status | VARCHAR(16) | N | ACTIVE | 状态 |
| owner | VARCHAR(64) | Y | | 责任人 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |
| created_by | VARCHAR(64) | Y | | |
| updated_by | VARCHAR(64) | Y | | |

**索引与约束**

- FK：`asset_id → data_asset(id)`，`source_endpoint_id → endpoint(id)`，`target_endpoint_id → endpoint(id)`
- IDX：`idx_flow_asset (asset_id)`、`idx_flow_source (source_endpoint_id)`、`idx_flow_target (target_endpoint_id)`
- 业务规则：单源单目标；源≠目标（应用层校验）

**说明**

- 资产与落点的关联仅通过本表体现
- 同一资产下允许多条流向；同一对源/目标可有多条（不同 purpose 或路径）

---

### 5. flow_path（路径）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| flow_id | BIGINT | N | | 所属流向 |
| name | VARCHAR(128) | N | | 路径名称 |
| enabled | BOOLEAN | N | TRUE | 是否启用 |
| sort_order | INT | N | 0 | 路径排序 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |

**索引与约束**

- FK：`flow_id → flow(id) ON DELETE CASCADE`
- IDX：`idx_flow_path_flow (flow_id)`

---

### 6. flow_step（步骤）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| path_id | BIGINT | N | | 所属路径 |
| seq | INT | N | | 顺序号，从 1 开始 |
| host_id | BIGINT | Y | | 执行节点（主机落点） |
| executor_id | BIGINT | N | | 程序/脚本 |
| method | VARCHAR(32) | N | | 传输/执行方式 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |

**索引与约束**

- FK：`path_id → flow_path(id) ON DELETE CASCADE`，`host_id → endpoint(id)`，`executor_id → executor(id)`
- UK：`uk_flow_step_path_seq (path_id, seq)`
- IDX：`idx_flow_step_executor (executor_id)`

---

### 7. derivation（派生/加工）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| name | VARCHAR(128) | N | | 加工名称 |
| output_asset_id | BIGINT | N | | 输出资产（仅 1 个） |
| executor_id | BIGINT | N | | 加工程序/脚本 |
| host_id | BIGINT | Y | | 执行节点 |
| status | VARCHAR(16) | N | ACTIVE | 状态 |
| owner | VARCHAR(64) | Y | | 责任人 |
| remark | VARCHAR(500) | Y | | 备注 |
| created_at | TIMESTAMP | N | NOW | |
| updated_at | TIMESTAMP | N | NOW | |
| created_by | VARCHAR(64) | Y | | |
| updated_by | VARCHAR(64) | Y | | |

**索引与约束**

- FK：`output_asset_id → data_asset(id)`，`executor_id → executor(id)`，`host_id → endpoint(id)`
- IDX：`idx_derivation_output (output_asset_id)`

---

### 8. derivation_input（派生输入）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| derivation_id | BIGINT | N | | 所属派生记录 |
| input_asset_id | BIGINT | N | | 输入资产 |
| sort_order | INT | N | 0 | 输入顺序（可选） |
| created_at | TIMESTAMP | N | NOW | |

**索引与约束**

- FK：`derivation_id → derivation(id) ON DELETE CASCADE`，`input_asset_id → data_asset(id)`
- UK：`uk_derivation_input (derivation_id, input_asset_id)`

---

### 9. flow_layout（流向图布局）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| asset_id | BIGINT | N | | 资产视角 |
| endpoint_id | BIGINT | N | | 落点节点 |
| layout_x | DOUBLE | N | 0 | X 坐标 |
| layout_y | DOUBLE | N | 0 | Y 坐标 |
| updated_at | TIMESTAMP | N | NOW | |

**索引与约束**

- FK：`asset_id → data_asset(id) ON DELETE CASCADE`，`endpoint_id → endpoint(id)`
- UK：`uk_flow_layout (asset_id, endpoint_id)`

**说明**

- 仅存视图坐标，不参与业务逻辑
- 一键成图默认走自动布局；用户微调后写入本表

---

### 10. change_log（变更单）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| entity_type | VARCHAR(32) | N | | 变更对象类型 |
| entity_id | BIGINT | N | | 变更对象 ID |
| action | VARCHAR(16) | N | | 动作 |
| summary | VARCHAR(500) | N | | 人话摘要 |
| operator | VARCHAR(64) | N | | 操作人 |
| operated_at | TIMESTAMP | N | NOW | 操作时间 |
| related_asset_id | BIGINT | Y | | 关联资产（便于汇总） |
| remark | VARCHAR(500) | Y | | 备注 |

**索引与约束**

- IDX：`idx_change_log_asset_time (related_asset_id, operated_at)`、`idx_change_log_entity (entity_type, entity_id)`、`idx_change_log_time (operated_at)`

**说明**

- 只追加，不更新、不删除（或仅管理员归档）

---

### 11. change_log_item（变更明细）

| 字段 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT | N | AUTO | 主键 |
| change_log_id | BIGINT | N | | 变更单 ID |
| field_name | VARCHAR(64) | N | | 字段名 |
| old_value | CLOB | Y | | 旧值 |
| new_value | CLOB | Y | | 新值 |

**索引与约束**

- FK：`change_log_id → change_log(id) ON DELETE CASCADE`
- IDX：`idx_change_log_item_log (change_log_id)`

---

## 四、ER 关系简图

```
endpoint ──(parent)──▶ endpoint
endpoint ◀── source/target/host ── flow ──▶ data_asset
flow ──▶ flow_path ──▶ flow_step ──▶ executor
derivation ──▶ data_asset (output)
derivation_input ──▶ data_asset (input)
flow_layout ──▶ data_asset + endpoint
change_log ──▶ change_log_item
```

---

## 五、attrs 字段建议结构（endpoint）

| type | attrs 示例字段 |
|---|---|
| KAFKA_TOPIC | `topicName` |
| ROCKETMQ_TOPIC | `topicName` |
| OBJECT_BUCKET | `bucketName` |
| OBJECT_PREFIX | `prefix` |
| DIRECTORY | `dirPath` |
| HTTP_API | `url`, `method` |
| HOST | `ip`, `hostname` |

---

## 六、H2 配置参考

```properties
spring.datasource.url=jdbc:h2:file:./data/datacenter-ledger;MODE=MySQL;DATABASE_TO_LOWER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.flyway.locations=classpath:db/migration
```

`MODE=MySQL` 可选，便于 SQL 方言与后期迁移；也可使用 H2 原生语法。

---

## 七、后续可扩展（本期不做）

- `flow_edge_layout`：边的折线/控制点坐标
- 软删除字段 `deleted_at`
- 版本号字段用于乐观锁
