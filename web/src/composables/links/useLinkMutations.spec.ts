import { beforeEach, describe, expect, it, vi } from "vitest";
import { reactive, ref } from "vue";
import { updateLink } from "../../services/links";
import { createEmptyCreateForm, createEmptyEditForm } from "./linkFormCodec";
import { useLinkMutations } from "./useLinkMutations";

vi.mock("../../services/links", () => ({
  archiveLink: vi.fn(),
  createLink: vi.fn(),
  deleteLink: vi.fn(),
  restoreLink: vi.fn(),
  updateLink: vi.fn(),
}));

const updateLinkMock = vi.mocked(updateLink);

describe("useLinkMutations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("surfaces pending approval when saving an application link destination change", async () => {
    const editForm = reactive(createEmptyEditForm());
    editForm.originalUrl = "https://example.com/new";
    const editingId = ref<number | null>(101);
    const errors: (string | null)[] = [];
    const load = vi.fn();
    const resetEditForm = vi.fn();

    updateLinkMock.mockResolvedValueOnce({
      id: 101,
      tenantId: 1,
      applicationId: 2001,
      domainId: 3001,
      code: "abc123",
      shortUrl: "https://go.example/r/abc123",
      originalUrl: "https://example.com/old",
      enabled: true,
      pendingApproval: true,
      approvalRequestId: 7001,
      requestedOriginalUrl: "https://example.com/new",
    });

    const mutations = useLinkMutations({
      createForm: reactive(createEmptyCreateForm()),
      editForm,
      editingId,
      creating: ref(false),
      filters: { showArchived: false },
      selectedApplicationId: ref(2001),
      selectedDomainId: ref(3001),
      setError: (message) => errors.push(message),
      getErrorMessage: (error, fallback) => (error instanceof Error ? error.message : fallback),
      load,
      resetCreateForm: vi.fn(),
      resetEditForm,
    });

    await mutations.saveEdit();

    expect(errors[errors.length - 1]).toBe("目标地址变更已提交审批（#7001），审批通过后生效");
    expect(editingId.value).toBeNull();
    expect(resetEditForm).toHaveBeenCalledTimes(1);
    expect(load).toHaveBeenCalledTimes(1);
  });
});
