-- 样例：idc301 脚本从 idc302 拉取故障文件到本机目录
-- 源：idc302:/data/origin/gz  →  目标：idc301:/data/sftp/gz
-- 程序：fault-gz-pull（SCRIPT，部署于 idc301）

-- 目录落点（复用 V3 已有主机 idc301=140、idc302=141）
INSERT INTO endpoint (id, type, name, parent_id, zone_id, attrs, status, remark) VALUES
    (300, 'DIRECTORY', '/data/origin/gz', 141, 101,
     '{"dirPath":"/data/origin/gz"}', 'ACTIVE', 'idc302 故障文件源目录'),
    (301, 'DIRECTORY', '/data/sftp/gz', 140, 101,
     '{"dirPath":"/data/sftp/gz"}', 'ACTIVE', 'idc301 故障文件落盘目录');

INSERT INTO executor (id, name, code, kind, default_host_id, status, remark) VALUES
    (300, 'fault-gz-pull', 'fault-gz-pull', 'SCRIPT', 140, 'ACTIVE',
     '在 idc301 执行，从 idc302:/data/origin/gz 下载故障文件到本机 /data/sftp/gz');

INSERT INTO data_asset (id, name, code, data_type, status, remark) VALUES
    (300, 'GZ故障文件拉取', 'ASSET_FAULT_GZ_PULL', 'FILE', 'ACTIVE',
     'idc301 脚本将 idc302 /data/origin/gz 下故障文件下载到 idc301 /data/sftp/gz');

INSERT INTO flow (id, asset_id, source_endpoint_id, target_endpoint_id, purpose, is_primary, status, remark) VALUES
    (300, 300, 300, 301, 'INGEST', TRUE, 'ACTIVE',
     'idc302:/data/origin/gz → idc301:/data/sftp/gz');

INSERT INTO flow_path (id, flow_id, name, enabled, sort_order, remark) VALUES
    (300, 300, 'fault-gz-pull默认路径', TRUE, 0, '脚本部署于 idc301');

INSERT INTO flow_step (id, path_id, seq, host_id, executor_id, method, remark) VALUES
    (300, 300, 1, 140, 300, 'SCRIPT_PULL',
     '在 idc301 运行 fault-gz-pull，拉取 idc302:/data/origin/gz 故障文件到 /data/sftp/gz');

ALTER TABLE endpoint ALTER COLUMN id RESTART WITH 400;
ALTER TABLE executor ALTER COLUMN id RESTART WITH 400;
ALTER TABLE data_asset ALTER COLUMN id RESTART WITH 400;
ALTER TABLE flow ALTER COLUMN id RESTART WITH 400;
ALTER TABLE flow_path ALTER COLUMN id RESTART WITH 400;
ALTER TABLE flow_step ALTER COLUMN id RESTART WITH 400;
