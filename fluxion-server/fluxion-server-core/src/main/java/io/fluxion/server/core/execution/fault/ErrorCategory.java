package io.fluxion.server.core.execution.fault;

import lombok.Getter;

/**
 * 错误分类
 */
@Getter
public enum ErrorCategory {

    TRANSIENT(true),    // 临时错误，可重试（网络抖动、超时）
    BUSINESS(false),    // 业务错误，不重试
    FATAL(false),       // 致命错误，需人工介入
    UNKNOWN(true);      // 未知错误，按配置处理

    private final boolean retryable;

    ErrorCategory(boolean retryable) {
        this.retryable = retryable;
    }
}