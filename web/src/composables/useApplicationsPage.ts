import { getCurrentInstance, onMounted, reactive, ref } from "vue";
import { createApplication, listApplications } from "../services/applications";
import type { ApplicationDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useApplicationsPage() {
  const applications = ref<ApplicationDto[]>([]);
  const loading = ref(false);
  const creating = ref(false);
  const error = ref<string | null>(null);

  const createForm = reactive({
    applicationKey: "",
    displayName: "",
  });

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      applications.value = await listApplications();
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载应用失败");
    } finally {
      loading.value = false;
    }
  }

  async function create() {
    const applicationKey = createForm.applicationKey.trim();
    const displayName = createForm.displayName.trim();
    if (!applicationKey || !displayName) {
      return;
    }

    creating.value = true;
    error.value = null;
    try {
      await createApplication({ applicationKey, displayName });
      createForm.applicationKey = "";
      createForm.displayName = "";
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "创建应用失败");
    } finally {
      creating.value = false;
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    applications,
    create,
    createForm,
    creating,
    error,
    load,
    loading,
  };
}
