/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.worker.spring.starter.properties;

public class DatasourceProperties {
    /**
     * 是否开启数据库存储
     */
    private boolean open = true;
    /**
     * 是否初始化数据库 如果是持久化 task 的则选 false 交由运维管理
     */
    private boolean initTable = true;

    private String url = "jdbc:h2:./fluxion_worker;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=mysql;";
    private String username;
    private String password;

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isInitTable() {
        return initTable;
    }

    public void setInitTable(boolean initTable) {
        this.initTable = initTable;
    }
}