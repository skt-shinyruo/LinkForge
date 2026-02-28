# Split platform API contract out of shared module Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move API error contract/JSON structure out of `server/platform` and prevent Edge from depending on it.

**Architecture:** Introduce `server/api-contract` module (`com.linkforge.contract.api.*`) for API contract types; API depends on it, Edge owns edge-local errors and responders.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Maven multi-module.

---

### Task 1: Add new Maven module `api-contract`

**Files:**
- Modify: `.worktrees/split-platform-api-contract/server/pom.xml`
- Create: `.worktrees/split-platform-api-contract/server/api-contract/pom.xml`
- Create: `.worktrees/split-platform-api-contract/server/api-contract/src/main/java/com/linkforge/contract/api/*`

**Steps:**
1. Add `<module>api-contract</module>` to parent POM.
2. Create `api-contract` POM with dependency on `linkforge-platform-shared` + required Spring deps.
3. Move contract classes from `server/platform/.../platform/api` into `api-contract` with package rename.

### Task 2: Refactor API module to new contract package

**Files:**
- Modify: `.worktrees/split-platform-api-contract/server/api/pom.xml`
- Modify: `.worktrees/split-platform-api-contract/server/api/src/main/java/**`
- Modify: `.worktrees/split-platform-api-contract/server/api/src/test/java/**`

**Steps:**
1. Add dependency on `linkforge-api-contract`.
2. Replace imports `com.linkforge.platform.api.*` → `com.linkforge.contract.api.*`.
3. Run `mvn -pl api -am test` and fix compilation errors.

### Task 3: Decouple Edge from API contract

**Files:**
- Modify: `.worktrees/split-platform-api-contract/server/edge/src/main/java/**`
- Create: `.worktrees/split-platform-api-contract/server/edge/src/main/java/com/linkforge/edge/web/error/*`
- Modify: `.worktrees/split-platform-api-contract/server/edge/src/test/java/**`

**Steps:**
1. Introduce `EdgeErrorCode`, `EdgeBusinessException`, `EdgeErrorResponseWriter`, `EdgeGlobalExceptionHandler`.
2. Update `RedirectService` and `RedirectController` to use edge-local errors.
3. Update `EdgeRiskControl` and `EdgeRiskControlFilter` to stop referencing API `ErrorCode` / `ApiErrorResponseWriter`.
4. Ensure browser requests can get HTML (Accept: `text/html`) and non-html callers get JSON.

### Task 4: Remove API contract package from platform module

**Files:**
- Delete: `.worktrees/split-platform-api-contract/server/platform/src/main/java/com/linkforge/platform/api/*`

**Steps:**
1. Delete the `platform/api` package.
2. Ensure `platform` still builds.

### Task 5: Verification

**Steps:**
1. Run full test suite: `cd .worktrees/split-platform-api-contract/server && mvn test`
2. Confirm Edge integration tests cover redirect + unavailable states.

