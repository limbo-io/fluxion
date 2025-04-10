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


import io.fluxion.remote.core.client.server.AbstractClientServer;
import io.fluxion.remote.core.client.server.ClientServerConfig;
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

import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 * @since 2023/8/10
 */
public class NettyHttpClientServer extends AbstractClientServer {

    private Thread thread;

    private static final int MAX_CONTENT_LENGTH = 5 * 1024 * 1024;

    public NettyHttpClientServer(ClientServerConfig config) {
        super(config);
    }

    @Override
    public void start() {
        int port = config.port();
        thread = new Thread(() -> {
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup();
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
                                .addLast(new EmbedHttpServerHandler(config.clientHandler()));
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture serverChannel = bootstrap.bind(port).sync();

                log.info("Fluxion-RpcServer start success, port = {}", port);

                // 绑定监听关闭状态 -- 阻塞
                serverChannel.channel().closeFuture().sync();

            } catch (Exception e) {
                log.error("Fluxion-RpcServer start error", e);
                throw new RuntimeException(e);
            } finally {
                // stop
                try {
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                } catch (Exception e) {
                    log.error("Fluxion-RpcServer stop fail", e);
                }
            }

        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

}
