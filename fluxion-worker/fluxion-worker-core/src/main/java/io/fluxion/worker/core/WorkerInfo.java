package io.fluxion.worker.core;

import io.fluxion.worker.core.executor.Executor;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import java.util.Collections;
import java.util.List;

/**
* @author PengQ 
* @since 0.0.1
*/public class WorkerInfo {

    private String appName;

    private String workerId;

    /**
     * 执行器
     */
    private List<Executor> executors;

    /**
     * Worker 标签
     */
    private MultiValuedMap<String, String> tags;

    public MultiValuedMap<String, String> getTags() {
        return tags == null ? MultiMapUtils.emptyMultiValuedMap() : tags;
    }

    public void setTags(MultiValuedMap<String, String> tags) {
        this.tags = tags;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public List<Executor> getExecutors() {
        return executors == null ? Collections.emptyList() : executors;
    }

    public void setExecutors(List<Executor> executors) {
        this.executors = executors;
    }
}
