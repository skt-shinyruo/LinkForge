# LinkForge Test System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, repeatable LinkForge testing system with fast unit gates, isolated integration tests, coverage reporting, targeted business-risk tests, pressure-test baselines, and clear documentation.

**Architecture:** Keep the existing Maven/Vitest/Testcontainers/k6 stack. Make `mvn test` fast by moving `server/integration-tests` behind the existing documented `it` profile, add JaCoCo and Vitest coverage as explicit quality gates, then fill targeted gaps at the HTTP/integration and frontend behavior boundaries. Keep pressure testing outside correctness gates and document smoke/baseline/ramp/burst runs.

**Tech Stack:** Java 17, Maven, Spring Boot 3.2.5, JUnit 5, AssertJ, Testcontainers, JaCoCo, Vue 3, Vite, Vitest, `@vitest/coverage-v8`, k6.

---

## File Structure

- Modify `server/pom.xml`: remove `integration-tests` from the default modules list, add an `it` Maven profile that includes `integration-tests`, and add JaCoCo plugin management/configuration for coverage reporting and a modest check.
- Modify `server/app/src/test/java/com/linkforge/architecture/DefaultTestSuiteGuardTest.java`: reverse the current guard so it asserts integration tests are excluded from the default reactor and included only in the `it` profile.
- Modify `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`: add an OpenAPI application-scope HTTP regression test that proves an API key bound to one application cannot create or list links through another application route.
- Modify `web/package.json`: add `test:coverage` and add `@vitest/coverage-v8`.
- Modify `web/package-lock.json`: update via `npm install --package-lock-only` after editing `package.json`.
- Modify `web/vite.config.ts`: configure coverage includes/excludes and modest thresholds.
- Create `web/src/composables/useAuditPage.spec.ts`: cover successful load and error state behavior for a currently untested composable.
- Modify `bench/redirect/redirect.js`: add scenario selection, stricter parameter validation, named thresholds, and smoke/baseline/ramp/burst modes.
- Modify `bench/redirect/README.md`: document pressure-test prerequisites and the four repeatable scenarios.
- Modify `bench/redirect/results.md`: add a reusable result-recording template and retain existing baseline history.
- Create `docs/reference/testing.md`: document quick, full, coverage, integration, and pressure-test commands with prerequisites.
- Modify `docs/reference/README.md`: add a link to `testing.md`.
- Modify `README.md`: point the local development testing section at the testing reference doc and correct the fast/integration command semantics.

---

### Task 1: Isolate Integration Tests Behind the Maven `it` Profile

**Files:**
- Modify: `server/app/src/test/java/com/linkforge/architecture/DefaultTestSuiteGuardTest.java`
- Modify: `server/pom.xml`

- [ ] **Step 1: Write the failing guard test**

Replace the body of `integration_tests_should_be_part_of_default_reactor` in `server/app/src/test/java/com/linkforge/architecture/DefaultTestSuiteGuardTest.java` with:

```java
    @Test
    void integration_tests_should_be_isolated_behind_it_profile() throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resolveFromCurrentWorkspace("../pom.xml", "server/pom.xml").toFile())
                .getDocumentElement();

        assertThat(childTexts(project, "modules", "module"))
                .as("Default mvn test must stay fast and must not require Docker/Testcontainers")
                .doesNotContain("integration-tests");

        List<String> profileIdsWithIntegrationTests = childElements(project, "profiles").stream()
                .flatMap(profiles -> childElements(profiles, "profile").stream())
                .filter(profile -> childTexts(profile, "modules", "module").contains("integration-tests"))
                .map(profile -> childText(profile, "id"))
                .toList();

        assertThat(profileIdsWithIntegrationTests)
                .as("The documented mvn -Pit test command must include integration-tests")
                .containsExactly("it");
    }
```

- [ ] **Step 2: Run the guard test and verify it fails**

Run:

```bash
cd server
mvn -pl app -Dtest=DefaultTestSuiteGuardTest test
```

Expected: FAIL because `server/pom.xml` still contains `<module>integration-tests</module>` in the default `<modules>` block and has no `it` profile.

- [ ] **Step 3: Move `integration-tests` into an `it` profile**

