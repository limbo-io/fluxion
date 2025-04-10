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

package io.fluxion.remote.netty;

import io.fluxion.common.thread.NamedThreadFactory;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.Response;
import io.fluxion.remote.core.client.server.ClientHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(EmbedHttpServerHandler.class);

    private static final int QUEUE_SIZE = 2000;

    private final ThreadPoolExecutor serverThreadPool;

    private final ClientHandler clientHandler;

    public EmbedHttpServerHandler(ClientHandler clientHandler) {
        this.serverThreadPool = new ThreadPoolExecutor(
            0,
            200,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_SIZE),
            NamedThreadFactory.newInstance("Fluxion-RpcServer-ThreadPool-"),
            (r, executor) -> {
                throw new RuntimeException("Http Server ThreadPool is Exhausted!");
            });
        this.clientHandler = clientHandler;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, FullHttpRequest msg) {
        // 最开始放到线程池中获取，导致异步执行前资源释放导致获取的时候抛出异常
        String requestData = msg.content().toString(CharsetUtil.UTF_8);
        String uri = msg.uri();
        HttpMethod httpMethod = msg.method();
        boolean keepAlive = HttpUtil.isKeepAlive(msg);
        serverThreadPool.submit(() -> {
            try {
                Response<?> response = clientHandler.process(uri, requestData);
                returnResponse(ctx, uri, keepAlive, JacksonUtils.toJSONString(response));
            } catch (Exception e) {
                log.error("Get Request Error method={} url={} param={}", httpMethod, uri, requestData, e);
                throw new RuntimeException(e);
            }
        });
    }

    private void returnResponse(ChannelHandlerContext ctx, String uri, boolean keepAlive, String responseJson) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.writeAndFlush(response);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("server caught exception", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.channel().close();
            log.debug("server close an idle channel.");
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
