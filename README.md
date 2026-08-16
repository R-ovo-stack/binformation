# 数据中心台账（datacenter-ledger）

数据资产流向台账与一键成图（后端 + 前端）。

## 技术栈

- 后端：Java 21 + Spring Boot 3 + H2 + Flyway + MyBatis-Plus
- 前端：Vue 3 + Element Plus + AntV X6 + @antv/layout

## 启动

请同时启动后端和前端。只开前端时，首页会一直转圈（请求 `/api/assets` 无响应）。

### 1. 后端

```bash
mvn spring-boot:run
```

就绪标志：终端出现 `Started LedgerApplication`，且可访问：

```bash
curl http://localhost:8080/api/assets
```

- API：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html
- H2 Console：http://localhost:8080/h2-console

### 2. 前端（一键成图）

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 （不要用 `npm run preview` 代替开发模式，否则 `/api` 不会代理到后端）。

1. 进入资产列表
2. 点击「订单文件数据」或「一键成图」
3. 查看自动布局流向图，点击连线查看步骤

### 本地一直转圈？

按顺序自查：

1. **后端是否在跑**：`curl http://localhost:8080/api/assets` 应返回 JSON。若失败，先只启动后端并等到 `Started LedgerApplication`。
2. **前端是否用开发服务器**：必须 `cd frontend && npm run dev`，浏览器打开 `http://localhost:5173`。
3. **端口占用**：8080 / 5173 被占用时换端口或结束旧进程。H2 被二次占用会出现数据库锁，关掉多余的 `spring-boot:run`。
4. **浏览器 Network**：看 `/api/assets` 是 pending / 502 / 超时。502 一般是后端未启动。
5. **不要只开 jar + 静态页而不配反向代理**：开发期请用 Vite 代理。

## 核心 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/assets` | 资产列表 |
| GET | `/api/assets/{id}` | 资产详情 |
| GET | `/api/graph/panorama` | 资产全景图（血缘：派生 + 落点衔接） |
| GET | `/api/graph/panorama/technical` | 技术全景（合并多资产落点级成图，`assetIds` 可选） |
| GET | `/api/export/full?format=json\|zip` | 全量导出（全部落点 + 全部资产流向；JSON 嵌套结构或 CSV 压缩包） |
| GET | `/api/endpoints/import/template` | 下载落点 CSV 导入模板 |
| POST | `/api/endpoints/import` | 上传 CSV 批量导入落点（multipart `file`） |
| GET | `/api/assets/{id}/graph` | 一键成图 GraphDTO |

```bash
curl http://localhost:8080/api/assets/1/graph
```

## 文档

- [库表字段设计](docs/schema-design.md)
- [GraphDTO 设计](docs/graph-dto-design.md)
- [样例数据资产一览](docs/sample-assets.md)
- [流向配置指南（表单/可视化/多路径/多步骤）](docs/flow-editing-guide.md) — 网页查看：http://localhost:5173/docs/flow-editing
- [前端说明](frontend/README.md)

## 构建

```bash
mvn test
cd frontend && npm run build
```
