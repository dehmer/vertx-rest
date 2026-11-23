import http from 'k6/http';
import { check } from 'k6';

/*
    FRITZ!Box 7590
    Peak speed (Wi-Fi 5 @ 5 GHz): 1733 Mbit/s
*/
export const options = {
    discardResponseBodies: true,
    scenarios: {
        glyph_load: {
            executor: 'ramping-arrival-rate',
            // Start low to give your Wi-Fi and server an easy warmup
            startRate: 100,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 200,
            stages: [
                { target: 250, duration: '30s' },
                { target: 500, duration: '30s' },
                { target: 800, duration: '30s' },
                { target: 1200, duration: '30s' }
            ],
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: [
          'p(95)<150', // ms
          'p(99)<200', // ms
        ],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const res = http.get('http://192.168.178.51:8080/glyph');

    check(res, {
        'status is 200': r => r.status === 200,
        'content type is JSON': r =>
            r.headers['Content-Type']?.includes('application/json'),
    });
}
