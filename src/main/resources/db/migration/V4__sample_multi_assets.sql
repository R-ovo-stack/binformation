-- 多类数据资产样例：覆盖模型中的主要场景
-- 1) Kafka消息 · 跨区同步
-- 2) 文件 · RocketMQ通知+HTTP拉取接入，再Kafka通知+对象目录供给
-- 3) 文件 · 直推落盘
-- 4) 多源清洗组合派生规范数据，再对外共享

-- ---------------------------------------------------------------------------
-- 安全区 / 系统 / 中间件 / 主机
-- ---------------------------------------------------------------------------
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (200, 'SECURITY_ZONE', '生产区', NULL, 200, 'ACTIVE', '生产安全区'),
    (201, 'SECURITY_ZONE', '灾备区', NULL, 201, 'ACTIVE', '灾备安全区'),
    (202, 'SECURITY_ZONE', '外联区', NULL, 202, 'ACTIVE', '外联/外部接入区');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, status) VALUES
    (210, 'SYSTEM', '行情系统', 200, 200, 'ACTIVE'),
    (211, 'SYSTEM', '行情系统', 201, 201, 'ACTIVE'),
    (212, 'SYSTEM', '外部客户系统B', 202, 202, 'ACTIVE'),
    (213, 'SYSTEM', '内部数据平台', 200, 200, 'ACTIVE'),
    (214, 'SYSTEM', '风控下游系统', 200, 200, 'ACTIVE'),
    (215, 'SYSTEM', '应用集群', 200, 200, 'ACTIVE');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (220, 'KAFKA', 'Kafka-行情生产', 210, 200, 'ACTIVE', '生产行情Kafka'),
    (221, 'KAFKA', 'Kafka-行情灾备', 211, 201, 'ACTIVE', '灾备行情Kafka'),
    (222, 'KAFKA', 'Kafka-数据平台', 213, 200, 'ACTIVE', '内部数据平台Kafka'),
    (223, 'ROCKETMQ', 'RocketMQ-客户B', 212, 202, 'ACTIVE', '外部客户增量通知'),
    (224, 'OBJECT_STORAGE', '对象存储-数据平台', 213, 200, 'ACTIVE', NULL);

INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status) VALUES
    (230, 'KAFKA_TOPIC', 'topic-tick', 220, 200, '{"topicName":"topic-tick"}', 'ACTIVE'),
    (231, 'KAFKA_TOPIC', 'topic-tick', 221, 201, '{"topicName":"topic-tick"}', 'ACTIVE'),
    (232, 'KAFKA_TOPIC', 'notify-cust-file', 222, 200, '{"topicName":"notify-cust-file"}', 'ACTIVE'),
    (233, 'KAFKA_TOPIC', 'notify-norm-position', 222, 200, '{"topicName":"notify-norm-position"}', 'ACTIVE'),
    (234, 'ROCKETMQ_TOPIC', 'file-incr-notify', 223, 202, '{"topicName":"file-incr-notify"}', 'ACTIVE'),
    (235, 'HTTP_API', '/api/file/download', 212, 202, '{"url":"https://ext-b.example/api/file/download","method":"GET"}', 'ACTIVE'),
    (236, 'OBJECT_BUCKET', 'cust-file-bucket', 224, 200, '{"bucketName":"cust-file-bucket"}', 'ACTIVE'),
    (237, 'OBJECT_PREFIX', 'inbound/cust/', 236, 200, '{"prefix":"inbound/cust/"}', 'ACTIVE'),
    (238, 'OBJECT_PREFIX', 'norm/position/', 236, 200, '{"prefix":"norm/position/"}', 'ACTIVE'),
    (239, 'OBJECT_PREFIX', 'raw/position/', 236, 200, '{"prefix":"raw/position/"}', 'ACTIVE'),
    (240, 'OBJECT_PREFIX', 'raw/customer/', 236, 200, '{"prefix":"raw/customer/"}', 'ACTIVE');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status, remark) VALUES
    (250, 'HOST', 'quote-broker-01', 210, 200, '{"hostname":"quote-broker-01","role":"kafka-broker"}', 'ACTIVE', 'Kafka节点'),
    (251, 'HOST', 'quote-broker-02', 210, 200, '{"hostname":"quote-broker-02","role":"kafka-broker"}', 'ACTIVE', 'Kafka节点'),
    (252, 'HOST', 'quote-dr-01', 211, 201, '{"hostname":"quote-dr-01","role":"kafka-broker"}', 'ACTIVE', 'Kafka节点'),
    (253, 'HOST', 'quote-sync-01', 210, 200, '{"hostname":"quote-sync-01","role":"sync"}', 'ACTIVE', '跨区同步节点'),
    (254, 'HOST', 'ingest-01', 213, 200, '{"hostname":"ingest-01","role":"ingest"}', 'ACTIVE', '文件接入节点'),
    (255, 'HOST', 'etl-01', 213, 200, '{"hostname":"etl-01","role":"etl"}', 'ACTIVE', '清洗组合节点'),
    (256, 'HOST', 'app-01', 215, 200, '{"hostname":"app-01","role":"app"}', 'ACTIVE', '应用主机'),
    (257, 'HOST', 'log-collector-01', 213, 200, '{"hostname":"log-collector-01","role":"collector"}', 'ACTIVE', '日志采集节点'),
    (258, 'DIRECTORY', '/data/app/logs', 256, 200, '{"dirPath":"/data/app/logs"}', 'ACTIVE', NULL),
    (259, 'DIRECTORY', '/data/collect/logs', 257, 200, '{"dirPath":"/data/collect/logs"}', 'ACTIVE', NULL);

