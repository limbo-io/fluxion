/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.remote.core.api.dto;

/**
 * 系统信息
 *
 * @author PengQ
 * @since 0.0.1
 */
public class SystemInfoDTO {

    /**
     * CPU processor num
     */
    private int cpuProcessors;

    /**
     * CPU Load
     */
    private double cpuLoad;

    /**
     * free memory
     */
    private long freeMemory;

    public int getCpuProcessors() {
        return cpuProcessors;
    }

    public void setCpuProcessors(int cpuProcessors) {
        this.cpuProcessors = cpuProcessors;
    }

    public double getCpuLoad() {
        return cpuLoad;
    }

    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public void setFreeMemory(long freeMemory) {
        this.freeMemory = freeMemory;
    }
}