In `server/pom.xml`, remove this line from the default `<modules>` block:

```xml
        <module>integration-tests</module>
```

Then add this block before the closing `</project>`:

```xml
    <profiles>
        <profile>
            <id>it</id>
            <modules>
                <module>integration-tests</module>
            </modules>
        </profile>
    </profiles>
```

Keep all existing non-integration modules in the default reactor.

- [ ] **Step 4: Run the guard test and verify it passes**

Run:

```bash
cd server
mvn -pl app -Dtest=DefaultTestSuiteGuardTest test
```

Expected: PASS.

- [ ] **Step 5: Verify both Maven reactor commands resolve the intended modules**

Run:

```bash
cd server
mvn -q help:evaluate -Dexpression=project.modules -DforceStdout
```

Expected: output does not include `integration-tests`.

Run:

```bash
cd server
mvn -q -Pit help:evaluate -Dexpression=project.modules -DforceStdout
```

Expected: output includes `integration-tests`.

- [ ] **Step 6: Commit**

```bash
git add server/pom.xml server/app/src/test/java/com/linkforge/architecture/DefaultTestSuiteGuardTest.java
git commit -m "test: isolate integration suite behind it profile"
```

---

### Task 2: Add Backend JaCoCo Coverage Gate

**Files:**
- Modify: `server/pom.xml`

- [ ] **Step 1: Add the failing verification for missing coverage report**

Run:

```bash
cd server
mvn -pl foundation/core test jacoco:report
```

Expected: FAIL with `No plugin found for prefix 'jacoco'` or no managed JaCoCo report configuration. If the command unexpectedly succeeds, continue by verifying that `foundation/core/target/site/jacoco/jacoco.xml` is absent or empty; the implementation still needs a managed parent-level plugin.

- [ ] **Step 2: Add JaCoCo properties**

In the `<properties>` block of `server/pom.xml`, add:

```xml
        <jacoco.version>0.8.12</jacoco.version>
        <jacoco.minimum.instruction.coverage>0.20</jacoco.minimum.instruction.coverage>
```

- [ ] **Step 3: Add JaCoCo to plugin management**

Inside `server/pom.xml` under `<build><pluginManagement><plugins>`, after the existing `maven-surefire-plugin`, add:

```xml
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>${jacoco.version}</version>
                    <configuration>
                        <excludes>
                            <exclude>**/*Application.class</exclude>
                            <exclude>**/*Config.class</exclude>
                            <exclude>**/*Configuration.class</exclude>
                            <exclude>**/*Properties.class</exclude>
                        </excludes>
                    </configuration>
                    <executions>
                        <execution>
                            <id>prepare-agent</id>
                            <goals>
                                <goal>prepare-agent</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>report</id>
                            <phase>test</phase>
                            <goals>
                                <goal>report</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>check</id>
                            <phase>verify</phase>
                            <goals>
                                <goal>check</goal>
                            </goals>
                            <configuration>
                                <rules>
                                    <rule>
                                        <element>BUNDLE</element>
                                        <limits>
                                            <limit>
                                                <counter>INSTRUCTION</counter>
                                                <value>COVEREDRATIO</value>
                                                <minimum>${jacoco.minimum.instruction.coverage}</minimum>
                                            </limit>
                                        </limits>
                                    </rule>
                                </rules>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
```

- [ ] **Step 4: Activate JaCoCo for all modules**

Still in `server/pom.xml`, add this `<plugins>` block under `<build>` after `</pluginManagement>`:

```xml
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
```

The `<plugins>` block must be a direct child of `<build>` and a sibling of the existing `<pluginManagement>` block. Do not remove the existing `maven-surefire-plugin` configuration.

```xml
        <plugins>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
        </plugins>
```

- [ ] **Step 5: Verify coverage report generation**

Run:

```bash
cd server
mvn -pl foundation/core test
test -f foundation/core/target/site/jacoco/jacoco.xml
```

Expected: PASS and `foundation/core/target/site/jacoco/jacoco.xml` exists.

- [ ] **Step 6: Verify the coverage check goal**

Run:

```bash
cd server
mvn -pl foundation/core verify
```

