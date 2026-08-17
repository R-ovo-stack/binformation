# 程序实现逻辑

> 本文说明数据中心台账**如何工作**：请求如何穿过前后端、一键成图如何组装、可视化编辑如何复用布局、影响分析 / 搜索 / 供需如何计算。  
> 表字段与枚举见 [schema-design.md](./schema-design.md)；GraphDTO 字段契约见 [graph-dto-design.md](./graph-dto-design.md)。

---

## 1. 总览

```text
浏览器 (Vue 3 + X6)
    │  /api/*  （Vite 开发代理 → :8080）
    ▼
Spring Boot Controllers
    ▼
Services（领域逻辑 / 成图 / 影响 / 搜索 / 供需 / 导出）
    ▼
MyBatis-Plus Mappers ←→ H2 文件库（Flyway 迁移）
```

| 层次 | 职责 |
|------|------|
| Controller | HTTP、校验、状态码 |
| Service | 业务规则、图组装、影响依赖扫描 |
| Entity/Mapper | 表映射 |
| DTO | 对外 JSON 契约（不直接暴露 Entity） |
| Frontend utils | 布局算法、边展开、可视化板状态 → AssetGraph |
| Frontend canvas | AntV X6 渲染 / 交互 / PNG |

**布局在前端，业务拓扑在后端。** 后端可持久化用户拖拽后的落点坐标（`flow_layout`），但不跑 Dagre/自研分层算法。

---

## 2. 领域模型与存储

### 2.1 核心实体关系

```text
endpoint（树：parent_id / zone_id / type）
    ↑ source_endpoint_id / target_endpoint_id
data_asset ──► flow ──► flow_path ──► flow_step
                              │              ├── executor_id → executor
                              │              └── host_id → endpoint(HOST)
                              │
derivation（output_asset_id）──► derivation_input（input_asset_id）
    └── executor_id / host_id

flow_layout（asset_id + endpoint_id → layout_x/y）
change_log / change_log_item（审计）
```

### 2.2 落点层级（强制）

合法父子关系由 `EndpointHierarchy` + `EndpointService` 校验，摘要：

```text
SECURITY_ZONE
  └── SYSTEM
        ├── KAFKA → KAFKA_TOPIC
        ├── ROCKETMQ → ROCKETMQ_TOPIC
        ├── OBJECT_STORAGE → OBJECT_BUCKET → OBJECT_PREFIX
        ├── HOST → DIRECTORY
        └── HTTP_API
```

面包屑、所属安全区解析：`EndpointSupport`（沿 `parent_id` 上溯 / 读 `zone_id`）。

### 2.3 流向语义

| 概念 | 含义 |
|------|------|
| Flow | 某资产下的一条「源落点 → 目标落点」；带 `purpose`、`isPrimary`、`status` |
| FlowPath | 同一流向的备选执行路径（可多条） |
| FlowStep | 路径上有序步骤：执行哪个 Executor、可选部署 Host、`method` |
| primary | 默认进入一键成图；非 primary / AUX 需「含辅助」或可视化编辑中维护 |

更新 Flow 时，服务层通常**删除旧 paths/steps 再插入新树**（单事务），保证嵌套一致性。

### 2.4 派生

`Derivation`：多个输入资产 → 一个输出资产，由某 Executor（可选 Host）加工。  
一键成图在 `includeUpstream=true` 时可把输入资产的主流向桥接到当前资产。

---

## 3. 后端请求处理惯例

1. Controller 接收参数 / `@Valid` Body。  
2. Service 查库；不存在 → `ResourceNotFoundException`（404）；非法 → `BadRequestException`（400）。  
3. `GlobalExceptionHandler` 转为 RFC 7807 `ProblemDetail`（前端优先读 `detail`）。  
4. 写操作常调用 `ChangeLogService.record(...)`。  
5. 创建 HTTP 201；删除 204。

配置要点（`application.yml`）：

- 端口 `8080`
- H2：`jdbc:h2:file:./data/datacenter-ledger;MODE=MySQL;DATABASE_TO_LOWER=TRUE`
- Flyway：`classpath:db/migration`（`V1` schema，`V2+` 样例）

