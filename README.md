# 数据中心台账（datacenter-ledger）

数据资产流向台账与一键成图后端服务。

## 技术栈

- Java 21 + Spring Boot 3
- H2（文件持久化）+ Flyway
- MyBatis-Plus
- SpringDoc OpenAPI

## 启动

```bash
mvn spring-boot:run
```

- API：http://localhost:8080
- Swagger UI：http://localhost:8080/swagger-ui.html
- H2 Console：http://localhost:8080/h2-console

## 核心 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/assets` | 资产列表 |
| GET | `/api/assets/{id}` | 资产详情 |
| GET | `/api/assets/{id}/graph` | 一键成图 GraphDTO |

`includeAuxiliary=true` 可包含辅助流向。

示例：

```bash
curl http://localhost:8080/api/assets/1/graph
```

## 文档

- [库表字段设计](docs/schema-design.md)
- [GraphDTO 设计](docs/graph-dto-design.md)

## 构建

```bash
mvn test
mvn package
```
