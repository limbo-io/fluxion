package io.fluxion.worker.core;

import io.fluxion.worker.core.executor.Executor;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author PengQ
 * @since 0.0.1
 */
public class WorkerInfo {

    private String workerId;

    private String appName;
    /**
     * 连接 worker 的 url
     */
    private URL url;

    /**
     * 执行器
     */
    private Map<String, Executor> executors;

    /**
     * Worker 标签
     */
    private Map<String, Set<String>> tags;

    public WorkerInfo(String appName, URL url, List<Executor> executors, Map<String, Set<String>> tags) {
        this.appName = appName;
        this.url = url;
        this.executors = executors == null ? Collections.emptyMap() : executors.stream().collect(Collectors.toMap(Executor::name, executor -> executor));
        this.tags = tags == null ? Collections.emptyMap() : tags;
    }

    public Map<String, Set<String>> getTags() {
        return tags == null ? Collections.emptyMap() : tags;
    }

    public String getAppName() {
        return appName;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public List<Executor> getExecutors() {
        return new ArrayList<>(executors.values());
    }

    public Executor getExecutor(String name) {
        return executors.get(name);
    }

}
