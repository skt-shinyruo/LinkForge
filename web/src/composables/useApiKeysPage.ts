import { reactive, ref } from "vue";
import { listApplications } from "../services/applications";
import {
  createApiKey,
  disableApiKey,
  enableApiKey,
  listApiKeys,
  rotateApiKey,
} from "../services/apiKeys";
import type { ApiKeyDto, ApplicationDto, CreateApiKeyResponse } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useApiKeysPage() {
  const applications = ref<ApplicationDto[]>([]);
  const apiKeys = ref<ApiKeyDto[]>([]);
  const latestCreated = ref<CreateApiKeyResponse | null>(null);
  const loading = ref(false);
  const creating = ref(false);
  const actingId = ref<number | null>(null);
  const error = ref<string | null>(null);

  const createForm = reactive({
    applicationId: null as number | null,
    name: "",
  });

  const selectedApplicationId = ref<number | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const [nextApplications, nextApiKeys] = await Promise.all([
        listApplications(),
        listApiKeys(selectedApplicationId.value ?? undefined),
      ]);
      applications.value = nextApplications;
      apiKeys.value = nextApiKeys;
      if (!createForm.applicationId && nextApplications.length > 0) {
        createForm.applicationId = nextApplications[0]!.id;
      }
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载 API Key 失败");
    } finally {
      loading.value = false;
    }
  }

  async function create() {
    if (!createForm.applicationId || !createForm.name.trim()) {
      return;
    }
    creating.value = true;
    error.value = null;
    try {
      latestCreated.value = await createApiKey({
        applicationId: createForm.applicationId,
        name: createForm.name.trim(),
      });
      createForm.name = "";
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "创建 API Key 失败");
    } finally {
      creating.value = false;
    }
  }

  async function disable(id: number) {
    actingId.value = id;
    error.value = null;
    try {
      await disableApiKey(id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "禁用 API Key 失败");
    } finally {
      actingId.value = null;
    }
  }

  async function enable(id: number) {
    actingId.value = id;
    error.value = null;
    try {
      await enableApiKey(id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "启用 API Key 失败");
    } finally {
      actingId.value = null;
    }
  }

  async function rotate(id: number) {
    actingId.value = id;
    error.value = null;
    try {
      latestCreated.value = await rotateApiKey(id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "轮换 API Key 失败");
    } finally {
      actingId.value = null;
    }
  }

  async function setSelectedApplicationId(value: number | null) {
    selectedApplicationId.value = value;
    await load();
  }

  return {
    actingId,
    apiKeys,
    applications,
    create,
    createForm,
    creating,
    enable,
    disable,
    error,
    latestCreated,
    load,
    loading,
    rotate,
    selectedApplicationId,
    setSelectedApplicationId,
  };
}
