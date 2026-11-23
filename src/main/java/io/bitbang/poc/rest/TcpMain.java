package io.bitbang.poc.rest;

import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Vertx;

import java.util.function.Function;


class TcpMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpMain.class);

    static void main() {
        // defaults:
        // eventLoopPoolSize = 24
        VertxOptions options = new VertxOptions();
        Vertx vertx = Vertx.vertx(options);

        NetServer netServer = vertx.createNetServer();

        Function<NetSocket, Handler<Buffer>> echo =
                socket -> buffer -> {
                    // String data = buffer.toString(StandardCharsets.UTF_8);
                    // LOGGER.info("Received {} bytes: {}", buffer.length(), data);
                    socket.write(buffer);
                };

        Handler<NetSocket> connectHandler = (NetSocket socket) -> {
            LOGGER.info("Client connected: {} on {}",
                    socket.remoteAddress(),
                    Thread.currentThread().getName());

            socket.handler(echo.apply(socket));
            socket.closeHandler(v -> LOGGER.info("Client disconnected: {}", socket.remoteAddress()));
            socket.exceptionHandler(error -> LOGGER.warn("Socket error", error));
        };

        netServer
                .connectHandler(connectHandler)
                .listen(9000, "127.0.0.1")
                .onSuccess(server ->
                        LOGGER.info("TCP server listening on port {}",
                                server.actualPort()))
                .onFailure(error -> {
                    LOGGER.error("Could not start TCP server", error);
                    vertx.close();
                });
    }
}
