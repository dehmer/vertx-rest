package io.bitbang.poc.rest.k6;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/** Pure: no I/O, no state. */
final class K6Merge {

    private K6Merge() {}

    private static final List<String> PER_INSTANCE_STATS =
        List.of("med", "p(90)", "p(95)");

    static JsonObject merge(List<String> reports) {
        List<JsonObject> summaries = reports.stream().map(JsonObject::new).toList();
        List<JsonObject> reqs = summaries.stream().map(s -> values(s, "http_reqs")).toList();
        List<JsonObject> durs = summaries.stream().map(s -> values(s, "http_req_duration")).toList();

        System.out.println(summaries);

        double totalCount = 0, totalRate = 0, weightedAvg = 0;
        double min = Double.MAX_VALUE, max = 0;
        for (int i = 0; i < summaries.size(); i++) {
            double count = reqs.get(i).getDouble("count");
            totalCount += count;
            totalRate += reqs.get(i).getDouble("rate");
            weightedAvg += durs.get(i).getDouble("avg") * count;
            min = Math.min(min, durs.get(i).getDouble("min"));
            max = Math.max(max, durs.get(i).getDouble("max"));
        }

        JsonObject duration = new JsonObject()
            .put("avg", totalCount == 0 ? 0 : weightedAvg / totalCount)
            .put("min", min)
            .put("max", max);
        for (String stat : PER_INSTANCE_STATS) {
            JsonArray perInstance = new JsonArray();
            durs.forEach(d -> perInstance.add(d.getDouble(stat)));
            duration.put(stat, perInstance);
        }

        return new JsonObject()
            .put("instances", summaries.size())
            .put("http_reqs", new JsonObject().put("count", totalCount).put("rate", totalRate))
            .put("http_req_duration", duration);
    }

    private static JsonObject values(JsonObject summary, String metric) {
        return summary.getJsonObject("metrics").getJsonObject(metric).getJsonObject("values");
    }
}
