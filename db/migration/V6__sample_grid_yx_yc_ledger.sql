-- 样例：安全二区 A系统 → 二区B Kafka(cloud201/202) 主题ABC
--      → 跨区同步到安全三区 B Kafka(idc301/302/303) 主题ABC
--      → idc305 程序拼接生成数据D(/data/d/) → SFTP到 idc306:/data/dd
--      → idc306 程序监听增量推送到三区 Kafka 主题F

-- ---------------------------------------------------------------------------
-- 安全区 / 系统
-- ---------------------------------------------------------------------------
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (400, 'SECURITY_ZONE', '安全二区', NULL, 400, 'ACTIVE', '安全2区'),
    (401, 'SECURITY_ZONE', '安全三区', NULL, 401, 'ACTIVE', '安全3区');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (410, 'SYSTEM', 'A系统', 400, 400, 'ACTIVE', '安全二区A系统，推送变化遥信/遥测与全量开关台账'),
    (411, 'SYSTEM', 'B系统', 400, 400, 'ACTIVE', '安全二区B系统'),
    (412, 'SYSTEM', 'B系统', 401, 401, 'ACTIVE', '安全三区B系统');

-- ---------------------------------------------------------------------------
-- Kafka / 主机 / 主题 / 目录
-- ---------------------------------------------------------------------------
INSERT INTO endpoint (id, type, name, parent_id, zone_id, status, remark) VALUES
    (420, 'KAFKA', 'Kafka-cloud', 411, 400, 'ACTIVE', '安全二区B系统Kafka，节点cloud201/202'),
    (421, 'KAFKA', 'Kafka-三区', 412, 401, 'ACTIVE', '安全三区B系统Kafka，节点idc301/302/303');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status, remark) VALUES
    (430, 'HOST', 'cloud201', 411, 400, '{"hostname":"cloud201","role":"kafka-broker"}', 'ACTIVE', '二区B Kafka节点'),
    (431, 'HOST', 'cloud202', 411, 400, '{"hostname":"cloud202","role":"kafka-broker"}', 'ACTIVE', '二区B Kafka节点'),
    (432, 'HOST', 'idc301', 412, 401, '{"hostname":"idc301","role":"kafka-broker"}', 'ACTIVE', '三区B Kafka节点'),
    (433, 'HOST', 'idc302', 412, 401, '{"hostname":"idc302","role":"kafka-broker"}', 'ACTIVE', '三区B Kafka节点'),
    (434, 'HOST', 'idc303', 412, 401, '{"hostname":"idc303","role":"kafka-broker"}', 'ACTIVE', '三区B Kafka节点'),
    (435, 'HOST', 'idc305', 412, 401, '{"hostname":"idc305","role":"app"}', 'ACTIVE', '拼接程序与SFTP脚本节点'),
    (436, 'HOST', 'idc306', 412, 401, '{"hostname":"idc306","role":"app"}', 'ACTIVE', '目录监听推送程序节点'),
    (437, 'HOST', 'a-push-01', 410, 400, '{"hostname":"a-push-01","role":"push"}', 'ACTIVE', 'A系统推送节点');

INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status, remark) VALUES
    (440, 'KAFKA_TOPIC', 'A', 420, 400, '{"topicName":"A"}', 'ACTIVE', '二区：变化遥信'),
    (441, 'KAFKA_TOPIC', 'B', 420, 400, '{"topicName":"B"}', 'ACTIVE', '二区：变化遥测'),
    (442, 'KAFKA_TOPIC', 'C', 420, 400, '{"topicName":"C"}', 'ACTIVE', '二区：全量开关台账(2小时一次)'),
    (443, 'KAFKA_TOPIC', 'A', 421, 401, '{"topicName":"A"}', 'ACTIVE', '三区：变化遥信'),
    (444, 'KAFKA_TOPIC', 'B', 421, 401, '{"topicName":"B"}', 'ACTIVE', '三区：变化遥测'),
    (445, 'KAFKA_TOPIC', 'C', 421, 401, '{"topicName":"C"}', 'ACTIVE', '三区：全量开关台账'),
    (446, 'KAFKA_TOPIC', 'F', 421, 401, '{"topicName":"F"}', 'ACTIVE', '三区：由/data/dd增量生成'),
    (450, 'DIRECTORY', '/data/d/', 435, 401, '{"dirPath":"/data/d/"}', 'ACTIVE', 'idc305 拼接输出目录(数据D)'),
    (451, 'DIRECTORY', '/data/dd', 436, 401, '{"dirPath":"/data/dd"}', 'ACTIVE', 'idc306 SFTP落盘目录');

-- ---------------------------------------------------------------------------
-- 程序 / 脚本
-- ---------------------------------------------------------------------------
INSERT INTO executor (id, name, code, kind, default_host_id, status, remark) VALUES
    (400, 'yx-yc-ledger-push', 'yx-yc-ledger-push', 'PROGRAM', 437, 'ACTIVE',
     'A系统推送变化遥信/遥测及全量开关台账到二区B Kafka'),
    (401, 'cross-zone-abc-sync', 'cross-zone-abc-sync', 'PROGRAM', 435, 'ACTIVE',
     '跨区隔离同步 ABC 主题：二区cloud → 三区 idc'),
    (402, 'abc-stitch-d', 'abc-stitch-d', 'PROGRAM', 435, 'ACTIVE',
     'idc305 消费三区ABC主题，拼接生成数据D到 /data/d/'),
    (403, 'sftp-d-to-dd', 'sftp-d-to-dd', 'SCRIPT', 435, 'ACTIVE',
     'idc305 SFTP脚本，将 /data/d/ 数据D发送到 idc306:/data/dd'),
    (404, 'dd-watch-to-f', 'dd-watch-to-f', 'PROGRAM', 436, 'ACTIVE',
     'idc306 监听 /data/dd 增量文件，推送到三区 Kafka 主题F');

