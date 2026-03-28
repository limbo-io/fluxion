-- H2 数据库初始化脚本 - Fluxion 核心表结构

-- 执行计划表 (Execution)
CREATE TABLE IF NOT EXISTS fluxion_execution (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    trigger_id VARCHAR(64),
    workflow_id VARCHAR(64),
    executor_id VARCHAR(64),
    trigger_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_times INT DEFAULT 0,
    max_retry_times INT DEFAULT 0,
    retry_interval INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务实例表 (Job)
CREATE TABLE IF NOT EXISTS fluxion_job (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    worker_address VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    retry_times INT DEFAULT 0,
    trigger_at TIMESTAMP,
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    error_msg TEXT,
    result TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 调度计划表 (Schedule)
CREATE TABLE IF NOT EXISTS fluxion_schedule (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    trigger_id VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    schedule_option TEXT,
    executable_type VARCHAR(32) NOT NULL,
    executable_info TEXT,
    trigger_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 延迟调度表 (Schedule Delay)
CREATE TABLE IF NOT EXISTS fluxion_schedule_delay (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    trigger_id VARCHAR(64) NOT NULL,
    trigger_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Worker 节点表
CREATE TABLE IF NOT EXISTS fluxion_worker (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    host VARCHAR(64) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_heartbeat_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 应用表
CREATE TABLE IF NOT EXISTS fluxion_app (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 触发器表
CREATE TABLE IF NOT EXISTS fluxion_trigger (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    trigger_type VARCHAR(32) NOT NULL,
    schedule_option TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 工作流表
CREATE TABLE IF NOT EXISTS fluxion_workflow (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    config TEXT,
    version INT DEFAULT 1,
    draft_version INT DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Executor 表
CREATE TABLE IF NOT EXISTS fluxion_executor (
    id VARCHAR(64) PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    config TEXT,
    version INT DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_execution_status ON fluxion_execution(status);
CREATE INDEX IF NOT EXISTS idx_execution_trigger_at ON fluxion_execution(trigger_at);
CREATE INDEX IF NOT EXISTS idx_job_execution_id ON fluxion_job(execution_id);
CREATE INDEX IF NOT EXISTS idx_job_status ON fluxion_job(status);
CREATE INDEX IF NOT EXISTS idx_schedule_trigger_at ON fluxion_schedule(trigger_at);
CREATE INDEX IF NOT EXISTS idx_schedule_delay_trigger_at ON fluxion_schedule_delay(trigger_at);
CREATE INDEX IF NOT EXISTS idx_worker_app_id ON fluxion_worker(app_id);
CREATE INDEX IF NOT EXISTS idx_worker_status ON fluxion_worker(status);