-- ---------------------------------------------------------------------------
-- 程序 / 脚本
-- ---------------------------------------------------------------------------
INSERT INTO executor (id, name, code, kind, default_host_id, status, remark) VALUES
    (200, 'quote-cross-sync', 'quote-cross-sync', 'PROGRAM', 253, 'ACTIVE', '行情跨区同步程序'),
    (201, 'cust-file-ingest', 'cust-file-ingest', 'PROGRAM', 254, 'ACTIVE', '客户文件通知拉取接入'),
    (202, 'cust-file-share', 'cust-file-share', 'PROGRAM', 254, 'ACTIVE', '客户文件Kafka通知+对象目录供给'),
    (203, 'position-etl', 'position-etl', 'PROGRAM', 255, 'ACTIVE', '多源清洗组合为规范持仓'),
    (204, 'norm-position-share', 'norm-position-share', 'PROGRAM', 254, 'ACTIVE', '规范持仓对外共享'),
    (205, 'log-direct-ship', 'log-direct-ship', 'SCRIPT', 257, 'ACTIVE', '应用日志目录直推采集');

-- ---------------------------------------------------------------------------
-- 数据资产
-- ---------------------------------------------------------------------------
INSERT INTO data_asset (id, name, code, data_type, status, remark) VALUES
    (200, '跨区行情Tick消息', 'ASSET_QUOTE_TICK', 'KAFKA_MESSAGE', 'ACTIVE',
     '生产区topic-tick跨区同步到灾备区topic-tick'),
    (201, '外部客户增量文件', 'ASSET_CUST_FILE_INCR', 'FILE', 'ACTIVE',
     'RocketMQ增量通知+HTTP下载接入，再Kafka通知+对象目录供给下游'),
    (202, '原始持仓快照文件', 'ASSET_RAW_POSITION', 'FILE', 'ACTIVE',
     '原始持仓文件，作为规范持仓加工输入'),
    (203, '原始客户主数据文件', 'ASSET_RAW_CUSTOMER', 'FILE', 'ACTIVE',
     '原始客户主数据，作为规范持仓加工输入'),
    (204, '规范持仓数据', 'ASSET_NORM_POSITION', 'FILE', 'ACTIVE',
     '由原始持仓+客户主数据经position-etl清洗组合生成，再共享给风控'),
    (205, '应用日志直推', 'ASSET_APP_LOG_DIRECT', 'FILE', 'ACTIVE',
     '应用主机日志目录直推到采集目录');