Expected: PASS with JaCoCo check executed.

- [ ] **Step 7: Commit**

```bash
git add server/pom.xml
git commit -m "test: add backend coverage reporting"
```

---

### Task 3: Add Frontend Vitest Coverage Gate

**Files:**
- Modify: `web/package.json`
- Modify: `web/package-lock.json`
- Modify: `web/vite.config.ts`

- [ ] **Step 1: Run the missing coverage command and verify it fails**

Run:

```bash
cd web
npm run test:coverage
```

Expected: FAIL because `test:coverage` is not defined.

- [ ] **Step 2: Add coverage dependency and script**

In `web/package.json`, add the script:

```json
    "test:coverage": "vitest run --coverage",
```

The scripts block should become:

```json
  "scripts": {
    "dev": "vite",
    "test": "vitest run",
    "test:coverage": "vitest run --coverage",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview"
  },
```

Add this dev dependency:

```json
    "@vitest/coverage-v8": "^3.2.4",
```

Place it near `vitest` in `devDependencies`.

- [ ] **Step 3: Update the package lock**

Run:

```bash
cd web
npm install --package-lock-only
```

Expected: PASS and `web/package-lock.json` includes `@vitest/coverage-v8`.

- [ ] **Step 4: Configure Vitest coverage**

Replace `web/vite.config.ts` with:

```ts
import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: false,
    restoreMocks: true,
    unstubEnvs: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      include: ["src/**/*.{ts,vue}"],
      exclude: [
        "src/main.ts",
        "src/**/*.d.ts",
        "src/assets/**",
        "src/**/__fixtures__/**",
      ],
      thresholds: {
        statements: 20,
        branches: 15,
        functions: 20,
        lines: 20,
      },
    },
  },
  server: {
    proxy: {
      // 本地开发：将 API 与跳转请求代理到后端
      "/api": "http://localhost:8080",
      "/r": "http://localhost:8080",
    },
  },
});
```

- [ ] **Step 5: Verify frontend coverage**

Run:

```bash
cd web
npm run test:coverage
```

Expected: PASS and `web/coverage/lcov.info` exists.

- [ ] **Step 6: Verify normal frontend tests still pass**

Run:

```bash
cd web
npm run test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add web/package.json web/package-lock.json web/vite.config.ts
git commit -m "test: add frontend coverage reporting"
```

---

### Task 4: Add OpenAPI Application-Scope Integration Regression

**Files:**
- Modify: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

- [ ] **Step 1: Add the failing HTTP integration test**

In `LinkForgeIntegrationTest`, add this test near `endToEnd_register_login_create_stats_and_openapi`:

```java
    @Test
    void openapi_application_routes_should_reject_api_key_bound_to_another_application() throws Exception {
        RegisteredPrincipal registered = registerTenantAdmin("openapi-scope");
        AppDomainFixture allowed = provisionDedicatedApplication(
                registered.tenantId(),
                "openapi-allowed-" + registered.tenantId(),
                "allowed-" + registered.tenantId() + ".example.test"
        );
        AppDomainFixture forbidden = provisionDedicatedApplication(
                registered.tenantId(),
                "openapi-forbidden-" + registered.tenantId(),
                "forbidden-" + registered.tenantId() + ".example.test"
        );

        JsonNode createKeyBody = objectMapper.createObjectNode()
                .put("applicationId", allowed.applicationId())
                .put("name", "allowed-openapi-key");
        String createKeyResp = mockMvc.perform(
                        post("/api/v1/api-keys")
                                .header("Authorization", "Bearer " + registered.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKeyBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String apiKey = objectMapper.readTree(createKeyResp).get("data").get("apiKey").asText();

        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/forbidden")
                .put("domainId", forbidden.domainId());

        String forbiddenCreateResp = mockMvc.perform(
                        post("/api/v1/open/applications/" + forbidden.applicationId() + "/links")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody))
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode forbiddenCreateJson = objectMapper.readTree(forbiddenCreateResp);
        assertThat(forbiddenCreateJson.get("code").asInt()).isNotEqualTo(0);

        String forbiddenListResp = mockMvc.perform(
                        get("/api/v1/open/applications/" + forbidden.applicationId() + "/links")
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode forbiddenListJson = objectMapper.readTree(forbiddenListResp);
        assertThat(forbiddenListJson.get("code").asInt()).isNotEqualTo(0);
    }
```

