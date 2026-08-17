# 数据中心台账（datacenter-ledger）

数据资产流向台账与可视化：落点树、资产、流向（多路径/多步骤）、程序/脚本、派生加工；支持一键成图、资产全景、供需查询、变更影响分析、全局搜索与全量导出。

仓库：`binformation` · 应用名：`datacenter-ledger`

---

## 目录

1. [技术栈](#技术栈)
2. [本地快速启动](#本地快速启动)
3. [功能地图](#功能地图)
4. [仓库结构](#仓库结构)
5. [核心 API 索引](#核心-api-索引)
6. [领域概念速查](#领域概念速查)
7. [开发约定（Rules）](#开发约定rules)
8. [文档索引](#文档索引)
9. [测试与构建](#测试与构建)
10. [排障](#排障)
11. [从 Cloud Agent 转到本地](#从-cloud-agent-转到本地)

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21 · Spring Boot 3.3 · H2（文件库，MySQL 兼容模式）· Flyway · MyBatis-Plus · springdoc OpenAPI |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · AntV X6 · `@fontsource/space-grotesk` + `@fontsource/source-sans-3` |
| 数据 | 本地文件 `./data/datacenter-ledger`（Flyway 迁移 + 样例数据自动装载） |

---

## 本地快速启动

**必须同时启动后端和前端。** 只开前端时，首页会一直转圈（`/api/assets` 无后端）。

### 1. 后端

```bash
# 仓库根目录
mvn spring-boot:run
```

就绪标志：日志出现 `Started LedgerApplication`，且：

```bash
curl http://localhost:8080/api/assets
```

| 服务 | 地址 |
|------|------|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| H2 Console | http://localhost:8080/h2-console （JDBC URL 见下方） |

H2 Console JDBC URL：

```text
jdbc:h2:file:./data/datacenter-ledger;MODE=MySQL;DATABASE_TO_LOWER=TRUE
```

用户 `sa`，密码空。

### 2. 前端

另开终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开：**http://localhost:5173**

- 开发模式会把 `/api` 代理到 `127.0.0.1:8080`。
- **不要**用 `npm run preview` 代替日常开发（无代理）。

### 3. 建议走查路径

1. 首页资产列表 → 点「一键成图」看流向图  
2. 「管理流向」→ 表单编辑 / **可视化编辑**  
3. 顶栏：落点管理、程序管理、**供需查询**、**影响分析**  
4. 顶栏搜索（或 `/search`）  
5. 「资产全景」`/panorama`

---

## 功能地图

| 能力 | 入口（前端） | 主要后端 |
|------|--------------|----------|
| 资产 CRUD / 列表 | `/` · `/assets/:id/edit` | `/api/assets` |
| 一键成图 | `/assets/:id/graph` | `GET /api/assets/{id}/graph` |
| 保存节点布局 | 成图页「保存布局」 | `PUT /api/assets/{id}/layout` |
| 流向表单编辑 | `/assets/:id/flows/...` | `/api/assets/{id}/flows` · `/api/flows/{id}` |
| 流向可视化编辑 | `/assets/:id/flows/visual` | 同上 + options 列表 |
| 落点树 CRUD / CSV 导入 | `/endpoints` | `/api/endpoints` · import |
| 程序/脚本 | `/executors` | `/api/executors` |
| 派生/加工 | `/assets/:id/derivations` | `/api/assets/{id}/derivations` |
| 资产全景（血缘） | `/panorama` | `/api/graph/panorama` |
| 技术全景（多资产落点图） | `/panorama` 技术 Tab | `/api/graph/panorama/technical` |
| 供需查询 | `/lineage` | `/api/lineage/...` |
| 影响分析 | `/impact` · 各编辑页面板 · 删除确认 | `GET /api/impact` |
| 全局搜索 | 顶栏 · `/search` | `GET /api/search` |
| 全量导出 | 首页导出按钮 | `GET /api/export/full` |
| 流向配置说明 | `/docs/flow-editing` | 静态 Markdown |

---

## 仓库结构

```text
.
├── README.md                          # 本文件
├── pom.xml                            # 后端 Maven
├── data/                              # H2 数据文件（本地生成，勿依赖提交）
├── docs/
│   ├── implementation-logic.md        # ★ 实现逻辑（端到端）
│   ├── schema-design.md               # 库表与枚举
│   ├── graph-dto-design.md            # GraphDTO 契约
│   ├── flow-editing-guide.md          # 流向配置产品说明
│   └── sample-assets.md               # 样例资产说明
├── .cursor/rules/                     # Cursor Agent 约定（本地开发会加载）
├── src/main/java/com/binformation/ledger/
│   ├── controller/                    # REST
│   ├── service/                       # 领域逻辑 / 成图 / 影响 / 搜索 / 供需
│   ├── entity/ · mapper/              # MyBatis-Plus
│   ├── dto/                           # 对外契约
│   ├── support/                       # 落点层级、CSV、面包屑
│   └── exception/                     # ProblemDetail
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                  # Flyway V1 schema + V2… 样例
├── src/test/                          # Controller 等测试
└── frontend/
    ├── src/
    │   ├── api/ · views/ · components/
    │   ├── utils/                     # graphLayout · flowBoardGraph · …
    │   ├── types/ · router/
    │   └── style.css                  # 字体与主题变量
    ├── vite.config.ts                 # /api 代理
    └── public/docs/                   # 构建时从 docs/ 同步的指南
```

---

## 核心 API 索引

完整交互式文档见 Swagger。下列为日常开发高频接口。

### 资产与成图

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/assets` | 资产列表 |
| GET/POST/PUT/DELETE | `/api/assets` · `/{id}` | CRUD |
| GET | `/api/assets/{id}/graph` | 一键成图；`includeAuxiliary` · `includeUpstream` |
| PUT | `/api/assets/{id}/layout` | 保存落点坐标 |
| GET | `/api/assets/{id}/change-logs` | 变更记录 |

### 流向

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/assets/{assetId}/flows` | 列表 / 创建（含 paths/steps） |
| GET/PUT/DELETE | `/api/flows/{flowId}` | 详情 / 整单更新 / 删除 |

### 落点 · 程序 · 派生

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/endpoints?optionsOnly=true` | 选项列表（可视化编辑依赖） |
| GET/POST/PUT/DELETE | `/api/endpoints` · `/{id}` | CRUD；支持 `type` / `parentId` |
| GET | `/api/endpoints/import/template` | CSV 模板 |
| POST | `/api/endpoints/import` | CSV 导入 |
| GET/POST/PUT/DELETE | `/api/executors` · `/{id}` | 程序/脚本 |
| GET/POST | `/api/assets/{assetId}/derivations` | 派生 |
| GET/PUT/DELETE | `/api/derivations/{id}` | 派生单条 |

### 全景 · 供需 · 影响 · 搜索 · 导出

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/graph/panorama` | 资产级血缘全景 |
| GET | `/api/graph/panorama/technical` | 多资产技术成图 |
| GET | `/api/lineage/systems` | 系统列表 |
| GET | `/api/lineage/systems/{id}/assets` | 系统消费了哪些资产 |
| GET | `/api/lineage/assets/{id}/downstream-systems` | 资产流向哪些下游系统 |
| GET | `/api/impact` | `entityType` · `entityId` · `action=DELETE\|UPDATE` |
| GET | `/api/search?q=` | 全局搜索 |
| GET | `/api/export/full?format=json\|zip` | 全量导出 |

---

## 领域概念速查

```text
SECURITY_ZONE
  └── SYSTEM
        ├── KAFKA → KAFKA_TOPIC
        ├── ROCKETMQ → ROCKETMQ_TOPIC
        ├── OBJECT_STORAGE → OBJECT_BUCKET → OBJECT_PREFIX
        ├── HOST → DIRECTORY
        └── HTTP_API

DataAsset ──owns──► Flow(source→target Endpoint)
                      └── FlowPath ──► FlowStep(Executor, Host?)
DataAsset ◄──output── Derivation ◄──input── DataAsset(s)
                      └── Executor
```

- **主流向**（`isPrimary=true`）：默认进入一键成图；AUX / 非 primary 需打开「含辅助」或在可视化编辑中编辑。
- **图上关系**（CONTAINS / RUNS_ON / BROKER_OF / VIA_EXECUTOR）由成图服务**计算**，不是独立业务表。
- 枚举与表字段详见 [`docs/schema-design.md`](docs/schema-design.md)。

---

## 开发约定（Rules）

本仓库已配置 Cursor Rules（本地打开仓库后 Agent 会加载）：

| 文件 | 作用 |
|------|------|
| [`.cursor/rules/00-project.mdc`](.cursor/rules/00-project.mdc) | 全局：启动铁律、领域用语、文档权威、改动原则 |
| [`.cursor/rules/frontend.mdc`](.cursor/rules/frontend.mdc) | Vue/X6/Element Plus；**画边前必须有节点**；compact/full |
| [`.cursor/rules/backend.mdc`](.cursor/rules/backend.mdc) | 分层、Flyway、DTO、影响分析与成图边界 |
| [`.cursor/rules/docs.mdc`](.cursor/rules/docs.mdc) | 文档维护 |

**已知硬坑（务必遵守）：**

1. 可视化编辑板布局必须用 **full**，不能用 compact 过滤节点后再画 AUX 边。  
2. `el-radio-button` 用 **`value`**，不用废弃的 `label`。  
3. 字体用自托管 `@fontsource/*`，禁止 Google Fonts CDN。  
4. 前后端一起跑；H2 不要多实例抢锁。

更完整的流水线说明见 **[`docs/implementation-logic.md`](docs/implementation-logic.md)**。

---

## 文档索引

| 文档 | 内容 |
|------|------|
| [docs/implementation-logic.md](docs/implementation-logic.md) | **实现逻辑**：请求链路、成图、可视化编辑、影响/搜索/供需 |
| [docs/schema-design.md](docs/schema-design.md) | H2 表结构与枚举 |
| [docs/graph-dto-design.md](docs/graph-dto-design.md) | GraphDTO 字段契约 |
| [docs/flow-editing-guide.md](docs/flow-editing-guide.md) | 流向配置指南（产品）· 网页：`/docs/flow-editing` |
| [docs/sample-assets.md](docs/sample-assets.md) | 样例资产说明 |
| [frontend/README.md](frontend/README.md) | 前端简要说明 |

---

## 测试与构建

```bash
# 后端单测
mvn test

# 前端类型检查 + 生产构建（会同步 flow-editing-guide 到 public/docs）
cd frontend && npm run build
```

---

## 排障

| 现象 | 处理 |
|------|------|
| 页面一直转圈 | 先确认 `curl localhost:8080/api/assets`；再确认用的是 `npm run dev` 的 5173 |
| `/api` 502 | 后端未启动或未就绪；看 Vite 终端代理错误 |
| 请求超时 | `http.ts` 12s；后端卡住或未监听 8080 |
| H2 锁 / 启动失败 | 关掉多余的 `spring-boot:run`；必要时结束占用进程后再启 |
| 控制台 `source node … not exists` | 画了边但节点被 compact 过滤；见 Rules / implementation-logic |
| 字体加载慢或挂起 | 确认未引入 Google Fonts；应使用 `@fontsource` |
| 端口占用 | 8080 / 5173 换端口或结束旧进程 |
| 数据库「空了」 | 删了 `data/` 或换了工作目录；Flyway 会按迁移重建（含样例） |

---

## 从 Cloud Agent 转到本地

- **代码 / 分支 / PR**：通过 Git 同步（`git fetch` + checkout 功能分支或 `main`）。
- **Cloud 对话记忆**：不会自动进本地新聊天；重要约定已写入 `.cursor/rules` 与本文档。
- 建议本地首次：

```bash
git clone <本仓库>
cd binformation
git checkout main   # 或你的功能分支
mvn spring-boot:run          # 终端 1
cd frontend && npm i && npm run dev   # 终端 2
```

在 Cursor 中打开仓库根目录，Rules 会自动生效。需要对照历史 Agent 讨论时，可到 [cursor.com/agents](https://cursor.com/agents) 查看云端会话。
