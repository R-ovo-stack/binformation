-- 示例：外部系统A Kafka主题C → idc310/kdc-forward → 内部Kafka主题C
-- 内部 Kafka 节点：idc301 / idc302 / idc303

-- 安全区
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (100, 'SECURITY_ZONE', '外部区', NULL, 100, 'ACTIVE', '外部系统所在安全区'),
    (101, 'SECURITY_ZONE', 'IDC内部区', NULL, 101, 'ACTIVE', '内部机房安全区');

-- 系统
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status) VALUES
    (110, 'SYSTEM', '外部系统A', 100, 100, 'ACTIVE'),
    (111, 'SYSTEM', '内部数据中心', 101, 101, 'ACTIVE');

-- Kafka 集群
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (120, 'KAFKA', 'Kafka-外部A', 110, 100, 'ACTIVE', '外部系统A侧Kafka'),
    (121, 'KAFKA', 'Kafka-内部', 111, 101, 'ACTIVE', '内部Kafka集群');

-- Kafka 主题 C（同名不同落点）
INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status) VALUES
    (130, 'KAFKA_TOPIC', 'C', 120, 100, '{"topicName":"C"}', 'ACTIVE'),
    (131, 'KAFKA_TOPIC', 'C', 121, 101, '{"topicName":"C"}', 'ACTIVE');

-- 主机：内部Kafka节点 + 转发程序节点
INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status, remark) VALUES
    (140, 'HOST', 'idc301', 111, 101, '{"hostname":"idc301","role":"kafka-broker"}', 'ACTIVE', '内部Kafka节点'),
    (141, 'HOST', 'idc302', 111, 101, '{"hostname":"idc302","role":"kafka-broker"}', 'ACTIVE', '内部Kafka节点'),
    (142, 'HOST', 'idc303', 111, 101, '{"hostname":"idc303","role":"kafka-broker"}', 'ACTIVE', '内部Kafka节点'),
    (143, 'HOST', 'idc310', 111, 101, '{"hostname":"idc310","role":"forward"}', 'ACTIVE', '部署kdc-forward的节点');

-- 程序
INSERT INTO executor (id, name, code, kind, default_host_id, status, remark) VALUES
    (100, 'kdc-forward', 'kdc-forward', 'PROGRAM', 143, 'ACTIVE', '订阅外部Kafka并转发到内部Kafka');

-- 数据资产
INSERT INTO data_asset (id, name, code, data_type, status, remark) VALUES
    (100, '外部系统A-主题C消息', 'ASSET_EXT_A_TOPIC_C', 'KAFKA_MESSAGE', 'ACTIVE',
     '由外部系统A的Kafka主题C经kdc-forward转发至内部Kafka主题C');

-- 流向：外部主题C → 内部主题C
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (100, 100, 130, 131, 'FORWARD', TRUE, 'ACTIVE', '外部Kafka C → 内部Kafka C');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order, remark) VALUES
    (100, 100, 'kdc-forward默认路径', TRUE, 0, '部署于idc310');

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (100, 100, 1, 143, 100, 'KAFKA_SUBSCRIBE_FORWARD',
     '在idc310运行kdc-forward，订阅外部系统A Kafka主题C，转发到内部Kafka(idc301/302/303)主题C');

ALTER TABLE endpoint ALTER COLUMN id RESTART WITH 200;
ALTER TABLE executor ALTER COLUMN id RESTART WITH 200;
ALTER TABLE data_asset ALTER COLUMN id RESTART WITH 200;
ALTER TABLE flow ALTER COLUMN id RESTART WITH 200;
ALTER TABLE flow_path ALTER COLUMN id RESTART WITH 200;
ALTER TABLE flow_step ALTER COLUMN id RESTART WITH 200;
