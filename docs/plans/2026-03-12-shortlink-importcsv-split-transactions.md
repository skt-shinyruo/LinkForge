# ShortLink CSV Import Split-Transaction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix `ShortLinkService#importCsv` doing a single giant transaction + linear `AfterCommit` registrations by splitting the import into independent small transactions (per-row), so memory/lock/rollback costs are bounded and cache/outbox writes don’t spike at one final commit.

**Architecture:** Remove `@Transactional` from `importCsv`, and execute each row’s `create(...)` inside a `TransactionTemplate` configured as `PROPAGATION_REQUIRES_NEW`. This guarantees that even if `importCsv` is called inside another transaction, the per-row create work will not join that caller transaction, preventing `AfterCommit` callback accumulation on the outer transaction.

**Tech Stack:** Spring Boot 3.x, Spring TX (`TransactionTemplate`), JUnit 5, AssertJ, Testcontainers (MySQL + Redis)

---

### Task 1: Add a failing integration test that reproduces callback accumulation

**Files:**
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java:102`

**Step 1: Write the failing test**

Add a new test that runs `importCsv(...)` inside an outer transaction and asserts that the outer transaction does **not** accumulate extra `TransactionSynchronization`s (old behavior: +N due to `AfterCommit.run(...)` being registered in the same big tx).

```java
@Test
void importCsv_inExistingTx_shouldNotAccumulateAfterCommitSynchronizations() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    outer.executeWithoutResult(status -> {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
        int before = TransactionSynchronizationManager.getSynchronizations().size();

        String csv = """
                originalUrl,code,expiresAt,note,tags
                https://example.com/a,,,,
                https://example.com/b,,,,
                """;
        shortLinkService.importCsv(TENANT_ID, USER_ID,
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        int after = TransactionSynchronizationManager.getSynchronizations().size();
        assertThat(after - before).isLessThanOrEqualTo(0);

        status.setRollbackOnly();
    });
}
```

**Step 2: Run the test to verify it fails**

Run:
- `cd server && mvn -Pit -pl integration-tests -am test -Dmaven.repo.local=../.m2/repository -Dtest=ShortLinkCacheAfterCommitIntegrationTest#importCsv_inExistingTx_shouldNotAccumulateAfterCommitSynchronizations -Dsurefire.failIfNoSpecifiedTests=false`

Expected:
- FAIL with `after - before` being `2` (or greater), proving `AfterCommit` callbacks are being registered onto the caller transaction.

---

### Task 2: Split `importCsv` into independent transactions (REQUIRES_NEW per row)

**Files:**
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java:378`

**Step 1: Add a `TransactionTemplate` configured as `REQUIRES_NEW`**

In the service constructor, build a `TransactionTemplate` from `PlatformTransactionManager` and set:
- `PROPAGATION_REQUIRES_NEW`

**Step 2: Remove `@Transactional` from `importCsv` and wrap each row’s `create`**

Replace the direct call:
- `create(tenantId, createdBy, req);`

with:
- `importRowTx.executeWithoutResult(status -> create(tenantId, createdBy, req));`

Keep the per-line try/catch so one bad line doesn’t abort the whole import.

**Step 3: Run the test again**

Run:
- `cd server && mvn -Pit -pl integration-tests -am test -Dmaven.repo.local=../.m2/repository -Dtest=ShortLinkCacheAfterCommitIntegrationTest#importCsv_inExistingTx_shouldNotAccumulateAfterCommitSynchronizations -Dsurefire.failIfNoSpecifiedTests=false`

Expected:
- PASS, proving `importCsv` no longer adds `AfterCommit` callbacks to the outer transaction.

---

### Task 3: Regression check

**Step 1: Run full integration tests module**

Run:
- `cd server && mvn -Pit -pl integration-tests -am test -Dmaven.repo.local=../.m2/repository`

Expected:
- PASS
