/*
 *
 *  * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
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


import io.fluxion.remote.core.server.RpcServer;
import io.fluxion.remote.core.server.RpcServerConfig;
import io.fluxion.remote.core.server.RpcServerStatus;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Devil
 * @since 2023/8/10
 */
public class NettyHttpRpcServer implements RpcServer {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpRpcServer.class);

    private Thread thread;

    private static final int MAX_CONTENT_LENGTH = 5 * 1024 * 1024;

    private static final int QUEUE_SIZE = 2000;

    private final AtomicReference<RpcServerStatus> status = new AtomicReference<>(RpcServerStatus.IDLE);

    @Override
    public boolean start(RpcServerConfig config) {
        if (!status.compareAndSet(RpcServerStatus.IDLE, RpcServerStatus.INITIALIZING)) {
            return false;
        }
        int port = config.getPort();
        thread = new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            ThreadPoolExecutor serverThreadPool = new ThreadPoolExecutor(
                0,
                200,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                r -> new Thread(r, "Fluxion-RpcServer-ThreadPool-" + r.hashCode()),
                (r, executor) -> {
                    throw new RuntimeException("Http Server ThreadPool is EXHAUSTED!");
                });


            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                .addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS))
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                                .addLast(new EmbedHttpServerHandler(serverThreadPool, config.getHandleProcessor()));
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture future = bootstrap.bind(port).sync();

                status.compareAndSet(RpcServerStatus.INITIALIZING, RpcServerStatus.RUNNING);
                log.info("Fluxion-RpcServer start success, port = {}", port);

                // 绑定监听关闭状态 -- 阻塞
                future.channel().closeFuture().sync();

            } catch (Exception e) {
                log.error("Fluxion-RpcServer start error", e);
                throw new RuntimeException(e);
            } finally {
                status.compareAndSet(RpcServerStatus.RUNNING, RpcServerStatus.TERMINATING);
                // stop
                try {
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                } catch (Exception e) {
                    log.error("Fluxion-RpcServer stop fail", e);
                }
                status.compareAndSet(RpcServerStatus.RUNNING, RpcServerStatus.TERMINATED);
            }

        });
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public boolean stop() {
        // 先修改状态，防止重复进入
        if (!status.compareAndSet(RpcServerStatus.RUNNING, RpcServerStatus.TERMINATING)) {
            return false;
        }

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        return true;
    }

    @Override
    public RpcServerStatus status() {
        return status.get();
    }

}
