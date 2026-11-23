package io.bitbang.poc.rest;

import io.vertx.ext.web.RoutingContext;

public interface Response {
    public void apply(RoutingContext ctx);
}
