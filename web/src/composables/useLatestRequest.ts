import { getCurrentScope, onScopeDispose, ref } from "vue";

function isAbortError(caught: unknown): boolean {
  return caught instanceof DOMException
    ? caught.name === "AbortError"
    : caught instanceof Error && caught.name === "AbortError";
}

/**
 * 为一组可替代的读取请求提供 latest-wins 语义。
 *
 * 新请求会取消旧 AbortController；即使底层 Promise 忽略 signal，请求序号仍阻止旧结果、旧错误和旧 finally
 * 覆盖当前状态。页面只在 commit 回调中修改业务状态，因此不会暴露半完成快照。
 */
export function useLatestRequest() {
  const loading = ref(false);
  const error = ref<string | null>(null);
  let generation = 0;
  let activeController: AbortController | null = null;

  async function run<T>(
    execute: (signal: AbortSignal) => Promise<T>,
    commit: (value: T) => void,
    fallbackMessage: string,
  ): Promise<void> {
    const currentGeneration = ++generation;
    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    loading.value = true;
    error.value = null;

    try {
      const value = await execute(controller.signal);
      if (currentGeneration === generation && !controller.signal.aborted) {
        commit(value);
      }
    } catch (caught) {
      if (
        currentGeneration === generation &&
        !controller.signal.aborted &&
        !isAbortError(caught)
      ) {
        error.value = caught instanceof Error ? caught.message : fallbackMessage;
      }
    } finally {
      if (currentGeneration === generation) {
        loading.value = false;
        activeController = null;
      }
    }
  }

  function cancel() {
    generation += 1;
    activeController?.abort();
    activeController = null;
    loading.value = false;
  }

  if (getCurrentScope()) {
    onScopeDispose(cancel);
  }

  return { cancel, error, loading, run };
}
