import http from "k6/http";
import { check } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 10,
  iterations: __ENV.ITERATIONS ? parseInt(__ENV.ITERATIONS) : 100,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/v1/accounts`,
    JSON.stringify({
      "customer-id": `customer-${uuidv4()}`,
      "name": `Test Account ${__VU}-${__ITER}`,
      "currency": "GBP",
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": uuidv4(),
      },
    },
  );

  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}
