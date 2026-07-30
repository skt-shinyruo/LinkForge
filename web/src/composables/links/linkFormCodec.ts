import type { CreateLinkRequest, LinkDto, QueryForwardMode, UpdateLinkRequest } from "../../services/types";

/** HTTP 跳转状态码在 select 中的显示值；空值表示继承全局默认。 */
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

/**
 * 把逗号/换行输入规范化为去重保序的 allowlist；空输入返回 null，供创建请求表达“未设置”。
 * 项数和 pattern 合法性最终由后端领域规则裁决。
 */
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

/** 把标签输入去空、去重并截断到 UI 支持的 20 项；后端仍是长度与权限事实源。 */
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

/**
 * 把浏览器本地 `datetime-local` 值转换为 UTC ISO instant；空值或非法值返回 undefined。
 * 该转换意味着用户输入按浏览器当前时区解释。
 */
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

/** 用后端快照原地初始化编辑表单；空策略字段保持“继承默认”的 UI 语义。 */
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

/** 构造创建 payload；空可选字段省略，不发送 clear flags。 */
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

/**
 * 构造完整编辑 payload。
 *
 * 空过期时间、redirect status 和 query mode 分别转换为对应 clear flag，且不会同时发送新值；
 * allowlist/tags 的空数组表示显式清空。目标地址变更是否进入审批由后端决定。
 */
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
