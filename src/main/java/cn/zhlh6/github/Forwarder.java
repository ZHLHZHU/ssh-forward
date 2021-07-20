package cn.zhlh6.github;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;

class Forwarder extends ChannelInboundHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(Forwarder.class);

    private Channel inboundChannel;

    private Channel outboundChannel;

    private final NioEventLoopGroup worker;

    private static final String UPSTREAM_HOST = "github.com";

    private static final int UPSTREAM_PORT = 22;

    private static final String IS_SSH_FLAG = "isSSH";

    private static final byte[] CHECK_TRAIT = new byte[]{0x53, 0x53, 0x48};

    public Forwarder(NioEventLoopGroup worker) {
        this.worker = worker;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        inboundChannel = ctx.channel();
        final io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.option(ChannelOption.AUTO_READ, false);
        b.group(worker).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                             @Override
                             protected void initChannel(SocketChannel ch) {
                                 ch.pipeline().addFirst(new ChannelTrafficShapingHandler(
                                         Configuration.UPSTREAM_SPEED_LIMIT,
                                         Configuration.UPSTREAM_SPEED_LIMIT)
                                 );
                                 ch.pipeline().addLast(new Forwarder.Upstream());
                             }
                         }
                );
        b.connect(UPSTREAM_HOST, UPSTREAM_PORT).addListener(future -> {
            if (future.isSuccess()) {
                inboundChannel.read();
            } else {
                inboundChannel.close();
            }
        });
        final InetSocketAddress address = (InetSocketAddress) inboundChannel.remoteAddress();
        log.info("request from [{}]:{}", address.getHostName(), address.getPort());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.info("read from client");
        final AttributeKey<Boolean> attributeKey = AttributeKey.valueOf(IS_SSH_FLAG);
        if (ctx.channel().attr(attributeKey).get() == null) {
            ByteBuf byteBuf = (ByteBuf) msg;
            if (!byteBuf.isReadable(CHECK_TRAIT.length)) {
                closeAndFlush(ctx.channel());
                return;
            }
            byte[] headBytes = new byte[CHECK_TRAIT.length];
            byteBuf.getBytes(0, headBytes);
            if (!Arrays.equals(headBytes, CHECK_TRAIT)) {
                closeAndFlush(ctx.channel());
                log.warn("receive unknown protocol data,first {} bytes: [{}]", CHECK_TRAIT.length, Arrays.toString(headBytes));
                return;
            }
            ctx.channel().attr(attributeKey).set(true);
        }
        if (!outboundChannel.isActive()) {
            return;
        }
        outboundChannel.writeAndFlush(msg).addListener(future -> {
            log.warn("read complete");
            if (future.isSuccess()) {
                log.warn("read...");
                inboundChannel.read();
            } else {
                log.warn("close");
                outboundChannel.close();
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeAndFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        outboundChannel.close();
        closeAndFlush(ctx.channel());
        log.error("error:", cause);
    }


    private void closeAndFlush(Channel channel) {
        if (channel.isActive()) {
            channel.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    class Upstream extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            outboundChannel = ctx.channel();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            log.info("fetch data from upstream");
            inboundChannel.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess()) {
                    outboundChannel.read();
                } else {
                    outboundChannel.close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeAndFlush(inboundChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            closeAndFlush(ctx.channel());
            log.error("error:", cause);
        }
    }
}

