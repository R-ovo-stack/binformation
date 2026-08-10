# 前端 · 流向可视化（一键成图）

Vue 3 + Element Plus + AntV X6，消费后端 GraphDTO。

## 启动

先启动后端（仓库根目录）：

```bash
mvn spring-boot:run
```

再启动前端：

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173

## 功能

- 资产列表
- 资产流向图（一键成图 + Dagre 自动布局）
- 点击连线查看路径/步骤
- 辅助流向开关
- 导出 PNG

## 代理

开发环境通过 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`。