- [ ] **Step 2: Run the new integration test and verify it fails if the HTTP boundary is not protected**

Run:

```bash
cd server
mvn -Pit -pl integration-tests -Dtest=LinkForgeIntegrationTest#openapi_application_routes_should_reject_api_key_bound_to_another_application test
```

Expected: FAIL if the OpenAPI controller/application scope allows the wrong application. If it already passes, keep the test as a regression guard and proceed.

- [ ] **Step 3: Fix application-scope enforcement only if the test fails**

If the test fails because the forbidden create/list returns success, inspect `ShortLinkActorScopeResolver` and ensure `resolveCreateForApiKey` and `resolveBrowseForApiKey` reject any requested application ID different from `ApiKeyActor.applicationId()`.

The intended rule is:

```java
        if (principalApplicationId == null) {
            throw new BusinessException(ShortLinkErrorCode.APPLICATION_SCOPE_REQUIRED);
        }
        if (requestedApplicationId != null && !principalApplicationId.equals(requestedApplicationId)) {
            throw new BusinessException(ShortLinkErrorCode.APPLICATION_SCOPE_FORBIDDEN);
        }
```

Use the existing `ShortLinkActorScopeResolverTest` names as the model for any service-level changes. Do not change HTTP response envelopes.

- [ ] **Step 4: Verify the targeted integration test**

Run:

```bash
cd server
mvn -Pit -pl integration-tests -Dtest=LinkForgeIntegrationTest#openapi_application_routes_should_reject_api_key_bound_to_another_application test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java
git commit -m "test: cover openapi application scope isolation"
```

If Step 3 required production changes, include those files in the commit and keep the same message.

---

### Task 5: Add Frontend Audit Page Behavior Tests

**Files:**
- Create: `web/src/composables/useAuditPage.spec.ts`

- [ ] **Step 1: Write the failing tests**

Create `web/src/composables/useAuditPage.spec.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuditLogDto } from "../services/types";

const listAuditLogsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/audit", () => ({
  listAuditLogs: listAuditLogsMock,
}));

function createAuditLog(id: number): AuditLogDto {
  return {
    id,
    tenantId: 7,
    operationType: "PUBLIC_LINK_DESTINATION_CHANGE",
    targetType: "SHORT_LINK",
    targetId: 42,
    actorUserId: 9,
    actorEmail: "auditor@example.com",
    action: "APPROVE",
    detail: "approved",
    createdAt: "2026-06-08T10:00:00Z",
  };
}

describe("useAuditPage", () => {
  beforeEach(() => {
    vi.resetModules();
    listAuditLogsMock.mockReset();
  });

  it("loads audit logs and clears previous errors", async () => {
    listAuditLogsMock.mockResolvedValueOnce([createAuditLog(1)]);

    const { useAuditPage } = await import("./useAuditPage");
    const page = useAuditPage();

    page.error.value = "previous";
    await page.load();

    expect(listAuditLogsMock).toHaveBeenCalledOnce();
    expect(page.logs.value.map((item) => item.id)).toEqual([1]);
    expect(page.error.value).toBeNull();
    expect(page.loading.value).toBe(false);
  });

  it("stores service error messages and keeps loading state balanced", async () => {
    listAuditLogsMock.mockRejectedValueOnce(new Error("audit unavailable"));

    const { useAuditPage } = await import("./useAuditPage");
    const page = useAuditPage();

    await page.load();

    expect(page.logs.value).toEqual([]);
    expect(page.error.value).toBe("audit unavailable");
    expect(page.loading.value).toBe(false);
  });
});
```

- [ ] **Step 2: Run the new frontend test**

Run:

```bash
cd web
npm run test -- src/composables/useAuditPage.spec.ts
```

Expected: PASS if `AuditLogDto` field names match. If TypeScript reports a DTO field mismatch, inspect `web/src/services/types.ts` and update `createAuditLog` to use the exact existing `AuditLogDto` shape. Do not weaken assertions.