---

## 4. 一键成图（单资产技术图）

### 4.1 入口

```text
GET /api/assets/{id}/graph?includeAuxiliary=false&includeUpstream=false
  → DataAssetController
  → AssetGraphService.buildGraph(...)
  → AssetGraphDto
  → 前端 AssetGraphView → FlowGraphCanvas
```

### 4.2 后端组装流水线（`AssetGraphService`）

```text
1. 加载 DataAsset；判断 hasUpstream（是否作为某派生的输出）
2. 加载本资产 Flows：
   - 默认仅 isPrimary=true
   - includeAuxiliary=true → 全部 Flows
3. includeUpstream=true：
   - 找以本资产为输出的 Derivation
   - 加载各输入资产的主流向，标记 upstream
4. 从流向两端 + step.host 收集 Endpoint ID，加载 Endpoint / Executor
5. 计算 relations（非持久化）：
   - CONTAINS：Kafka→Topic、Host→Directory 等
   - BROKER_OF：集群与 broker 主机（启发式）
   - RUNS_ON：Executor→Host
   - VIA_EXECUTOR：沿路径表达「经程序」
6. 读取 flow_layout（本资产已保存坐标）写入节点 layoutX/Y
7. 组装 edges（每条 Flow 一条逻辑边，内嵌 paths/steps DTO）
8. 可选：派生桥接边（上游末跳 → 派生程序 → 本资产接入点）
9. 按安全区生成 groups；nodes = 落点 + 程序
10. 返回 AssetGraphDto
```

节点 ID 约定：

- 落点：`ep-{endpointId}`
- 程序：`exec-{executorId}`

### 4.3 前端渲染流水线（`FlowGraphCanvas`）

```text
props.graph (AssetGraphDto)
  → filterGraphForMode(mode)           # compact | full
  → 可选 applyCompressExecutorHost()   # 折叠部署主机
  → layoutGraphSmart()                 # 有保存坐标则用；否则分层+泳道+嵌套
  → X6 addNode（先容器后叶子，再 parent.addChild）
  → expandDisplayEdges()               # 源→程序→目标 折线展示
  → visibleRelations() 画 RUNS_ON 等
  → zoomToFit
```

**compact**：只保留 primary（及 upstream）相关节点/边，减少噪声。  
**full**：保留过滤前的全集。  

**压缩部署**：去掉 RUNS_ON 边与纯部署主机节点，主机名写入程序框文案。

### 4.4 边展开（`expandDisplayEdges`）

逻辑边 `sourceEndpoint → targetEndpoint` 在画布上拆成：

```text
ep-源 → exec-1 → … → exec-n → ep-目标
```

点击任一段仍回到原 `GraphEdge`（`flowEdge`），供 `EdgeDetailPanel` 展示路径/步骤。  
展开时若某 ID 不在当前 `nodes` 集合中，该 hop 会被跳过，避免悬空边。

### 4.5 布局算法要点（`graphLayout.ts`）

1. 若存在任一节点的 `layoutX/Y`，优先采用保存坐标，再跑集群嵌套。  
2. 否则：按展示边做 DAG 分层（左→右），按安全区泳道（上→下），层内重心减少交叉。  
3. `applyClusterContainment`：Kafka/RocketMQ 等容器框套主题；Broker 芯片。  
4. `resolveOverlaps`：根级卡片互推，避免重叠。  
5. 嵌套子节点禁止单独拖动；只拖容器。

保存布局：`collectEndpointLayouts` → `PUT /api/assets/{id}/layout`（仅落点，按节点中心点写回）。

---

## 5. 可视化流向编辑

### 5.1 入口与状态

```text
FlowVisualEditView
  ├── 加载：asset · flows · flowDetails · endpoints(optionsOnly) · executors
  ├── canvasEndpointIds：画布上出现的落点集合（默认=流向两端）
  ├── boardEdges：每条 Flow 一条板边（含 draft）
  ├── editing / draft：右侧面板编辑的 FlowDetail
  └── FlowBoardCanvas：可连线、选边、双击落点快捷编辑
```

