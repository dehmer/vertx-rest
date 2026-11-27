package io.bitbang.poc.rest.k6;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import java.util.UUID;


public final class EventBusK6Store implements K6Store {
    private final EventBus eventBus;

    public EventBusK6Store(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Future<UUID> workerStarted() {
        return eventBus.<String>request("k6.started", null)
            .map(msg -> UUID.fromString(msg.body()));
    }

    @Override
    public Future<Integer> workerFinished(UUID runId, String report) {
        JsonObject body = new JsonObject()
            .put("id", runId.toString())
            .put("report", report);
        return eventBus.<Integer>request("k6.finished", body)
            .map(Message::body);
    }

    @Override
    public Future<JsonObject> report(UUID runId) {
        return eventBus.<JsonObject>request("k6.report", runId.toString())
            .map(Message::body);
    }
}