- [ ] **Step 3: Verify frontend coverage improves without breaking the suite**

Run:

```bash
cd web
npm run test:coverage
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/composables/useAuditPage.spec.ts
git commit -m "test: cover audit page loading states"
```

---

### Task 6: Make Redirect Pressure Tests Repeatable

**Files:**
- Modify: `bench/redirect/redirect.js`
- Modify: `bench/redirect/README.md`
- Modify: `bench/redirect/results.md`

- [ ] **Step 1: Run current script validation and capture existing behavior**

Run:

```bash
docker run --rm -i -v "$PWD/bench/redirect:/scripts" grafana/k6:latest run /scripts/redirect.js -e BASE_URL="http://127.0.0.1:18080"
```

Expected: FAIL with `CODE 不能为空：请设置环境变量 CODE=<short_code>`.

- [ ] **Step 2: Replace the k6 script with scenario modes**

Replace `bench/redirect/redirect.js` with:

```js
import http from "k6/http";
import { check } from "k6";

function envInt(name, def) {
  const raw = __ENV[name];
  if (!raw) return def;
  const n = parseInt(raw, 10);
  return Number.isFinite(n) && n > 0 ? n : def;
}

function envDuration(name, def) {
  const raw = __ENV[name];
  return raw && raw.trim() ? raw.trim() : def;
}

function scenarioName() {
  return (__ENV.SCENARIO || "baseline").trim().toLowerCase();
}

const BASE_URL = (__ENV.BASE_URL || "http://localhost:18080").replace(/\/+$/, "");
const CODE = (__ENV.CODE || "").trim();
const SCENARIO = scenarioName();

const presets = {
  smoke: {
    rate: envInt("RATE", 10),
    duration: envDuration("DURATION", "10s"),
    preAllocatedVUs: envInt("PRE_ALLOCATED_VUS", 5),
    maxVUs: envInt("MAX_VUS", 50),
    p95: envInt("P95_MS", 500),
    failedRate: __ENV.FAILED_RATE || "0.01",
  },
  baseline: {
    rate: envInt("RATE", 1000),
    duration: envDuration("DURATION", "30s"),
    preAllocatedVUs: envInt("PRE_ALLOCATED_VUS", 200),
    maxVUs: envInt("MAX_VUS", 2000),
    p95: envInt("P95_MS", 200),
    failedRate: __ENV.FAILED_RATE || "0.01",
  },
  ramp: {
    stages: [
      { target: envInt("RAMP_TARGET_1", 100), duration: envDuration("RAMP_DURATION_1", "20s") },
      { target: envInt("RAMP_TARGET_2", 500), duration: envDuration("RAMP_DURATION_2", "20s") },
      { target: envInt("RAMP_TARGET_3", 1000), duration: envDuration("RAMP_DURATION_3", "20s") },
      { target: 0, duration: envDuration("RAMP_COOLDOWN", "10s") },
    ],
    p95: envInt("P95_MS", 300),
    failedRate: __ENV.FAILED_RATE || "0.01",
  },
  burst: {
    rate: envInt("RATE", 2000),
    duration: envDuration("DURATION", "15s"),
    preAllocatedVUs: envInt("PRE_ALLOCATED_VUS", 400),
    maxVUs: envInt("MAX_VUS", 3000),
    p95: envInt("P95_MS", 500),
    failedRate: __ENV.FAILED_RATE || "0.02",
  },
};

if (!Object.prototype.hasOwnProperty.call(presets, SCENARIO)) {
  throw new Error(`Unknown SCENARIO=${SCENARIO}; expected smoke, baseline, ramp, or burst`);
}

const preset = presets[SCENARIO];

function buildScenario() {
  if (SCENARIO === "ramp") {
    return {
      executor: "ramping-arrival-rate",
      startRate: envInt("RAMP_START_RATE", 1),
      timeUnit: "1s",
      preAllocatedVUs: envInt("PRE_ALLOCATED_VUS", 200),
      maxVUs: envInt("MAX_VUS", 2000),
      stages: preset.stages,
    };
  }
  return {
    executor: "constant-arrival-rate",
    rate: preset.rate,
    timeUnit: "1s",
    duration: preset.duration,
    preAllocatedVUs: preset.preAllocatedVUs,
    maxVUs: preset.maxVUs,
  };
}

export const options = {
  scenarios: {
    redirect: buildScenario(),
  },
  thresholds: {
    http_req_failed: [`rate<${preset.failedRate}`],
    http_req_duration: [`p(95)<${preset.p95}`],
    checks: ["rate>0.99"],
  },
  tags: {
    scenario: SCENARIO,
  },
};

export default function () {
  if (!CODE) {
    throw new Error("CODE is required: pass -e CODE=<short_code>");
  }
  const res = http.get(`${BASE_URL}/r/${encodeURIComponent(CODE)}`, { redirects: 0 });
  check(res, {
    "status is 301/302": (r) => r.status === 301 || r.status === 302,
    "has Location": (r) => !!r.headers.Location,
  });
}
```

