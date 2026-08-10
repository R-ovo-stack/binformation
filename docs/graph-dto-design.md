# GraphDTO 设计（一键成图）

> 后端负责组装业务图数据；前端（AntV X6 + @antv/layout）负责自动布局与渲染。

---

## 一、API

```
GET /api/assets/{assetId}/graph?includeAuxiliary=false
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `includeAuxiliary` | false | 是否包含 `isPrimary=false` 的辅助流向 |

响应体：`AssetGraphDto`

---

## 二、AssetGraphDto 结构

```json
{
  "assetId": 1,
  "assetName": "订单文件数据",
  "assetCode": "ASSET_ORDER_FILE",
  "dataType": "FILE",
  "groups": [],
  "nodes": [],
  "edges": [],
  "derivations": []
}
```

---

## 三、字段说明

### groups（安全区分组）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 分组 ID，格式 `zone-{zoneEndpointId}` |
| zoneEndpointId | long | 安全区落点 ID |
| label | string | 安全区名称 |

前端用法：按 `groupId` 将 nodes 分区渲染；无安全区归属的节点 `groupId=null`。

### nodes（落点节点）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 节点 ID，格式 `ep-{endpointId}` |
| endpointId | long | 落点主键 |
| type | string | EndpointType |
| label | string | 显示名 |
| groupId | string | 所属安全区分组 ID |
| breadcrumb | string | 归属链，如 `安全区A / 我方系统 / Kafka-A / topic-order` |
| layoutX | double? | 已保存布局 X；null 表示需自动布局 |
| layoutY | double? | 已保存布局 Y |

**节点来源**：当前资产所有流向的源/目标落点 + 步骤中的 host 落点。

### edges（流向边）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 边 ID，格式 `flow-{flowId}` |
| flowId | long | 流向主键 |
| source | string | 源节点 ID |
| target | string | 目标节点 ID |
| purpose | string | INGEST / SHARE / SYNC / FORWARD / AUX |
| primary | boolean | 是否主流向 |
| status | string | 状态 |
| remark | string | 备注 |
| paths | array | 路径与步骤详情 |

#### paths[]

| 字段 | 说明 |
|---|---|
| pathId | 路径 ID |
| name | 路径名称 |
| enabled | 是否启用 |
| sortOrder | 排序 |
| steps | 有序步骤列表 |

#### steps[]

| 字段 | 说明 |
|---|---|
| seq | 顺序号 |
| hostId / hostLabel | 执行节点 |
| executorId / executorName | 程序/脚本 |
| method | 传输方式枚举 |
| remark | 备注 |

### derivations（相关派生/加工）

与当前资产相关：作为**输出资产**，或作为**输入资产**参与。

| 字段 | 说明 |
|---|---|
| derivationId | 派生记录 ID |
| name | 名称 |
| status | 状态 |
| outputAssetId / outputAssetName | 输出资产 |
| inputs[] | 输入资产列表（assetId, assetName, sortOrder） |
| executorId / executorName | 加工程序 |
| hostId / hostLabel | 执行节点 |

前端二期可在画布上用独立「加工节点」展示，首期可用侧边面板。

---

### relations（拓扑关系，非业务流向）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 关系 ID |
| source / target | string | 节点 ID |
| type | string | `CONTAINS` / `BROKER_OF` / `RUNS_ON`（程序→部署主机）/ `VIA_EXECUTOR`（源→程序→目标） |
| label | string | 展示文案 |

成图时自动补齐：
- 主题所属 Kafka 集群，以及同系统下 `role=kafka-broker` 的主机
- 流步骤中的程序/脚本节点，及其 `部署于` 主机关系

---

## 四、ID 约定

| 对象 | ID 格式 | 示例 |
|---|---|---|
| 节点 | `ep-{endpointId}` | `ep-50` |
| 边 | `flow-{flowId}` | `flow-1` |
| 分组 | `zone-{zoneEndpointId}` | `zone-1` |

前端 X6 直接使用这些 string id 作为 cell id。

---

## 五、一键成图流程

```text
1. GET /api/assets/{id}/graph
2. 若 nodes[].layoutX/Y 均为 null → 调用 @antv/layout（dagre）自动排布
3. 若部分节点有 layout → 有坐标的用 saved，其余 auto layout
4. 渲染 X6 Graph
5. 用户微调后 POST 布局（后续 API）写入 flow_layout
```

---

## 六、与模型的映射

| GraphDTO | 数据库 |
|---|---|
| groups | endpoint(type=SECURITY_ZONE) |
| nodes | endpoint |
| edges | flow |
| paths | flow_path |
| steps | flow_step + executor + endpoint(host) |
| derivations | derivation + derivation_input |
| layoutX/Y | flow_layout |

---

## 七、后续 API（待实现）

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/assets/{id}/layout` | 批量保存节点坐标 |
| GET | `/api/assets/{id}/graph/export` | 导出 Mermaid（可选） |
