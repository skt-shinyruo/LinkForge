export type ApiResponse<T> = {
  code: number;
  message: string;
  data?: T;
  requestId?: string;
};

export type AuthResponse = {
  token: string;
  user: {
    id: number;
    tenantId: number;
    email: string;
    roles: string[];
  };
};

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
  redirectStatusCode?: number | null;
  previewEnabled?: boolean;
  unavailableLandingUrl?: string | null;
  queryForwardMode?: string | null;
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

export type TagDto = {
  id: number;
  name: string;
};