- [ ] **Step 3: Verify script-level validation**

Run:

```bash
docker run --rm -i -v "$PWD/bench/redirect:/scripts" grafana/k6:latest run /scripts/redirect.js -e SCENARIO="unknown" -e CODE="abc"
```

Expected: FAIL with `Unknown SCENARIO=unknown`.

Run:

```bash
docker run --rm -i -v "$PWD/bench/redirect:/scripts" grafana/k6:latest inspect /scripts/redirect.js -e SCENARIO="smoke" -e CODE="abc"
```

Expected: PASS and output includes the `redirect` scenario.

- [ ] **Step 4: Update pressure-test README**

In `bench/redirect/README.md`, document these commands:

```bash
# smoke: validates target and script wiring
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e SCENARIO="smoke" \
  -e BASE_URL="http://localhost:18080" \
  -e CODE="<your_code>"

# baseline: repeatable regression comparison
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e SCENARIO="baseline" \
  -e BASE_URL="http://localhost:18080" \
  -e CODE="<your_code>"

# ramp: gradually increases arrival rate
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e SCENARIO="ramp" \
  -e BASE_URL="http://localhost:18080" \
  -e CODE="<your_code>"

# burst: short high-rate run
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e SCENARIO="burst" \
  -e BASE_URL="http://localhost:18080" \
  -e CODE="<your_code>"
```

Also document that pressure tests require a running local/test deployment and must not target production directly.

- [ ] **Step 5: Add a result template**

At the top of `bench/redirect/results.md`, after the introductory paragraph, add:

```markdown
## 结果记录模板

- 日期：
- 场景：`smoke` / `baseline` / `ramp` / `burst`
- 命令：
- BASE_URL：
- CODE 来源：
- 部署方式：
- 机器规格：
- RATE / DURATION / VU 参数：
- http_reqs：
- http_req_failed：
- http_req_duration avg / p95 / p99 / max：
- checks：
- 结论：
- 备注：

---
```

- [ ] **Step 6: Commit**

```bash
git add bench/redirect/redirect.js bench/redirect/README.md bench/redirect/results.md
git commit -m "test: define repeatable redirect pressure scenarios"
```

---

### Task 7: Document Test Commands and Run Final Verification

**Files:**
- Create: `docs/reference/testing.md`
- Modify: `docs/reference/README.md`
- Modify: `README.md`

- [ ] **Step 1: Create the testing reference document**

Create `docs/reference/testing.md`:

````markdown
# 测试与质量门禁

本文档定义 LinkForge 本地可重复执行的测试命令。默认快速门禁不依赖 Docker、k6 或正在运行的服务；集成测试和压测按需单独执行。

## 快速后端测试

```bash
cd server
mvn test
```

用途：运行默认 Maven reactor 中的单元测试、切片测试、架构守卫和覆盖率报告生成。

前置条件：JDK 17、Maven、本地依赖缓存可用。

## 后端覆盖率

```bash
cd server
mvn verify
```

用途：运行默认后端测试并执行 JaCoCo 覆盖率检查。

报告位置：各 Maven 模块的 `target/site/jacoco/index.html` 和 `target/site/jacoco/jacoco.xml`。

