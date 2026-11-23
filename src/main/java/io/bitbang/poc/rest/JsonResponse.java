package io.bitbang.poc.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public record JsonResponse(
        JsonObject body
) implements Response {

    public static Response ok(JsonObject body) {
        return new JsonResponse(body);
    }

    public void apply(RoutingContext ctx) {
        ctx.json(body());
    }
}