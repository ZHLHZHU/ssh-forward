package cn.zhlh6.github;

import io.github.cdimascio.dotenv.Dotenv;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * create 2021/7/16 22:22
 *
 * @author LH
 */
public class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private static ChannelFuture init() throws InterruptedException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        //init logger
        final URL logConfigURL = Bootstrap.class.getClassLoader().getResource("config/log4j.xml");
        DOMConfigurator.configure(logConfigURL);
        log.info("SSH-Forward start up...");

        //load dotenv file to system environment
        Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .systemProperties()
                .load();
        Configuration.load();

        //init server
        final NioEventLoopGroup boss = new NioEventLoopGroup(1);
        final NioEventLoopGroup worker = new NioEventLoopGroup();
        final ServerBootstrap booster = new ServerBootstrap();
        final NioEventLoopGroup clientWorker = new NioEventLoopGroup();
        booster.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addFirst(new ChannelTrafficShapingHandler(Configuration.CLIENT_SPEED_LIMIT, Configuration.CLIENT_SPEED_LIMIT));
                        socketChannel.pipeline().addLast(new Forwarder(worker));

                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.AUTO_READ, false);

        //listen stop signal
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("server shutdown...");
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }));
        //listen port
        log.info("server listening on [{}]:{}", Configuration.LISTEN_HOST, Configuration.LISTEN_PORT);
        final ChannelFuture future = booster.bind(Configuration.LISTEN_HOST, Configuration.LISTEN_PORT).sync();
        return future;
    }

    public static void main(String[] args) {
        try {
            ChannelFuture future = init();
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("error:", e);
        }
    }
}