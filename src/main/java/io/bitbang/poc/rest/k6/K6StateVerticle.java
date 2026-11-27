package io.bitbang.poc.rest.k6;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/** Owns the one and only K6State. Deploy exactly ONE instance. */
public class K6StateVerticle extends VerticleBase {

    // Only ever touched from this verticle's event loop thread: no locks needed.
    private K6State state = K6State.empty();

    @Override
    public Future<?> start() {
        // vertx :: VerticleBase.vertx
        var eventBus = vertx.eventBus();

        eventBus.consumer("k6.started", msg -> {
            Transition<UUID> t = K6Core.workerStarted(state, UUID.randomUUID());
            state = t.state();
            msg.reply(t.result().toString());
        });

        eventBus.<JsonObject>consumer("k6.finished", msg -> {
            JsonObject body = msg.body();
            UUID runId = UUID.fromString(body.getString("id"));
            Transition<Integer> t = K6Core.workerFinished(state, runId, body.getString("report"));
            state = t.state();
            msg.reply(t.result());
        });

        eventBus.<String>consumer("k6.report", msg -> {
            UUID runId = UUID.fromString(msg.body());
            JsonObject merged = K6Merge.merge(state.run(runId).orElseThrow().reports());
            msg.reply(merged);
        });

        return Future.succeededFuture();
    }
}