本地草稿 / 撤销：`visualEditHistory` 等工具模块（快照 canvas + 面板状态）。

### 5.2 板 → 图（`buildBoardAssetGraph`）

将编辑态转为与一键成图相同的 `AssetGraph`，以便复用 `layoutGraph` / 样式：

1. 为 `canvasEndpointIds` 建落点节点与安全区分组。  
2. 主题类落点补齐父集群并加 `CONTAINS`。  
3. 每条 board edge 用详情里的 paths 生成 GraphPath；步骤中的 Executor / Host 入图；**仅当 Host 节点确实创建成功才加 RUNS_ON**。  
4. 输出 nodes / edges / relations。

### 5.3 布局与画边一致性（重要）

历史缺陷：板用 **compact** 布局会丢掉非 primary（如 AUX）落点，但仍对全量 edges 调用 `addEdge`，X6 抛出：

```text
Edge's source node with id "ep-…" not exists
```

并可能连带出现空引用类控制台错误。

**现行规则：`FlowBoardCanvas` 固定 `layoutGraph(assetGraph, 'full')`，且 `addEdge` 前检查两端节点存在。**

只读一键成图则布局与边都基于同一份 `filterGraphForMode` 结果，不会出现该分裂。

### 5.4 保存

面板保存 → `FlowService` 创建或整单更新 → 刷新 summaries / details → 画布 `render`。

---

## 6. 资产全景与技术全景

### 6.1 资产全景（`PanoramaGraphService`）

```text
GET /api/graph/panorama
```

- 节点：DataAsset  
- 边：`DERIVE`（派生输入→输出）；可选 `ENDPOINT_LINK`（两资产主流向共享落点）  
- 前端：`PanoramaGraphCanvas` + `layoutPanoramaGraph`（拓扑分层）

### 6.2 技术全景

```text
GET /api/graph/panorama/technical?assetIds=&includeAuxiliary=&includeDerivationBridges=
```

复用 `AssetGraphService` 在多资产集合上合并落点级图（不写单资产 layout）。  
前端仍用 `FlowGraphCanvas` + compact/full。

---

## 7. 供需查询（Lineage）

```text
GET /api/lineage/systems
GET /api/lineage/systems/{systemId}/assets
GET /api/lineage/assets/{assetId}/downstream-systems
```

逻辑要点（`SystemAssetLineageService`）：

1. 「系统」= `endpoint.type == SYSTEM`。  
2. 落点归属系统：从落点沿 `parent_id` 上溯直到 SYSTEM。  
3. **系统消费资产**：存在流向，其**目标落点**归属该系统 → 该流向所属资产算作被消费（默认可排除 AUX）。  
4. **资产下游系统**：本资产流出的目标落点归属哪些 SYSTEM；无法归属则进「未归属」桶。

前端：`SystemAssetLineageView` 两种查询模式切换。

---

## 8. 变更影响分析

```text
GET /api/impact?entityType=ENDPOINT|ASSET|FLOW|EXECUTOR|DERIVATION&entityId=&action=DELETE|UPDATE
```

`ImpactAnalysisService` 扫描引用关系，产出：

- `blockers`（BLOCKER）→ `canProceed=false`  
- `warnings` / info  
- 条目带可导航引用（前端点进编辑页）

| 实体 | DELETE 时典型阻断 |
|------|-------------------|
| Endpoint | 子落点、作为流向源/目标、作为 step host、executor 默认主机、派生主机 |
| Asset | 名下流向、作为派生输出、作为派生输入 |
| Executor | 被 flow_step / derivation 引用 |
| Flow | 一般可删（级联 path/step），以警告提示为主 |
| Derivation | 提示输入行删除、输出资产失联 |

前端挂载点：

- `/impact` 独立页  
- 删除确认：`impactConfirm` + `ImpactDeleteBody`  
- 编辑页侧栏：`ImpactAnalysisPanel`（UPDATE）

---

## 9. 全局搜索

```text
GET /api/search?q=&limit=
```

`SearchService`：内存扫描资产 / 落点 / 流向 / 程序 / 派生，按名称、编码、类型、备注、面包屑等做大小写不敏感子串匹配，并支持 ID。  
结果分组返回；前端 `GlobalSearch`（快捷键）与 `/search` 页用 `searchNav` 跳转实体。

