package io.bitbang.poc.rest.k6;

import java.util.UUID;

/** Pure functions only: no I/O, no randomness, no clocks. */
final class K6Core {

    private K6Core() {}

    /** A worker announces itself. Returns the run ID it should use. */
    static Transition<UUID> workerStarted(K6State state, UUID freshId) {
        Run run = state.current()
            .flatMap(state::run)
            .filter(Run::isActive)
            .orElseGet(() -> Run.create(freshId))
            .withWorkerStarted();

        K6State next = state.with(run).withCurrent(run.id());
        return new Transition<>(next, run.id());
    }

    /** A worker uploads its report. Returns the number of workers still pending. */
    static Transition<Integer> workerFinished(K6State state, UUID runId, String report) {
        Run run = state.run(runId)
            .orElseThrow()   // happy path: unknown run IDs are ignored for now
            .withReport(report);

        return new Transition<>(state.with(run), run.pending());
    }
}
