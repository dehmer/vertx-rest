package io.bitbang.poc.rest;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public record TextResponse (
    int statusCode,
    String body
) implements Response {

    public static Response notFound() {
        return new TextResponse(404, null);
    }

    public static Response internalError(String body) {
        return new TextResponse(500, body);
    }

    public void apply(RoutingContext ctx) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(statusCode());
        if (body() != null) response.end(body());
        else response.end();
    }
}
