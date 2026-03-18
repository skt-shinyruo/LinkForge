import type { CreateLinkRequest, LinkDto, QueryForwardMode, UpdateLinkRequest } from "../../services/types";

export type RedirectStatusFieldValue = "" | "301" | "302";

export type QueryForwardModeFieldValue = "" | QueryForwardMode;

export type LinkCreateFormState = {
  originalUrl: string;
  note: string;
  customCode: string;
  expiresAt: string;
  tags: string;
  enabled: boolean;
  redirectStatusCode: RedirectStatusFieldValue;
  previewEnabled: boolean;
  unavailableLandingUrl: string;
  queryForwardMode: QueryForwardModeFieldValue;
  queryForwardAllowlist: string;
};

export type LinkEditFormState = {
  originalUrl: string;
  note: string;
  expiresAt: string;
  tags: string;
  enabled: boolean;
  redirectStatusCode: RedirectStatusFieldValue;
  previewEnabled: boolean;
  unavailableLandingUrl: string;
  queryForwardMode: QueryForwardModeFieldValue;
  queryForwardAllowlist: string;
};

export function createEmptyCreateForm(): LinkCreateFormState {
  return {
    originalUrl: "",
    note: "",
    customCode: "",
    expiresAt: "",
    tags: "",
    enabled: true,
    redirectStatusCode: "",
    previewEnabled: false,
    unavailableLandingUrl: "",
    queryForwardMode: "",
    queryForwardAllowlist: "",
  };
}

export function createEmptyEditForm(): LinkEditFormState {
  return {
    originalUrl: "",
    note: "",
    expiresAt: "",
    tags: "",
    enabled: true,
    redirectStatusCode: "",
    previewEnabled: false,
    unavailableLandingUrl: "",
    queryForwardMode: "",
    queryForwardAllowlist: "",
  };
}

export function parseAllowlist(raw: string): string[] | null {
  const value = raw.trim();
  if (!value) {
    return null;
  }

  const parts = value
    .split(/[\n,]+/g)
    .map((item) => item.trim())
    .filter(Boolean);

  return Array.from(new Set(parts));
}

export function parseTags(raw: string): string[] {
  const value = raw.trim();
  if (!value) {
    return [];
  }

  const parts = value
    .split(/[\n,]+/g)
    .map((item) => item.trim())
    .filter(Boolean);

  return Array.from(new Set(parts)).slice(0, 20);
}

export function dateTimeLocalToInstantString(raw: string): string | undefined {
  const value = raw.trim();
  if (!value) {
    return undefined;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

export function instantStringToDateTimeLocalInput(value?: string | null): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

export function formatInstantLocal(value?: string | null): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

export function policySummary(link: LinkDto): string {
  const redirectStatusCode =
    link.redirectStatusCode === 301 || link.redirectStatusCode === 302
      ? String(link.redirectStatusCode)
      : "默认";
  const preview = link.previewEnabled ? "Preview" : "";
  const queryForwardMode = link.queryForwardMode || "默认";
  return [redirectStatusCode, queryForwardMode, preview].filter(Boolean).join(" / ");
}

export function statusLabel(link: LinkDto): string {
  if (link.archivedAt) {
    return "已归档";
  }
  return link.enabled ? "启用" : "禁用";
}

export function fillEditFormFromLink(editForm: LinkEditFormState, link: LinkDto) {
  editForm.originalUrl = link.originalUrl || "";
  editForm.note = link.note || "";
  editForm.expiresAt = instantStringToDateTimeLocalInput(link.expiresAt);
  editForm.tags = (link.tags || []).join(",");
  editForm.enabled = !!link.enabled;
  editForm.redirectStatusCode = link.redirectStatusCode === 301
    ? "301"
    : link.redirectStatusCode === 302
      ? "302"
      : "";
  editForm.previewEnabled = !!link.previewEnabled;
  editForm.unavailableLandingUrl = link.unavailableLandingUrl || "";
  editForm.queryForwardMode = link.queryForwardMode || "";
  editForm.queryForwardAllowlist = (link.queryForwardAllowlist || []).join(",");
}

export function buildCreatePayload(form: LinkCreateFormState): CreateLinkRequest {
  const allowlist = parseAllowlist(form.queryForwardAllowlist);
  const tags = parseTags(form.tags);
  const expiresAt = dateTimeLocalToInstantString(form.expiresAt);
  const redirectStatusCode =
    form.redirectStatusCode === "301"
      ? 301
      : form.redirectStatusCode === "302"
        ? 302
        : undefined;

  return {
    originalUrl: form.originalUrl,
    note: form.note || undefined,
    enabled: form.enabled,
    customCode: form.customCode.trim() || undefined,
    expiresAt,
    tags: tags.length > 0 ? tags : undefined,
    redirectStatusCode,
    previewEnabled: form.previewEnabled,
    unavailableLandingUrl: form.unavailableLandingUrl.trim() || undefined,
    queryForwardMode: form.queryForwardMode || undefined,
    queryForwardAllowlist: allowlist && allowlist.length > 0 ? allowlist : undefined,
  };
}

export function buildEditPayload(form: LinkEditFormState): UpdateLinkRequest {
  const allowlist = parseAllowlist(form.queryForwardAllowlist) || [];
  const expiresAt = dateTimeLocalToInstantString(form.expiresAt);
  const tags = parseTags(form.tags);
  const payload: UpdateLinkRequest = {
    originalUrl: form.originalUrl.trim(),
    note: form.note,
    enabled: form.enabled,
    previewEnabled: form.previewEnabled,
    unavailableLandingUrl: form.unavailableLandingUrl,
    queryForwardAllowlist: allowlist,
    tags,
  };

  if (expiresAt) {
    payload.expiresAt = expiresAt;
  } else {
    payload.clearExpiresAt = true;
  }

  if (form.redirectStatusCode) {
    payload.redirectStatusCode = form.redirectStatusCode === "301" ? 301 : 302;
  } else {
    payload.clearRedirectStatusCode = true;
  }

  if (form.queryForwardMode) {
    payload.queryForwardMode = form.queryForwardMode;
  } else {
    payload.clearQueryForwardMode = true;
  }

  return payload;
}
