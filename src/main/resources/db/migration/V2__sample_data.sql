-- 示例数据：跨区 Kafka 同步 + 通知+取数 场景

-- 安全区
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status) VALUES
    (1, 'SECURITY_ZONE', '安全区A', NULL, 1, 'ACTIVE'),
    (2, 'SECURITY_ZONE', '安全区B', NULL, 2, 'ACTIVE');

-- 系统
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status) VALUES
    (10, 'SYSTEM', '外部系统S', 1, 1, 'ACTIVE'),
    (11, 'SYSTEM', '我方系统', 1, 1, 'ACTIVE'),
    (12, 'SYSTEM', '我方系统', 2, 2, 'ACTIVE'),
    (13, 'SYSTEM', '下游系统T', 2, 2, 'ACTIVE');

-- Kafka / 对象存储 / 主机
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status) VALUES
    (20, 'KAFKA', 'Kafka-A', 11, 1, 'ACTIVE'),
    (21, 'KAFKA', 'Kafka-B', 12, 2, 'ACTIVE'),
    (30, 'OBJECT_STORAGE', '对象存储-OSS', 11, 1, 'ACTIVE'),
    (31, 'OBJECT_BUCKET', '业务桶', 30, 1, 'ACTIVE'),
    (32, 'OBJECT_PREFIX', 'data/order/', 31, 1, 'ACTIVE'),
    (40, 'HOST', 'sync-node-01', 11, 1, 'ACTIVE');

-- 主题
INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status) VALUES
    (50, 'KAFKA_TOPIC', 'topic-order', 20, 1, '{"topicName":"topic-order"}', 'ACTIVE'),
    (51, 'KAFKA_TOPIC', 'topic-order', 21, 2, '{"topicName":"topic-order"}', 'ACTIVE'),
    (52, 'KAFKA_TOPIC', 'notify-order', 21, 2, '{"topicName":"notify-order"}', 'ACTIVE');

-- 程序
INSERT INTO executor (id, name, code, kind, default_host_id, status) VALUES
    (1, '跨区同步程序', 'sync-cross-zone', 'PROGRAM', 40, 'ACTIVE'),
    (2, '文件接入程序', 'file-ingest', 'PROGRAM', 40, 'ACTIVE'),
    (3, '下游供给程序', 'downstream-share', 'PROGRAM', 40, 'ACTIVE');

-- 数据资产
INSERT INTO data_asset (id, name, code, data_type, status) VALUES
    (1, '订单文件数据', 'ASSET_ORDER_FILE', 'FILE', 'ACTIVE');

-- 流向：跨区同步
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status) VALUES
    (1, 1, 50, 51, 'SYNC', TRUE, 'ACTIVE');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (1, 1, '默认路径', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method) VALUES
    (1, 1, 1, 40, 1, 'CROSS_ZONE_PUSH');

-- 流向：接入（外部系统 -> 对象目录）
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status) VALUES
    (2, 1, 10, 32, 'INGEST', TRUE, 'ACTIVE');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (2, 2, 'RocketMQ+HTTP', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method) VALUES
    (2, 2, 1, 40, 2, 'NOTIFY_THEN_PULL'),
    (3, 2, 2, 40, 2, 'DIRECT_PUSH');

-- 流向：供给（对象目录 -> 下游系统）
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status) VALUES
    (3, 1, 32, 13, 'SHARE', TRUE, 'ACTIVE');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (3, 3, 'Kafka+对象目录', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method) VALUES
    (4, 3, 1, 40, 3, 'NOTIFY_THEN_SHARED_READ');

-- 辅助流向：通知主题
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status) VALUES
    (4, 1, 52, 13, 'AUX', FALSE, 'ACTIVE');

ALTER TABLE endpoint ALTER COLUMN id RESTART WITH 100;
ALTER TABLE executor ALTER COLUMN id RESTART WITH 100;
ALTER TABLE data_asset ALTER COLUMN id RESTART WITH 100;
ALTER TABLE flow ALTER COLUMN id RESTART WITH 100;
ALTER TABLE flow_path ALTER COLUMN id RESTART WITH 100;
ALTER TABLE flow_step ALTER COLUMN id RESTART WITH 100;
