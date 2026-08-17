# 前端 · 数据中心台账

Vue 3 + Element Plus + AntV X6。完整启动、功能地图与排障见**仓库根目录** [`README.md`](../README.md)；实现逻辑见 [`docs/implementation-logic.md`](../docs/implementation-logic.md)。

## 启动

```bash
# 终端 1（仓库根）
mvn spring-boot:run

# 终端 2
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 （开发模式会代理 `/api` → `8080`）。

## 目录要点

| 路径 | 说明 |
|------|------|
| `src/api/` | 按领域划分的 HTTP 客户端 |
| `src/views/` | 路由页面 |
| `src/components/` | 画布、导航、影响面板等 |
| `src/utils/graphLayout.ts` | 一键成图布局 / 边展开 |
| `src/utils/flowBoardGraph.ts` | 可视化编辑态 → AssetGraph |
| `src/router/index.ts` | 全部路由 |

## 约定

Cursor Rules：`../.cursor/rules/frontend.mdc`（画边前必须有节点、板布局用 full、`el-radio-button` 用 `value`、禁止 Google Fonts CDN）。

## 流向配置说明

- 网页：http://localhost:5173/docs/flow-editing  
- 源文件：`../docs/flow-editing-guide.md`（`npm run build` 前会同步到 `public/docs/`）