-- ---------------------------------------------------------------------------
-- 数据资产
-- ---------------------------------------------------------------------------
INSERT INTO data_asset (id, name, code, data_type, status, remark) VALUES
    (400, '电网遥信遥测台账链路', 'ASSET_GRID_YX_YC_LEDGER', 'FILE', 'ACTIVE',
     '二区A推送ABC→二区B Kafka(cloud201/202)→跨区同步三区B Kafka(idc301/302/303)→idc305拼接D→SFTP到idc306→推送主题F');

-- ---------------------------------------------------------------------------
-- 流向：A系统推送到二区B Kafka 主题 A/B/C
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (400, 400, 410, 440, 'SHARE', TRUE, 'ACTIVE', '变化遥信 → 主题A'),
    (401, 400, 410, 441, 'SHARE', TRUE, 'ACTIVE', '变化遥测 → 主题B'),
    (402, 400, 410, 442, 'SHARE', TRUE, 'ACTIVE', '全量开关台账(2h) → 主题C');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (400, 400, '默认路径', TRUE, 0),
    (401, 401, '默认路径', TRUE, 0),
    (402, 402, '默认路径', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (400, 400, 1, 437, 400, 'DIRECT_PUSH', '推送变化遥信到 Kafka-cloud 主题A'),
    (401, 401, 1, 437, 400, 'DIRECT_PUSH', '推送变化遥测到 Kafka-cloud 主题B'),
    (402, 402, 1, 437, 400, 'DIRECT_PUSH', '每2小时推送全量开关台账到主题C');

-- ---------------------------------------------------------------------------
-- 流向：跨区隔离同步 ABC → 三区同名主题
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (403, 400, 440, 443, 'SYNC', TRUE, 'ACTIVE', '主题A 二区→三区'),
    (404, 400, 441, 444, 'SYNC', TRUE, 'ACTIVE', '主题B 二区→三区'),
    (405, 400, 442, 445, 'SYNC', TRUE, 'ACTIVE', '主题C 二区→三区');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (403, 403, '跨区隔离', TRUE, 0),
    (404, 404, '跨区隔离', TRUE, 0),
    (405, 405, '跨区隔离', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (403, 403, 1, 435, 401, 'CROSS_ZONE_PUSH', '跨区隔离同步主题A'),
    (404, 404, 1, 435, 401, 'CROSS_ZONE_PUSH', '跨区隔离同步主题B'),
    (405, 405, 1, 435, 401, 'CROSS_ZONE_PUSH', '跨区隔离同步主题C');

-- ---------------------------------------------------------------------------
-- 流向：三区 ABC → idc305 /data/d/（拼接生成数据D；B/C 为同程序多入口）
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (406, 400, 443, 450, 'FORWARD', TRUE, 'ACTIVE', '消费主题A，参与拼接数据D'),
    (407, 400, 444, 450, 'FORWARD', TRUE, 'ACTIVE', '消费主题B，参与拼接数据D'),
    (408, 400, 445, 450, 'FORWARD', TRUE, 'ACTIVE', '消费主题C，参与拼接数据D');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (406, 406, '拼接路径', TRUE, 0),
    (407, 407, '拼接路径', TRUE, 0),
    (408, 408, '拼接路径', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (406, 406, 1, 435, 402, 'STREAM_JOIN', 'idc305/abc-stitch-d 消费A并写入 /data/d/'),
    (407, 407, 1, 435, 402, 'STREAM_JOIN', 'idc305/abc-stitch-d 消费B并写入 /data/d/'),
    (408, 408, 1, 435, 402, 'STREAM_JOIN', 'idc305/abc-stitch-d 消费C并写入 /data/d/');

-- ---------------------------------------------------------------------------
-- 流向：idc305 /data/d/ --SFTP--> idc306 /data/dd
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (409, 400, 450, 451, 'SHARE', TRUE, 'ACTIVE', 'SFTP：数据D → idc306:/data/dd');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (409, 409, 'SFTP路径', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (409, 409, 1, 435, 403, 'SFTP_PUSH', 'idc305 执行 sftp-d-to-dd，发送到 idc306:/data/dd');

-- ---------------------------------------------------------------------------
-- 流向：idc306 /data/dd → 三区 Kafka 主题F
-- ---------------------------------------------------------------------------
INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (410, 400, 451, 446, 'INGEST', TRUE, 'ACTIVE', '监听 /data/dd 增量，推送主题F');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order) VALUES
    (410, 410, '目录监听推送', TRUE, 0);

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (410, 410, 1, 436, 404, 'DIR_WATCH_PUSH', 'idc306/dd-watch-to-f 监听增量并写入主题F');

ALTER TABLE endpoint ALTER COLUMN id RESTART WITH 500;
ALTER TABLE executor ALTER COLUMN id RESTART WITH 500;
ALTER TABLE data_asset ALTER COLUMN id RESTART WITH 500;
ALTER TABLE flow ALTER COLUMN id RESTART WITH 500;
ALTER TABLE flow_path ALTER COLUMN id RESTART WITH 500;
ALTER TABLE flow_step ALTER COLUMN id RESTART WITH 500;