## 后端集成测试

```bash
cd server
mvn -Pit test
```

用途：额外包含 `server/integration-tests`，通过 Testcontainers 验证数据库、Redis、事务、安全和跨模块行为。

前置条件：Docker 可用。该命令比默认 `mvn test` 慢，不属于日常快速门禁。

## 快速前端测试

```bash
cd web
npm run test
```

用途：运行 Vitest 单元和组件行为测试。

## 前端覆盖率

```bash
cd web
npm run test:coverage
```

报告位置：`web/coverage/index.html` 和 `web/coverage/lcov.info`。

## 前端构建

```bash
cd web
npm run build
```

用途：运行 TypeScript 项目构建和 Vite 生产构建。

## 完整本地验证

```bash
cd server
mvn verify
mvn -Pit test

cd ../web
npm run test:coverage
npm run build
```

如果只做日常代码改动，优先运行后端 `mvn test` 或前端 `npm run test`。合并前再运行完整本地验证。

## Redirect 压测

压测脚本位于 `bench/redirect`，使用 k6。压测不属于正确性门禁，只用于本地或测试环境容量与性能回归对比。

先启动本地或测试部署，并准备一个有效短码 `CODE`。

```bash
docker run --rm -i \
  -v "$PWD/bench/redirect:/scripts" \
  grafana/k6:latest run /scripts/redirect.js \
  -e SCENARIO="smoke" \
  -e BASE_URL="http://localhost:18080" \
  -e CODE="<your_code>"
```

更多场景见 `bench/redirect/README.md`。禁止直接对生产环境执行压测。
````

- [ ] **Step 2: Link the testing doc from reference README**

In `docs/reference/README.md`, under `## 架构与总览`, add:

```markdown
- [测试与质量门禁](testing.md)：后端、前端、集成测试、覆盖率和 Redirect 压测命令。
```

- [ ] **Step 3: Update root README testing commands**

In `README.md`, replace the backend/frontend local development testing snippet with:

````markdown
后端：

```bash
cd server
mvn test          # 快速后端测试，不包含 Docker/Testcontainers 集成测试
mvn verify        # 快速后端测试 + JaCoCo 覆盖率检查
mvn -Pit test     # 集成测试（需要 Docker/Testcontainers）
mvn -pl app spring-boot:run
```

前端：

```bash
cd web
npm install
npm run test
npm run test:coverage
npm run build
npm run dev
```

完整测试说明见：`docs/reference/testing.md`。
````

- [ ] **Step 4: Run final backend fast verification**

Run:

```bash
cd server
mvn test
```

Expected: PASS. This must not start Testcontainers.

- [ ] **Step 5: Run final backend integration verification**

Run:

```bash
cd server
mvn -Pit test
```

Expected: PASS. This may start MySQL and Redis Testcontainers.

- [ ] **Step 6: Run final frontend verification**

Run:

```bash
cd web
npm run test:coverage
npm run build
```

Expected: PASS.

- [ ] **Step 7: Run pressure-test script validation**

Run:

```bash
docker run --rm -i -v "$PWD/bench/redirect:/scripts" grafana/k6:latest inspect /scripts/redirect.js -e SCENARIO="smoke" -e CODE="abc"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add README.md docs/reference/README.md docs/reference/testing.md
git commit -m "docs: document test and quality gates"
```

---

## Final Verification Checklist

- [ ] `cd server && mvn test` passes and does not include `server/integration-tests`
- [ ] `cd server && mvn verify` passes and generates JaCoCo reports
- [ ] `cd server && mvn -Pit test` passes and includes Testcontainers integration tests
- [ ] `cd web && npm run test` passes
- [ ] `cd web && npm run test:coverage` passes and writes `web/coverage/lcov.info`
- [ ] `cd web && npm run build` passes
- [ ] `docker run --rm -i -v "$PWD/bench/redirect:/scripts" grafana/k6:latest inspect /scripts/redirect.js -e SCENARIO="smoke" -e CODE="abc"` passes
- [ ] `docs/reference/testing.md` documents prerequisites and commands
- [ ] `bench/redirect/results.md` contains a reusable result template
