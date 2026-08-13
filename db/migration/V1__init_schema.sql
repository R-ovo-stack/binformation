-- 数据中心台账 · H2 初版表结构
-- Flyway migration V1

-- ---------------------------------------------------------------------------
-- 1. endpoint 落点
-- ---------------------------------------------------------------------------
CREATE TABLE endpoint (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    code            VARCHAR(64),
    parent_id       BIGINT,
    zone_id         BIGINT,
    attrs           CLOB,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    owner           VARCHAR(64),
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT uk_endpoint_parent_type_name UNIQUE (parent_id, type, name),
    CONSTRAINT fk_endpoint_parent FOREIGN KEY (parent_id) REFERENCES endpoint (id),
    CONSTRAINT fk_endpoint_zone FOREIGN KEY (zone_id) REFERENCES endpoint (id)
);

CREATE INDEX idx_endpoint_parent ON endpoint (parent_id);
CREATE INDEX idx_endpoint_zone ON endpoint (zone_id);
CREATE INDEX idx_endpoint_type ON endpoint (type);

-- 顶层安全区 parent_id 为 NULL 时，uk_endpoint_parent_type_name 无法约束重名；
-- 应用层保证 type=SECURITY_ZONE 且 parent_id IS NULL 时 name 唯一

-- ---------------------------------------------------------------------------
-- 2. data_asset 数据资产
-- ---------------------------------------------------------------------------
CREATE TABLE data_asset (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    data_type       VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    owner           VARCHAR(64),
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT uk_data_asset_code UNIQUE (code)
);

CREATE INDEX idx_data_asset_type ON data_asset (data_type);
CREATE INDEX idx_data_asset_status ON data_asset (status);

-- ---------------------------------------------------------------------------
-- 3. executor 程序/脚本
-- ---------------------------------------------------------------------------
CREATE TABLE executor (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    kind            VARCHAR(16)  NOT NULL,
    default_host_id BIGINT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    owner           VARCHAR(64),
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT uk_executor_code UNIQUE (code),
    CONSTRAINT fk_executor_default_host FOREIGN KEY (default_host_id) REFERENCES endpoint (id)
);

CREATE INDEX idx_executor_kind ON executor (kind);

-- ---------------------------------------------------------------------------
-- 4. flow 流向
-- ---------------------------------------------------------------------------
CREATE TABLE flow (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id            BIGINT       NOT NULL,
    source_endpoint_id  BIGINT       NOT NULL,
    target_endpoint_id  BIGINT       NOT NULL,
    purpose             VARCHAR(16)  NOT NULL,
    is_primary          BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    owner               VARCHAR(64),
    remark              VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    CONSTRAINT fk_flow_asset FOREIGN KEY (asset_id) REFERENCES data_asset (id),
    CONSTRAINT fk_flow_source FOREIGN KEY (source_endpoint_id) REFERENCES endpoint (id),
    CONSTRAINT fk_flow_target FOREIGN KEY (target_endpoint_id) REFERENCES endpoint (id)
);

CREATE INDEX idx_flow_asset ON flow (asset_id);
CREATE INDEX idx_flow_source ON flow (source_endpoint_id);
CREATE INDEX idx_flow_target ON flow (target_endpoint_id);

-- ---------------------------------------------------------------------------
-- 5. flow_path 路径
-- ---------------------------------------------------------------------------
CREATE TABLE flow_path (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_id         BIGINT       NOT NULL,
    name            VARCHAR(128) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flow_path_flow FOREIGN KEY (flow_id) REFERENCES flow (id) ON DELETE CASCADE
);

CREATE INDEX idx_flow_path_flow ON flow_path (flow_id);

