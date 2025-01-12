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

package io.fluxion.server.start.config;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.server.infrastructure.exception.PlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.stream.Collectors;

/**
 * @author Brozen
 * @since 2021-07-05
 */
@Slf4j
@RestControllerAdvice
public class ControllerResponseHandler implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof String) {
            // Spring 默认使用 StringHttpMessageConverter 处理
            return JacksonUtils.toJSONString(Response.ok(body));
        } else if (body instanceof Response) {
            return body;
        }
        return Response.ok(body);
    }

    /**
     * 所有未处理的异常最终执行分支
     */
    @ExceptionHandler(value = Exception.class)
    public Response<?> handleException(Exception e) {
        log.error("error", e);
        return Response.builder().error(e.getMessage()).build();
    }


    /**
     * 参数绑定异常，JSR303 校验失败时可能会抛出此异常
     */
    @ExceptionHandler(value = BindException.class)
    public Response<?> handleBindException(BindException e) {
        BindingResult result = e.getBindingResult();
        if (!result.hasErrors()) {
            log.error("Arguments bind error", e);
            return Response.builder().badRequest(e.getMessage()).build();
        }

        String msg = result.getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .collect(Collectors.joining("\n"));

        return Response.builder().badRequest("Arguments error: \n" + msg).build();
    }


    /**
     * 处理 JSR303 参数校验错误，WebFlux 框架下。
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Response<?> handleValidateFailedException(WebExchangeBindException e) {
        // 拼装错误信息
        StringBuilder errMsg = new StringBuilder();
        e.getFieldErrors().forEach(fe -> {
            errMsg.append(fe.getField()).append(" ")
                .append(fe.getDefaultMessage()).append("; ");
        });

        return Response.builder().badRequest(errMsg.toString()).build();
    }

    /**
     * 处理平台异常
     */
    @ExceptionHandler(PlatformException.class)
    public Response<?> handlePlatformException(PlatformException e) {
        log.debug("Has PlatformException", e);
        return Response.builder().badRequest(e.getMessage()).build();
    }
}
