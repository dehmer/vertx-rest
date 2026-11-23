package io.bitbang.poc.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


class HttpMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMain.class);
    private static final List<String> IDS = List.of(
        "7593b292-206e-47f8-a240-6cfc4d7578ac",
        "f1f9aa19-df63-488e-8d89-4eccab83d0bc",
        "1bca0ab0-f592-469b-a275-bc1b5529b3a5",
        "ffa07efb-14cb-4c29-b586-c34cce7b2f70",
        "220d5c86-8150-4a22-9994-9f2cef9dc69c",
        "a8fd3b47-44a8-4e10-97e9-35812a9c8c62",
        "29afad4f-8800-45fc-8824-fa55c7cffab1",
        "d29721d1-08ec-46f6-9a56-4b4d6a6a94b9",
        "a6adf0c6-374b-42fe-ac0f-71580d08c3de",
        "828561db-9eb3-4aea-b5c8-5236de365141",
        "6fbd10ac-4c18-4750-a360-c139f1d00aee",
        "71e910c7-e90c-49d6-9659-41525f304e22",
        "94f36b60-22a7-4d49-ae6f-c774740fd4bf",
        "c583e3f0-e6bc-453a-b920-76914e60e13d",
        "711b2153-57cb-4b1e-8a06-48ebc8727b7e",
        "381b099b-f5be-4021-b747-395128b827bd",
        "10e5e97a-1135-42d7-b764-9ead5b2f60ac",
        "b02236f6-13e1-49ec-86f5-8bea372aee6d",
        "ff1c7dbe-7d82-4b05-a821-abd8e0cc7a26",
        "31f9dda5-350a-4511-a832-c36f486653b2"
    );

    private static final List<String> LITERALS = List.of(
        "谷", "今", "学", "月", "歳", "虫", "台"
    );

    private static String randomId() {
        return IDS.get(ThreadLocalRandom.current().nextInt(IDS.size()));
    }

    private static String randomLiteral() {
        return LITERALS.get(ThreadLocalRandom.current().nextInt(LITERALS.size()));
    }

    static void main() {

        // defaults:
        // * eventLoopPoolSize = 24 (Mac mini M2 Pro)
        VertxOptions options = new VertxOptions();

        Vertx vertx = Vertx.vertx(options);

        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost("/tmp")
            .setPort(5432)
            .setDatabase("kanji_nn")
            .setUser("postgres");

        // Pool options
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(12);

        // Create the client pool (actually a pooled client):
        ClientBuilder<SqlClient> clientBuilder = PgBuilder.client();
        SqlClient client = clientBuilder
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx) // re-use Http event loop for SQL
                .build();

        Router router = Router.router(vertx);

        /**
         * Get random Kanji/Kana glyph.
         */
        router.get("/glyph").handler(ctx -> {
            // TODO: Pooled client does not really benefit from prepared statement
            PreparedQuery<RowSet<Row>> query = client.preparedQuery("SELECT * FROM glyph WHERE id=$1");
            Future<RowSet<Row>> pending = query.execute(Tuple.of(randomId()));
            Future<Response> response = pending
                .map(rows -> rows.size() == 0
                        ? TextResponse.notFound()
                        : JsonResponse.ok(rows.iterator().next().toJson())
                )
                .recover(err -> Future.succeededFuture(
                        TextResponse.internalError(err.getMessage())
                ));

            response.onSuccess(result -> result.apply(ctx));
        });

        router.get("/literal").handler(ctx -> {
            String sql = "SELECT * FROM glyph WHERE dataset = 'ETL9B' AND literal=$1";
            PreparedQuery<RowSet<Row>> query = client.preparedQuery(sql);
            Future<RowSet<Row>> pending = query.execute(Tuple.of(randomLiteral()));
            // TODO: stream result as TSV
        });

        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router);

        httpServer
            .listen(8080, "0.0.0.0")
            .onSuccess(server ->
                    LOGGER.info("TCP server listening on port {}",
                            server.actualPort()))
            .onFailure(error -> {
                LOGGER.error("Could not start TCP server", error);
                vertx.close();
            });
    }
}