-- ---------------------------------------------------------------------------
-- 6. flow_step 步骤
-- ---------------------------------------------------------------------------
CREATE TABLE flow_step (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_id         BIGINT       NOT NULL,
    seq             INT          NOT NULL,
    host_id         BIGINT,
    executor_id     BIGINT       NOT NULL,
    method          VARCHAR(32)  NOT NULL,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flow_step_path_seq UNIQUE (path_id, seq),
    CONSTRAINT fk_flow_step_path FOREIGN KEY (path_id) REFERENCES flow_path (id) ON DELETE CASCADE,
    CONSTRAINT fk_flow_step_host FOREIGN KEY (host_id) REFERENCES endpoint (id),
    CONSTRAINT fk_flow_step_executor FOREIGN KEY (executor_id) REFERENCES executor (id)
);

CREATE INDEX idx_flow_step_executor ON flow_step (executor_id);

-- ---------------------------------------------------------------------------
-- 7. derivation 派生/加工
-- ---------------------------------------------------------------------------
CREATE TABLE derivation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    output_asset_id BIGINT       NOT NULL,
    executor_id     BIGINT       NOT NULL,
    host_id         BIGINT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    owner           VARCHAR(64),
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT fk_derivation_output FOREIGN KEY (output_asset_id) REFERENCES data_asset (id),
    CONSTRAINT fk_derivation_executor FOREIGN KEY (executor_id) REFERENCES executor (id),
    CONSTRAINT fk_derivation_host FOREIGN KEY (host_id) REFERENCES endpoint (id)
);

CREATE INDEX idx_derivation_output ON derivation (output_asset_id);

-- ---------------------------------------------------------------------------
-- 8. derivation_input 派生输入
-- ---------------------------------------------------------------------------
CREATE TABLE derivation_input (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    derivation_id   BIGINT    NOT NULL,
    input_asset_id  BIGINT    NOT NULL,
    sort_order      INT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_derivation_input UNIQUE (derivation_id, input_asset_id),
    CONSTRAINT fk_derivation_input_derivation FOREIGN KEY (derivation_id) REFERENCES derivation (id) ON DELETE CASCADE,
    CONSTRAINT fk_derivation_input_asset FOREIGN KEY (input_asset_id) REFERENCES data_asset (id)
);

-- ---------------------------------------------------------------------------
-- 9. flow_layout 流向图布局
-- ---------------------------------------------------------------------------
CREATE TABLE flow_layout (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id        BIGINT    NOT NULL,
    endpoint_id     BIGINT    NOT NULL,
    layout_x        DOUBLE    NOT NULL DEFAULT 0,
    layout_y        DOUBLE    NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flow_layout UNIQUE (asset_id, endpoint_id),
    CONSTRAINT fk_flow_layout_asset FOREIGN KEY (asset_id) REFERENCES data_asset (id) ON DELETE CASCADE,
    CONSTRAINT fk_flow_layout_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoint (id)
);

-- ---------------------------------------------------------------------------
-- 10. change_log 变更单
-- ---------------------------------------------------------------------------
CREATE TABLE change_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type         VARCHAR(32)  NOT NULL,
    entity_id           BIGINT       NOT NULL,
    action              VARCHAR(16)  NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    operator            VARCHAR(64)  NOT NULL,
    operated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    related_asset_id    BIGINT,
    remark              VARCHAR(500)
);

CREATE INDEX idx_change_log_asset_time ON change_log (related_asset_id, operated_at);
CREATE INDEX idx_change_log_entity ON change_log (entity_type, entity_id);
CREATE INDEX idx_change_log_time ON change_log (operated_at);

-- ---------------------------------------------------------------------------
-- 11. change_log_item 变更明细
-- ---------------------------------------------------------------------------
CREATE TABLE change_log_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_log_id   BIGINT      NOT NULL,
    field_name      VARCHAR(64) NOT NULL,
    old_value       CLOB,
    new_value       CLOB,
    CONSTRAINT fk_change_log_item_log FOREIGN KEY (change_log_id) REFERENCES change_log (id) ON DELETE CASCADE
);

CREATE INDEX idx_change_log_item_log ON change_log_item (change_log_id);
