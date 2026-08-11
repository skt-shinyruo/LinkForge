import type { RuntimeValidator } from "./apiContract";
import type {
  ApiKeyDto,
  ApplicationDto,
  ApprovalRequestDto,
  AuditLogDto,
  AuthResponse,
  CreateApiKeyResponse,
  DailyStat,
  DomainDto,
  LinkDto,
  LinkImportResult,
  PageResponse,
  TagDto,
  TopLinkStat,
} from "./types";

type UnknownRecord = Record<string, unknown>;

function record(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function numberField(value: UnknownRecord, key: string): boolean {
  return typeof value[key] === "number" && Number.isFinite(value[key]);
}

function stringField(value: UnknownRecord, key: string): boolean {
  return typeof value[key] === "string";
}

function booleanField(value: UnknownRecord, key: string): boolean {
  return typeof value[key] === "boolean";
}

function optionalNumber(value: UnknownRecord, key: string): boolean {
  return value[key] === undefined || value[key] === null || numberField(value, key);
}

function optionalString(value: UnknownRecord, key: string): boolean {
  return value[key] === undefined || value[key] === null || stringField(value, key);
}

function stringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string");
}

export function arrayOf<T>(validator: RuntimeValidator<T>): RuntimeValidator<T[]> {
  return (value: unknown): value is T[] => Array.isArray(value) && value.every(validator);
}

export const isLinkDto: RuntimeValidator<LinkDto> = (value): value is LinkDto =>
  record(value) &&
  numberField(value, "id") &&
  numberField(value, "tenantId") &&
  stringField(value, "code") &&
  stringField(value, "shortUrl") &&
  stringField(value, "originalUrl") &&
  booleanField(value, "enabled") &&
  optionalNumber(value, "applicationId") &&
  optionalNumber(value, "domainId") &&
  (value.tags === undefined || stringArray(value.tags));

export function pageOf<T>(validator: RuntimeValidator<T>): RuntimeValidator<PageResponse<T>> {
  return (value: unknown): value is PageResponse<T> =>
    record(value) &&
    Array.isArray(value.items) &&
    value.items.every(validator) &&
    numberField(value, "total") &&
    numberField(value, "page") &&
    numberField(value, "size") &&
    (value.hasMore === undefined || booleanField(value, "hasMore")) &&
    optionalString(value, "nextCursor");
}

export const isApplicationDto: RuntimeValidator<ApplicationDto> = (value): value is ApplicationDto =>
  record(value) && numberField(value, "id") && numberField(value, "tenantId") &&
  stringField(value, "applicationKey") && stringField(value, "displayName");

export const isDomainDto: RuntimeValidator<DomainDto> = (value): value is DomainDto =>
  record(value) && numberField(value, "id") && numberField(value, "tenantId") &&
  optionalNumber(value, "applicationId") && stringField(value, "hostname") &&
  (value.scope === "TENANT_SHARED" || value.scope === "APPLICATION_DEDICATED");

export const isApiKeyDto: RuntimeValidator<ApiKeyDto> = (value): value is ApiKeyDto =>
  record(value) && numberField(value, "id") && optionalNumber(value, "applicationId") &&
  stringField(value, "name") && stringField(value, "status") &&
  optionalString(value, "lastUsedAt") && optionalString(value, "createdAt");

export const isCreateApiKeyResponse: RuntimeValidator<CreateApiKeyResponse> =
  (value): value is CreateApiKeyResponse =>
    record(value) && numberField(value, "id") && stringField(value, "name") && stringField(value, "apiKey");

export const isDailyStat: RuntimeValidator<DailyStat> = (value): value is DailyStat =>
  record(value) && stringField(value, "day") && numberField(value, "pv") && numberField(value, "uv");

export const isTopLinkStat: RuntimeValidator<TopLinkStat> = (value): value is TopLinkStat =>
  record(value) && numberField(value, "linkId") && optionalString(value, "code") &&
  optionalString(value, "shortUrl") && optionalString(value, "originalUrl") &&
  numberField(value, "pv") && numberField(value, "uv") && booleanField(value, "deleted");

export const isTagDto: RuntimeValidator<TagDto> = (value): value is TagDto =>
  record(value) && numberField(value, "id") && stringField(value, "name");

export const isApprovalRequestDto: RuntimeValidator<ApprovalRequestDto> =
  (value): value is ApprovalRequestDto =>
    record(value) && numberField(value, "id") && numberField(value, "tenantId") &&
    stringField(value, "operationType") && optionalNumber(value, "targetApplicationId") &&
    numberField(value, "requestedByUserId") && stringField(value, "requestedByEmail") &&
    stringField(value, "status") && optionalNumber(value, "approverUserId") &&
    optionalString(value, "approverEmail") && optionalString(value, "decisionReason");

export const isAuditLogDto: RuntimeValidator<AuditLogDto> = (value): value is AuditLogDto =>
  record(value) && numberField(value, "id") && numberField(value, "tenantId") &&
  numberField(value, "actorUserId") && stringField(value, "actorEmail") &&
  stringField(value, "actionType") && stringField(value, "resourceType") &&
  stringField(value, "resourceId") && optionalNumber(value, "requestId") &&
  optionalString(value, "beforeSnapshot") && optionalString(value, "afterSnapshot") &&
  stringField(value, "createdAt");

export const isLinkImportResult: RuntimeValidator<LinkImportResult> =
  (value): value is LinkImportResult =>
    record(value) && numberField(value, "success") && numberField(value, "failed") && stringArray(value.errors);

export const isAuthUser: RuntimeValidator<AuthResponse["user"]> =
  (value): value is AuthResponse["user"] =>
    record(value) && numberField(value, "id") && numberField(value, "tenantId") &&
    stringField(value, "email") && stringArray(value.roles);

export const isAuthResponse: RuntimeValidator<AuthResponse> = (value): value is AuthResponse =>
  record(value) && optionalString(value, "token") && isAuthUser(value.user);