-- ---------------------------------------------------------------------------
-- 1) Kafka消息 · 跨区同步
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (200, 200, 230, 231, 'SYNC', TRUE, 'ACTIVE', '生产topic-tick → 灾备topic-tick');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (200, 200, '跨区同步主路径', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (200, 200, 1, 253, 200, 'CROSS_ZONE_PUSH', 'quote-cross-sync@quote-sync-01 跨区推送');

-- ---------------------------------------------------------------------------
-- 2) 文件 · 通知+拉取接入，再通知+共享读取供给
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (210, 201, 212, 237, 'INGEST', TRUE, 'ACTIVE', '外部客户系统B → 对象目录 inbound/cust/'),
    (211, 201, 237, 214, 'SHARE', TRUE, 'ACTIVE', '对象目录 → 风控下游系统'),
    (212, 201, 234, 254, 'AUX', FALSE, 'ACTIVE', '辅助：RocketMQ通知主题 → 接入主机'),
    (213, 201, 235, 237, 'AUX', FALSE, 'ACTIVE', '辅助：HTTP下载接口 → 对象目录'),
    (214, 201, 232, 214, 'AUX', FALSE, 'ACTIVE', '辅助：Kafka通知主题 → 下游系统');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (210, 210, '通知+拉取', TRUE, 0),
    (211, 211, '通知+共享读取', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (210, 210, 1, 254, 201, 'NOTIFY_THEN_PULL', '订阅RocketMQ增量通知并HTTP下载'),
    (211, 210, 2, 254, 201, 'DIRECT_PUSH', '写入对象目录 inbound/cust/'),
    (212, 211, 1, 254, 202, 'NOTIFY_THEN_SHARED_READ', '发Kafka通知，下游读对象目录');

-- ---------------------------------------------------------------------------
-- 3) 原始文件接入（供派生输入）
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (220, 202, 212, 239, 'INGEST', TRUE, 'ACTIVE', '外部系统 → 原始持仓目录'),
    (221, 203, 212, 240, 'INGEST', TRUE, 'ACTIVE', '外部系统 → 原始客户目录');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (220, 220, '文件拉取', TRUE, 0),
    (221, 221, '文件拉取', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (220, 220, 1, 254, 201, 'NOTIFY_THEN_PULL', '拉取原始持仓文件'),
    (221, 221, 1, 254, 201, 'NOTIFY_THEN_PULL', '拉取原始客户主数据');

-- ---------------------------------------------------------------------------
-- 4) 派生/加工：原始持仓+客户主数据 → 规范持仓，再共享
-- ---------------------------------------------------------------------------
INSERT INTO derivation (id, name, output_asset_id, executor_id, host_id, status, remark) VALUES
    (200, '持仓规范清洗组合', 204, 203, 255, 'ACTIVE',
     '输入原始持仓与客户主数据，输出规范持仓数据');

INSERT INTO derivation_input (id, derivation_id, input_asset_id, sort_order) VALUES
    (200, 200, 202, 1),
    (201, 200, 203, 2);

INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (230, 204, 238, 214, 'SHARE', TRUE, 'ACTIVE', '规范持仓目录 → 风控下游'),
    (231, 204, 233, 214, 'AUX', FALSE, 'ACTIVE', '辅助：规范持仓Kafka通知');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (230, 230, '通知+共享读取', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (230, 230, 1, 254, 204, 'NOTIFY_THEN_SHARED_READ', 'norm-position-share 通知下游读取规范目录');

-- ---------------------------------------------------------------------------
-- 5) 文件 · 目录直推
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (240, 205, 258, 259, 'FORWARD', TRUE, 'ACTIVE', '应用日志目录 → 采集目录');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (240, 240, '直推采集', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (240, 240, 1, 257, 205, 'DIRECT_PUSH', 'log-direct-ship 脚本直推日志文件');

ALTER TABLE endpoint ALTER COLUMN id RESTART WITH 300;
ALTER TABLE executor ALTER COLUMN id RESTART WITH 300;
ALTER TABLE data_asset ALTER COLUMN id RESTART WITH 300;
ALTER TABLE flow ALTER COLUMN id RESTART WITH 300;
ALTER TABLE flow_path ALTER COLUMN id RESTART WITH 300;
ALTER TABLE flow_step ALTER COLUMN id RESTART WITH 300;
ALTER TABLE derivation ALTER COLUMN id RESTART WITH 300;
ALTER TABLE derivation_input ALTER COLUMN id RESTART WITH 300;
