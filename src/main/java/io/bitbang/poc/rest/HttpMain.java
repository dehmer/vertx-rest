package io.bitbang.poc.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

import io.bitbang.poc.rest.k6.*;

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
        int eventLoopPoolSize = options.getEventLoopPoolSize();
        LOGGER.debug("eventLoopPoolSize: " + eventLoopPoolSize);

        Vertx vertx = Vertx.vertx(options);

        // TODO: .setCachePreparedStatements(true)
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost("/tmp")
            .setPort(5432)
            .setDatabase("kanji_nn")
            .setUser("postgres");

        // Pool options
        // TODO: set max size to `eventLoopPoolSize`
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(12);

        // Create the client pool (actually a pooled client):
        ClientBuilder<SqlClient> clientBuilder = PgBuilder.client();
        SqlClient client = clientBuilder
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx) // re-use Http event loop for SQL
                .build();


        vertx.deployVerticle(new K6StateVerticle())
            .onSuccess(instanceId -> {
                K6Store store = new EventBusK6Store(vertx.eventBus());

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


                // Workers request/join a shared run ID.
                // $ curl -X POST http://localhost:8080/k6-started
                // router.post("/k6-started").handler(ctx -> {
                //     // TODO: update state with new k6 instance; return run ID
                //     String bogusId = UUID.randomUUID().toString();
                //     LOGGER.info("[/k6-started] " + bogusId);
                //     HttpServerResponse response = ctx.response();
                //     response.putHeader("content-type", "text/plain");
                //     response.end(bogusId);
                // });

                router.post("/k6-started").handler(ctx ->
                    store.workerStarted()
                        .onSuccess(id -> ctx.response()
                            .putHeader("content-type", "text/plain")
                            .end(id.toString())
                        )
                        .onFailure(ctx::fail)
                );


                /*
                    Workers upload their JSON summary report.
                    $ curl -X POST http://localhost:8080/k6-finished/2c21bd5e-6dcb-40dd-9d31-52f6a130cd74 \
                        -H "Content-Type: application/json" \
                        -d '{}'
                */
                // router.post("/k6-finished/:id").handler(ctx -> {
                //     // TODO: update state with new report; return pending k6 instance count

                //     String id = ctx.request().getParam("id");
                //     ctx.request().bodyHandler(buffer -> {
                //         String report = buffer.toString();
                //         LOGGER.info("k6-finished: " + id);
                //         LOGGER.info("k6-finished: " + report);
                //         ctx.response()
                //             .end();
                //     });
                // });

                router.post("/k6-finished/:id").handler(ctx -> {
                    UUID id = UUID.fromString(ctx.request().getParam("id"));
                    ctx.request().bodyHandler(buffer -> {
                        store.workerFinished(id, buffer.toString())
                            .onSuccess(pending -> {
                                LOGGER.info("[/k6-finished/:id] pending=" + pending);
                                ctx.response().end(String.valueOf(pending));
                            })
                            .onFailure(ctx::fail);
                    }

                    );
                });

                /*
                    Fetch merged results for a given run ID.
                    Either called by the "last" worker or by each worker for intermediate result.
                    $ curl http://localhost:8080/k6-report/2c21bd5e-6dcb-40dd-9d31-52f6a130cd74
                */
                // router.get("/k6-report/:id").handler(ctx -> {
                //     String id = ctx.request().getParam("id");
                //     // TODO: merge reports to single report and return as JSON
                //     JsonObject aggregate = new JsonObject();
                //     ctx.json(aggregate);
                // });
                router.get("/k6-report/:id").handler(ctx -> {
                    UUID id = UUID.fromString(ctx.request().getParam("id"));
                    store.report(id)
                        .onSuccess(ctx::json)
                        .onFailure(ctx::fail);
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

            })
            .onFailure(err -> LOGGER.error("Could not deploy state verticle", err));

    }
}
