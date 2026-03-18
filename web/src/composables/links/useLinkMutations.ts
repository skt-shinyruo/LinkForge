import {
  archiveLink as archiveLinkRequest,
  createLink as createLinkRequest,
  deleteLink as deleteLinkRequest,
  restoreLink as restoreLinkRequest,
  updateLink,
} from "../../services/links";
import type { LinkDto } from "../../services/types";
import { buildCreatePayload, buildEditPayload, fillEditFormFromLink, type LinkCreateFormState, type LinkEditFormState } from "./linkFormCodec";

export function useLinkMutations(args: {
  createForm: LinkCreateFormState;
  editForm: LinkEditFormState;
  editingId: { value: number | null };
  creating: { value: boolean };
  filters: { showArchived: boolean };
  setError: (message: string | null) => void;
  getErrorMessage: (error: unknown, fallbackMessage: string) => string;
  load: () => Promise<void>;
  resetCreateForm: () => void;
  resetEditForm: () => void;
}) {
  const { createForm, editForm, editingId, creating, filters, setError, getErrorMessage, load, resetCreateForm, resetEditForm } = args;

  async function createLink() {
    creating.value = true;
    setError(null);

    try {
      await createLinkRequest(buildCreatePayload(createForm));
      filters.showArchived = false;
      resetCreateForm();
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "创建失败"));
    } finally {
      creating.value = false;
    }
  }

  async function toggleEnabled(link: LinkDto) {
    setError(null);

    try {
      if (link.archivedAt) {
        throw new Error("短链已归档，请先恢复后再启用/禁用");
      }

      await updateLink(link.id, { enabled: !link.enabled });
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "更新失败"));
    }
  }

  function startEdit(link: LinkDto) {
    if (link.archivedAt) {
      setError("短链已归档，请先恢复后再编辑");
      return;
    }

    editingId.value = link.id;
    fillEditFormFromLink(editForm, link);
  }

  function cancelEdit() {
    editingId.value = null;
    resetEditForm();
  }

  async function saveEdit() {
    if (!editingId.value) {
      return;
    }

    setError(null);

    try {
      const originalUrl = editForm.originalUrl.trim();
      if (!originalUrl) {
        throw new Error("原始链接不能为空");
      }

      await updateLink(editingId.value, buildEditPayload(editForm));
      cancelEdit();
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "更新失败"));
    }
  }

  async function archiveLink(link: LinkDto) {
    setError(null);
    try {
      await archiveLinkRequest(link.id);
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "归档失败"));
    }
  }

  async function restoreLink(link: LinkDto) {
    setError(null);
    try {
      await restoreLinkRequest(link.id);
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "恢复失败"));
    }
  }

  async function deleteLink(link: LinkDto) {
    setError(null);

    try {
      if (!link.archivedAt) {
        throw new Error("删除前请先归档");
      }

      const confirmed = window.confirm(`确认删除短链 ${link.code}？该操作不可恢复。`);
      if (!confirmed) {
        return;
      }

      await deleteLinkRequest(link.id);
      await load();
    } catch (caught) {
      setError(getErrorMessage(caught, "删除失败"));
    }
  }

  return {
    archiveLink,
    cancelEdit,
    createLink,
    deleteLink,
    restoreLink,
    saveEdit,
    startEdit,
    toggleEnabled,
  };
}
