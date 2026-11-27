package io.bitbang.poc.rest.k6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One k6 run: how many workers joined, and the reports received so far. */
record Run(UUID id, int started, List<String> reports) {

    static Run create(UUID id) {
        return new Run(id, 0, List.of());
    }

    int pending() {
        return started - reports.size();
    }

    boolean isActive() {
        return pending() > 0;
    }

    Run withWorkerStarted() {
        return new Run(id, started + 1, reports);
    }

    Run withReport(String report) {
        List<String> updated = new ArrayList<>(reports);
        updated.add(report);
        return new Run(id, started, List.copyOf(updated));
    }
}

/** The whole application state. Immutable: every "change" yields a new instance. */
record K6State(Map<UUID, Run> runs, Optional<UUID> current) {

    static K6State empty() {
        return new K6State(Map.of(), Optional.empty());
    }

    Optional<Run> run(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    /** Store (insert or replace) a run. */
    K6State with(Run run) {
        Map<UUID, Run> updated = new HashMap<>(runs);
        updated.put(run.id(), run);
        return new K6State(Map.copyOf(updated), current);
    }

    K6State withCurrent(UUID id) {
        return new K6State(runs, Optional.of(id));
    }
}

/** A state change plus the value to hand back to the caller. */
record Transition<T>(K6State state, T result) {}
