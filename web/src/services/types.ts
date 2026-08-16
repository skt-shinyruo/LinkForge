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
  applicationId?: number | null;
  domainId?: number | null;
  lifecycleState?: string;
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
  pendingApproval?: boolean;
  approvalRequestId?: number | null;
  requestedOriginalUrl?: string | null;
};

export type PageResponse<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
  hasMore?: boolean;
  nextCursor?: string | null;
};

export type CursorPageResponse<T> = {
  items: T[];
  hasMore: boolean;
  nextCursor: string | null;
};

export type LinkListQuery = {
  applicationId?: number;
  archived?: boolean;
  enabled?: boolean;
  keyword?: string;
  tag?: string;
  page?: number;
  size?: number;
  cursor?: string;
  includeTotal?: boolean;
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
  applicationId?: number;
  domainId?: number;
  lifecycleState?: string;
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
  lifecycleState?: string;
};

export type LinkExportQuery = {
  applicationId?: number;
  archived?: boolean;
  enabled?: boolean;
  keyword?: string;
  tag?: string;
  page?: number;
  size?: number;
};

export type LinkImportQuery = {
  applicationId?: number;
  domainId?: number;
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
  shortUrl: string | null;
  originalUrl: string | null;
  pv: number;
  uv: number;
  deleted: boolean;
};

export type StatsRangeQuery = {
  from: string;
  to: string;
  applicationId?: number;
};

export type TopLinkSortBy = "pv" | "uv";

export type TopLinksQuery = StatsRangeQuery & {
  applicationId?: number;
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

export type ApplicationDto = {
  id: number;
  tenantId: number;
  applicationKey: string;
  displayName: string;
};

export type CreateApplicationRequest = {
  applicationKey: string;
  displayName: string;
};

export type DomainScope = "TENANT_SHARED" | "APPLICATION_DEDICATED";

export type DomainDto = {
  id: number;
  tenantId: number;
  applicationId?: number | null;
  hostname: string;
  scope: DomainScope;
};

export type CreateDomainRequest = {
  hostname: string;
};

export type ApiKeyDto = {
  id: number;
  applicationId?: number | null;
  name: string;
  status: string;
  lastUsedAt?: string | null;
  createdAt?: string | null;
};

export type CreateApiKeyRequest = {
  applicationId: number;
  name: string;
};

export type CreateApiKeyResponse = {
  id: number;
  name: string;
  apiKey: string;
};

export type ApprovalRequestDto = {
  id: number;
  tenantId: number;
  operationType: string;
  targetApplicationId?: number | null;
  requestedByUserId: number;
  requestedByEmail: string;
  status: string;
  approverUserId?: number | null;
  approverEmail?: string | null;
  decisionReason?: string | null;
};

export type ApproveRequest = {
  reason?: string;
};

export type ApprovalListQuery = {
  status?: string;
  limit?: number;
  cursor?: string;
};

export type AuditLogDto = {
  id: number;
  tenantId: number;
  actorUserId: number;
  actorEmail: string;
  actionType: string;
  resourceType: string;
  resourceId: string;
  requestId?: number | null;
  createdAt: string;
};

export type AuditLogListQuery = {
  actionType?: string;
  resourceType?: string;
  limit?: number;
  cursor?: string;
};
