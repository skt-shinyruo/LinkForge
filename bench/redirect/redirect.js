import http from "k6/http";
import { check } from "k6";

function envInt(name, def) {
  const raw = __ENV[name];
  if (!raw) return def;
  const n = parseInt(raw, 10);
  return Number.isFinite(n) ? n : def;
}

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const CODE = __ENV.CODE || "";

const RATE = envInt("RATE", 1000); // requests / second
const PRE_ALLOCATED_VUS = envInt("PRE_ALLOCATED_VUS", 200);
const MAX_VUS = envInt("MAX_VUS", 2000);
const DURATION = __ENV.DURATION || "30s";

export const options = {
  scenarios: {
    redirect: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<200"],
  },
};

export default function () {
  if (!CODE) {
    throw new Error("CODE 不能为空：请设置环境变量 CODE=<short_code>");
  }
  const res = http.get(`${BASE_URL}/r/${CODE}`, { redirects: 0 });
  check(res, {
    "status is 301/302": (r) => r.status === 301 || r.status === 302,
    "has Location": (r) => !!r.headers["Location"],
  });
}

