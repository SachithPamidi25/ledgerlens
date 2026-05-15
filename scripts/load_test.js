import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const uploadUrlDuration = new Trend('upload_url_duration');
const receiptListDuration = new Trend('receipt_list_duration');
const insightsDuration = new Trend('insights_duration');
const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const selected = __ENV.SCENARIO || 'smoke';

const scenarios = {
  smoke: {
    stages: [
      { duration: '30s', target: 5 },
      { duration: '1m', target: 5 },
      { duration: '15s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<1000'],
      http_req_failed: ['rate<0.01'],
    },
  },
  load: {
    stages: [
      { duration: '30s', target: 50 },
      { duration: '2m', target: 100 },
      { duration: '1m', target: 200 },
      { duration: '2m', target: 200 },
      { duration: '30s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<2000'],
      http_req_failed: ['rate<0.05'],
      receipt_list_duration: ['p(95)<500'],
      insights_duration: ['p(95)<2000'],
    },
  },
  stress: {
    stages: [
      { duration: '30s', target: 100 },
      { duration: '1m', target: 300 },
      { duration: '1m', target: 500 },
      { duration: '2m', target: 500 },
      { duration: '30s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<5000'],
      http_req_failed: ['rate<0.10'],
    },
  },
};

const config = scenarios[selected] || scenarios.smoke;
const maxVus = Math.max(...config.stages.map((stage) => stage.target));
const userCount = Number(__ENV.USER_COUNT || maxVus || 1);

export const options = {
  stages: config.stages,
  thresholds: config.thresholds,
};

const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

function authHeaders(token) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  };
}

function checkOrLog(res, name, predicate) {
  const ok = check(res, { [name]: predicate });
  if (!ok) {
    errorRate.add(1);
    console.error(`${name} failed: status=${res.status} body=${String(res.body).slice(0, 200)}`);
  }
  return ok;
}

export function setup() {
  const users = [];
  const runId = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;

  for (let i = 1; i <= userCount; i++) {
    const email = `k6user_${runId}_${i}@loadtest.com`;
    const password = 'LoadTest@123';

    const regRes = http.post(
      `${BASE_URL}/api/auth/register`,
      JSON.stringify({ fullName: `Load Tester ${i}`, email, password }),
      jsonHeaders
    );

    if (regRes.status >= 200 && regRes.status < 300) {
      const body = JSON.parse(regRes.body);
      users.push({
        email,
        password,
        accessToken: body.accessToken,
        refreshToken: body.refreshToken,
      });
    } else {
      console.error(`setup register failed: email=${email} status=${regRes.status} body=${String(regRes.body).slice(0, 200)}`);
    }
  }

  if (users.length === 0) {
    throw new Error('No users created in setup; aborting load test.');
  }

  return { users };
}

export default function (data) {
  const user = data.users[(__VU - 1) % data.users.length];
  const accessToken = user.accessToken;

  if (!accessToken) {
    errorRate.add(1);
    sleep(1);
    return;
  }

  let receiptId = null;

  group('01 - Upload URL', () => {
    const filename = `receipt_${__VU}_${__ITER}_${Date.now()}.png`;
    const res = http.post(
      `${BASE_URL}/api/receipts/upload-url?filename=${encodeURIComponent(filename)}`,
      null,
      authHeaders(accessToken)
    );
    uploadUrlDuration.add(res.timings.duration);
    if (checkOrLog(res, 'upload-url: 2xx', (r) => r.status >= 200 && r.status < 300)) {
      const body = JSON.parse(res.body);
      receiptId = body.receiptId || body.id;
    }
  });

  sleep(0.3);

  group('02 - Process Receipt', () => {
    if (!receiptId) return;
    const res = http.post(
      `${BASE_URL}/api/receipts/${receiptId}/process`,
      null,
      authHeaders(accessToken)
    );
    checkOrLog(res, 'process: 2xx', (r) => r.status >= 200 && r.status < 300);
  });

  sleep(0.5);

  group('03 - List Receipts', () => {
    const res = http.get(
      `${BASE_URL}/api/receipts?page=0&size=20`,
      authHeaders(accessToken)
    );
    receiptListDuration.add(res.timings.duration);
    checkOrLog(res, 'list receipts: 200', (r) => r.status === 200);
  });

  sleep(0.3);

  group('04 - Expense Summary', () => {
    const res = http.get(
      `${BASE_URL}/api/expenses/summary`,
      authHeaders(accessToken)
    );
    checkOrLog(res, 'expense summary: 200', (r) => r.status === 200);
  });

  sleep(0.3);

  group('05 - Insights', () => {
    const res = http.get(
      `${BASE_URL}/api/insights`,
      authHeaders(accessToken)
    );
    insightsDuration.add(res.timings.duration);
    checkOrLog(res, 'insights: 200', (r) => r.status === 200);
  });

  sleep(0.3);

  group('06 - Delete Receipt', () => {
    if (!receiptId) return;
    const res = http.del(
      `${BASE_URL}/api/receipts/${receiptId}`,
      null,
      authHeaders(accessToken)
    );
    checkOrLog(res, 'delete receipt: 2xx', (r) => r.status >= 200 && r.status < 300);
  });

  sleep(1);
}

export function teardown(data) {
  for (const user of data.users || []) {
    if (!user.accessToken) continue;
    http.post(
      `${BASE_URL}/api/auth/logout`,
      JSON.stringify({ refreshToken: user.refreshToken }),
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${user.accessToken}`,
        },
      }
    );
  }
}
