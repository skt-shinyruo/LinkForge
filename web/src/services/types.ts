export type ApiResponse<T> = {
  code: number;
  message: string;
  data?: T;
  requestId?: string;
};

export type AuthResponse = {
  token?: string | null;
  user: {
    id: number;
    tenantId: number;
    email: string;
    roles: string[];
  };
};

export type LinkRedirectStatusCode = 301 | 302;

export type QueryForwardMode = "OFF" | "ALLOWLIST" | "ALL";

export type LinkDto = {
  id: number;
  tenantId: number;
  code: string;
  shortUrl: string;
  originalUrl: string;
  note?: string | null;
  enabled: boolean;
  expiresAt?: string | null;
  archivedAt?: string | null;
  redirectStatusCode?: LinkRedirectStatusCode | null;
  previewEnabled?: boolean;
  unavailableLandingUrl?: string | null;
  queryForwardMode?: QueryForwardMode | null;
  queryForwardAllowlist?: string[];
  tags?: string[];
  createdAt?: string | null;
};

export type PageResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type LinkListQuery = {
  archived?: boolean;
  enabled?: boolean;
  keyword?: string;
  tag?: string;
  page?: number;
  size?: number;
};

export type CreateLinkRequest = {
  originalUrl: string;
  note?: string;
  expiresAt?: string;
  enabled?: boolean;
  customCode?: string;
  tags?: string[];
  redirectStatusCode?: LinkRedirectStatusCode;
  previewEnabled?: boolean;
  unavailableLandingUrl?: string;
  queryForwardMode?: QueryForwardMode;
  queryForwardAllowlist?: string[];
};

export type UpdateLinkRequest = {
  originalUrl?: string;
  note?: string;
  expiresAt?: string;
  clearExpiresAt?: boolean;
  enabled?: boolean;
  tags?: string[];
  redirectStatusCode?: LinkRedirectStatusCode;
  clearRedirectStatusCode?: boolean;
  previewEnabled?: boolean;
  unavailableLandingUrl?: string;
  queryForwardMode?: QueryForwardMode;
  clearQueryForwardMode?: boolean;
  queryForwardAllowlist?: string[];
};

export type LinkExportQuery = {
  page?: number;
  size?: number;
};

export type LinkImportResult = {
  success: number;
  failed: number;
  errors: string[];
};

export type DailyStat = {
  day: string; // yyyy-MM-dd
  pv: number;
  uv: number;
};

export type TopLinkStat = {
  linkId: number;
  code: string | null;
  originalUrl: string | null;
  pv: number;
  uv: number;
  deleted: boolean;
};

export type StatsRangeQuery = {
  from: string;
  to: string;
};

export type TopLinkSortBy = "pv" | "uv";

export type TopLinksQuery = StatsRangeQuery & {
  limit?: number;
  sortBy?: TopLinkSortBy;
};

export type TagDto = {
  id: number;
  name: string;
};

export type CreateTagRequest = {
  name: string;
};
