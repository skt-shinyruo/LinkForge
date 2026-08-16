-- Bounded tenant-scoped keyset scans for Governance summary lists.

CREATE INDEX idx_approval_requests_tenant_created_id
  ON approval_requests (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_approval_requests_tenant_status_created_id
  ON approval_requests (tenant_id, status, created_at DESC, id DESC);

CREATE INDEX idx_audit_logs_tenant_created_id
  ON audit_logs (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_audit_logs_tenant_action_created_id
  ON audit_logs (tenant_id, action_type, created_at DESC, id DESC);

CREATE INDEX idx_audit_logs_tenant_resource_created_id
  ON audit_logs (tenant_id, resource_type, created_at DESC, id DESC);

CREATE INDEX idx_audit_logs_tenant_action_resource_created_id
  ON audit_logs (tenant_id, action_type, resource_type, created_at DESC, id DESC);
