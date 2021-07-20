package cn.zhlh6.github;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Forwarder extends ChannelInboundHandlerAdapter {

    Logger log = LoggerFactory.getLogger(Forwarder.class);

    Channel inboundChannel;

    Channel outboundChannel;

    final NioEventLoopGroup worker;

    private static final String UPSTREAM_HOST = "github.com";

    private static final int UPSTREAM_PORT = 22;

    private static final String IS_SSH_FLAG = "isSSH";

    private static final byte[] CHECK_TRAIT = new byte[]{0x53, 0x53, 0x48};

    public Forwarder(NioEventLoopGroup worker) {
        this.worker = worker;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws InterruptedException {
        inboundChannel = ctx.channel();
        final io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.group(worker).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                             @Override
                             protected void initChannel(SocketChannel ch) {
                                 ch.pipeline().addFirst(new ChannelTrafficShapingHandler(
                                         Configuration.UPSTREAM_SPEED_LIMIT,
                                         Configuration.UPSTREAM_SPEED_LIMIT)
                                 );
                                 ch.pipeline().addLast(new Upstream());
                             }
                         }
                );
        b.connect(UPSTREAM_HOST, UPSTREAM_PORT).sync();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        final AttributeKey<Boolean> attributeKey = AttributeKey.valueOf(IS_SSH_FLAG);
        if (ctx.channel().attr(attributeKey).get() == null) {
            final ByteBuf byteBuf = (ByteBuf) msg;
            if (!byteBuf.isReadable(CHECK_TRAIT.length)) {
                ctx.channel().close();
                return;
            }
            byte[] headBytes = new byte[CHECK_TRAIT.length];
            byteBuf.getBytes(0, headBytes);
            if (!Arrays.equals(headBytes, CHECK_TRAIT)) {
                ctx.channel().close();
                log.warn("receive unknown protocol data,first {} bytes: [{}]", CHECK_TRAIT.length, Arrays.toString(headBytes));
                return;
            }
            ctx.channel().attr(attributeKey).set(true);
        }
        //todo NPE error
        if (outboundChannel == null) {
            // temporary deal
            ctx.channel().close().sync();
            return;
        }
        outboundChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        log.error("error:", cause);
    }

    class Upstream extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            outboundChannel = ctx.channel();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            inboundChannel.writeAndFlush(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            log.error("error:", cause);
        }
    }

}
