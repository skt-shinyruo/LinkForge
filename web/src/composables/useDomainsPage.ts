import { computed, getCurrentInstance, onMounted, reactive, ref } from "vue";
import { listApplications } from "../services/applications";
import {
  authorizeDomain,
  createApplicationDomain,
  createTenantSharedDomain,
  listDomains,
} from "../services/domains";
import type { ApplicationDto, DomainDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useDomainsPage() {
  const applications = ref<ApplicationDto[]>([]);
  const domains = ref<DomainDto[]>([]);
  const loading = ref(false);
  const creating = ref(false);
  const authorizing = ref(false);
  const error = ref<string | null>(null);

  const createForm = reactive({
    hostname: "",
    applicationId: null as number | null,
  });

  const authorizationForm = reactive({
    applicationId: null as number | null,
    domainId: null as number | null,
  });

  const tenantSharedDomains = computed(() =>
    domains.value.filter((domain) => domain.scope === "TENANT_SHARED"),
  );

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const [nextApplications, nextDomains] = await Promise.all([
        listApplications(),
        listDomains(),
      ]);
      applications.value = nextApplications;
      domains.value = nextDomains;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载域名失败");
    } finally {
      loading.value = false;
    }
  }

  async function create() {
    const hostname = createForm.hostname.trim();
    if (!hostname) {
      return;
    }

    creating.value = true;
    error.value = null;
    try {
      if (createForm.applicationId) {
        await createApplicationDomain(createForm.applicationId, { hostname });
      } else {
        await createTenantSharedDomain({ hostname });
      }
      createForm.hostname = "";
      createForm.applicationId = null;
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "创建域名失败");
    } finally {
      creating.value = false;
    }
  }

  async function authorize() {
    if (!authorizationForm.applicationId || !authorizationForm.domainId) {
      return;
    }

    authorizing.value = true;
    error.value = null;
    try {
      await authorizeDomain(authorizationForm.applicationId, authorizationForm.domainId);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "域名授权失败");
    } finally {
      authorizing.value = false;
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    applications,
    authorizationForm,
    authorize,
    authorizing,
    create,
    createForm,
    creating,
    domains,
    error,
    load,
    loading,
    tenantSharedDomains,
  };
}
