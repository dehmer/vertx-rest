import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const SUT = __ENV.SUT || 'http://localhost:8080';

// Runs once per k6 instance, before the load starts.
export function setup() {
    const res = http.post(`${SUT}/k6-started`);
    // check(res, { 'run id received': (r) => r.status === 200 });
    const runId = res.body.trim();
    console.log(`joined run ${runId}`);
    return { runId };
}

// Runs once per k6 instance, after the test ends.
export function handleSummary(data) {
    const runId = data.setup_data.runId;
    console.log("[handleSummary]", runId)
    const res = http.post(`${SUT}/k6-finished/${runId}`, JSON.stringify(data), {
        headers: { 'Content-Type': 'application/json' },
    });

    const pending = parseInt(res.body)

    console.log(JSON.stringify(data, null, 2))
    const output = {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };

    if (pending === 0) {
        const report = http.get(`${SUT}/k6-report/${runId}`);
        output[`report-${runId}.json`] = report.body;
        output.stdout += `\nAggregated report for run ${runId}:\n${report.body}\n`;
    }

    return output;
    // if (pending === 0) {
    //     const res = http.get(`${SUT}/k6-report/${runId}`)
    //     console.log("[handleSummary] res", res.body)
    //     // TODO: return optional aggregated reports
    // }


    // // Returning an object replaces k6's default output.
    // // Re-add the usual console summary so you still see it.
    // // return { stdout: textSummary(data, { indent: ' ', enableColors: true }) };
    // return {
    //     'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    // }
}

export const options = {
    discardResponseBodies: false,
    scenarios: {
        glyph_load: {
            executor: 'ramping-arrival-rate',
            // Start low to give your Wi-Fi and server an easy warmup
            startRate: 1000,
            timeUnit: '1s',
            preAllocatedVUs: 150,
            maxVUs: 200,
            stages: [
                { target: 4000, duration: '10s' },
                // { target: 7000, duration: '30s' },
            ],
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: [
          'p(95)<300',
          'p(99)<600',
        ],
        checks: ['rate>0.99'],
    }
};

export default function () {
    const res = http.get('http://127.0.0.1:8080/glyph');

    check(res, {
        'status is 200': r => r.status === 200,
        'content type is JSON': r =>
            r.headers['Content-Type']?.includes('application/json'),
    });
}
