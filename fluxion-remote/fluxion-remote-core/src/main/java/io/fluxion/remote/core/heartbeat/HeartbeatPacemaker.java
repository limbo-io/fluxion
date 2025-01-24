/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.remote.core.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 定时发送心跳
 *
 * @author Devil
 * @since 2023/8/4
 */
public class HeartbeatPacemaker {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPacemaker.class);

    /**
     * 工作节点
     */
    private final Heartbeat heartbeat;

    /**
     * 心跳任务调度器
     */
    private final Timer heartbeatScheduler;

    /**
     * Worker 心跳发送定时任务
     */
    private TimerTask heartbeatTask;

    /**
     * 心跳周期
     */
    private final Duration period;

    /**
     * 是否运行中
     */
    private boolean running;


    public HeartbeatPacemaker(Heartbeat heartbeat, Duration period) {
        this.heartbeat = heartbeat;
        this.heartbeatScheduler = new Timer("HeartbeatTask");
        this.period = period;
        this.running = false;
    }


    /**
     * 启动心跳
     */
    public synchronized void start() {
        if (this.running) {
            return;
        }

        // 已经有任务存在，停止
        if (this.heartbeatTask != null) {
            this.heartbeatTask.cancel();
        }

        // 重新生成心跳任务
        this.heartbeatTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // So beat it
                    heartbeat.beat();
                } catch (Exception e) {
                    log.error("[heartbeatTask] error", e);
                }
            }
        };

        // 启动心跳任务
        this.heartbeatScheduler.schedule(this.heartbeatTask, 0, this.period.toMillis());
        this.running = true;
    }


    /**
     * 停止心跳任务
     */
    public synchronized void stop() {
        if (!this.running) {
            return;
        }

        TimerTask prevTask = this.heartbeatTask;
        this.heartbeatTask = null;

        if (prevTask != null) {
            prevTask.cancel();
        }

        this.running = false;
    }

}
