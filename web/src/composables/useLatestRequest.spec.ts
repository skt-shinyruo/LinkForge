import { describe, expect, it, vi } from "vitest";
import { useLatestRequest } from "./useLatestRequest";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

describe("useLatestRequest", () => {
  it("aborts the previous request and ignores its late result", async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const commits: string[] = [];
    const firstSignals: AbortSignal[] = [];
    const request = useLatestRequest((caught, fallback) =>
      caught instanceof Error ? caught.message : fallback,
    );

    const firstRun = request.run(
      (signal) => {
        firstSignals.push(signal);
        return first.promise;
      },
      (value) => commits.push(value),
      "failed",
    );
    const secondRun = request.run(() => second.promise, (value) => commits.push(value), "failed");

    expect(firstSignals[0]?.aborted).toBe(true);
    second.resolve("new");
    await secondRun;
    first.resolve("old");
    await firstRun;

    expect(commits).toEqual(["new"]);
    expect(request.loading.value).toBe(false);
    expect(request.error.value).toBeNull();
  });

  it("does not publish an error from an obsolete request", async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    const formatter = vi.fn((_caught: unknown, fallback: string) => fallback);
    const request = useLatestRequest(formatter);

    const firstRun = request.run(() => first.promise, vi.fn(), "old failure");
    const secondRun = request.run(() => second.promise, vi.fn(), "new failure");
    second.resolve("ok");
    await secondRun;
    first.reject(new Error("late"));
    await firstRun;

    expect(formatter).not.toHaveBeenCalled();
    expect(request.error.value).toBeNull();
  });
});
