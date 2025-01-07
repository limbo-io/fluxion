/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

package io.fluxion.remote.api;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Brozen
 * @since 2021-06-16
 */
@Getter
@NoArgsConstructor
public class Response<T> {

    public static final String SUCCESS_MSG = "success";

    /**
     * 响应状态码，参考{@link HttpStatus}中状态码的定义
     */
    private int code;

    /**
     * 错误提示信息，可选项
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    Response(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }


    /**
     * 获取一个响应类Builder
     *
     * @param <T> 响应类封装的数据类型
     * @return 响应类Builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }


    /**
     * 获取一个响应类，并设置响应状态码{@link HttpStatus#OK}，填充数据为入参 data
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     */
    public static <T> Response<T> ok(T data) {
        return new Response<>(HttpStatus.OK, SUCCESS_MSG, data);
    }

    /**
     * 获取一个响应类，并设置响应状态码{@link HttpStatus#OK}
     *
     * @param <T> 响应数据类型
     */
    public static <T> Response<T> ok() {
        return new Response<>(HttpStatus.OK, SUCCESS_MSG, null);
    }


    /**
     * 请求是否成功
     *
     * @return 是否成功
     */
    public boolean success() {
        return this.code == HttpStatus.OK;
    }


    /**
     * 响应类Response的Builder
     */
    public static class Builder<T> {

        private int code;

        private String message;

        private T data;

        /**
         * 根据Builder的配置生成响应结果
         *
         * @return 响应
         */
        public Response<T> build() {
            return new Response<>(code, message, data);
        }

        /**
         * 设置响应状态码{@link HttpStatus#BAD_REQUEST}，并设置提示信息
         *
         * @param message 错误提示信息
         * @return 链式调用
         */
        public Builder<T> badRequest(String message) {
            this.code = HttpStatus.BAD_REQUEST;
            this.message = message;
            return this;
        }

        /**
         * 设置响应状态码{@link HttpStatus#NOT_FOUND}，并设置提示信息
         *
         * @param message 错误提示信息
         * @return 链式调用
         */
        public Builder<T> notFound(String message) {
            this.code = HttpStatus.NOT_FOUND;
            this.message = message;
            return this;
        }

        /**
         * 设置响应状态码{@link HttpStatus#UNAUTHORIZED} 未认证，未登录，并设置提示信息
         *
         * @return 链式调用
         */
        public Builder<T> unauthorized() {
            this.code = HttpStatus.UNAUTHORIZED;
            return this;
        }

        /**
         * 设置响应状态码{@link HttpStatus#UNAUTHORIZED} 未认证，未登录，并设置提示信息
         *
         * @param message 错误提示信息
         * @return 链式调用
         */
        public Builder<T> unauthorized(String message) {
            this.code = HttpStatus.UNAUTHORIZED;
            this.message = message;
            return this;
        }


        /**
         * 设置响应状态码{@link HttpStatus#FORBIDDEN}
         *
         * @return 链式调用
         */
        public Builder<T> forbidden() {
            this.code = HttpStatus.FORBIDDEN;
            return this;
        }

        /**
         * 设置响应状态码{@link HttpStatus#FORBIDDEN} 未授权，无权限，并设置提示信息
         *
         * @param message 错误提示信息
         * @return 链式调用
         */
        public Builder<T> forbidden(String message) {
            this.code = HttpStatus.FORBIDDEN;
            this.message = message;
            return this;
        }


        /**
         * 设置响应状态码{@link HttpStatus#INTERNAL_SERVER_ERROR}
         *
         * @param message 错误提示信息
         * @return 链式调用
         */
        public Builder<T> error(String message) {
            this.code = HttpStatus.INTERNAL_SERVER_ERROR;
            this.message = message;
            return this;
        }

        /**
         * 设置错误码和错误信息
         *
         * @param code    错误码
         * @param message 错误信息
         * @return 链式调用
         */
        public Builder<T> error(int code, String message) {
            this.code = code;
            this.message = message;
            return this;
        }

        /**
         * 设置响应中的提示信息
         *
         * @param message 提示信息
         * @return 链式调用
         */
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 设置响应中的数据
         *
         * @param data 响应数据
         * @return 链式调用
         */
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

    }

}