---

## 10. 导出与落点导入

### 导出

```text
GET /api/export/full?format=json|zip
```

- `json`：嵌套结构（落点树 + 资产流向等）  
- `zip`：多 CSV（`LedgerCsvExportService`）

### 落点 CSV

- 模板下载 / 上传导入：`EndpointCsvImportService` + `CsvImportSupport`  
- 导入时仍走层级校验。

---

## 11. 前端路由与模块对应

| 路由 | 视图 | 依赖能力 |
|------|------|----------|
| `/` | AssetListView | 资产列表、导出、CSV 导入 |
| `/assets/:id/graph` | AssetGraphView | 一键成图 |
| `/assets/:id/flows/visual` | FlowVisualEditView | 可视化编辑 |
| `/panorama` | PanoramaGraphView | 全景 / 技术全景 |
| `/lineage` | SystemAssetLineageView | 供需 |
| `/impact` | ImpactAnalysisView | 影响分析 |
| `/search` | SearchView | 搜索 |
| `/endpoints*` `/executors*` | 列表/编辑 | CRUD |
| `/docs/flow-editing` | FlowEditingGuideView | Markdown 指南 |

API 客户端：`frontend/src/api/*.ts`（`http.ts` 统一超时与错误文案）。

---

## 12. 端到端时序（一键成图）

```text
用户打开 /assets/1/graph
  → getAssetGraph(1)
  → Vite 代理 /api/assets/1/graph
  → AssetGraphService.buildGraph
  → JSON AssetGraphDto
  → AssetGraphView.graph = data
  → FlowGraphCanvas.render
       layoutGraph → X6 nodes/edges → zoomToFit
  → 用户点击边 → EdgeDetailPanel 显示 paths/steps
  → 「保存布局」→ PUT layout → 下次成图带坐标
```

---

## 13. 扩展指南（本地改代码时）

| 目标 | 建议落点 |
|------|----------|
| 新落点类型 | `EndpointHierarchy` + schema 枚举文档 + 前端类型标签 |
| 新流向 purpose/method | DB/校验枚举 + `graphLayout` 文案 + schema 文档 |
| 成图多显示一种关系 | `AssetGraphService` 计算 relations + 前端 `visibleRelations` / 样式 |
| 新影响规则 | `ImpactAnalysisService` + 测试 + 前端展示字段若有变 |
| 新页面 | `router/index.ts` + `AppNav`（如需）+ `api/` 模块 |
| 新表 | **只追加** Flyway `V{n+1}__...sql`，禁止改历史迁移 |

改契约时同步：

1. 后端 DTO / API  
2. 前端 `types` + `api`  
3. `docs/graph-dto-design.md` 或 `schema-design.md`  
4. 相关 `*ControllerTest`

---

## 14. 相关代码索引（速查）

| 主题 | 后端 | 前端 |
|------|------|------|
| 一键成图 | `AssetGraphService` | `AssetGraphView` · `FlowGraphCanvas` · `graphLayout.ts` |
| 可视化编辑 | `FlowService` | `FlowVisualEditView` · `FlowBoardCanvas` · `flowBoardGraph.ts` |
| 全景 | `PanoramaGraphService` | `PanoramaGraphView` · `PanoramaGraphCanvas` |
| 供需 | `SystemAssetLineageService` | `SystemAssetLineageView` |
| 影响 | `ImpactAnalysisService` | `ImpactAnalysisPanel` · `impactConfirm.ts` |
| 搜索 | `SearchService` | `GlobalSearch` · `SearchView` |
| 落点层级 | `EndpointHierarchy` · `EndpointService` | `EndpointTreeSelect` · `endpointSelectTree.ts` |
| 布局持久化 | `FlowLayoutService` | `api/layout.ts` · `collectEndpointLayouts` |

---

*文档版本与仓库 `main` 功能集对齐：资产台账 · 成图 · 可视化流向 · 全景 · 供需 · 影响分析 · 全局搜索 · 导出/导入。*
