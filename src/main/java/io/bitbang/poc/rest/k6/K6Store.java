package io.bitbang.poc.rest.k6;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public interface K6Store {
    Future<UUID> workerStarted();
    Future<Integer> workerFinished(UUID runId, String report);
    Future<JsonObject> report(UUID runId);
}
