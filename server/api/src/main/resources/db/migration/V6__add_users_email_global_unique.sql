-- IAM 加固：email 全局唯一（用于简化登录，不再要求选择租户）
--
-- 数据清理策略（已确认）：若存在跨租户重复 email，则 **全部删除** 这些 email 对应的用户（不保留任意一个）。
-- 说明：
-- - 本仓库处于开发阶段，允许破坏性清理；上线前建议先做预检并采用更稳妥的迁移策略。
-- - 由于当前表结构未声明外键，这里额外清理 `user_roles`，避免残留脏数据。

-- 1) 删除重复 email 对应的角色关联（避免残留）
DELETE ur
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN (
  SELECT email
  FROM users
  GROUP BY email
  HAVING COUNT(*) > 1
) dup ON dup.email = u.email;

-- 2) 删除重复 email 对应的用户（不保留任意一个）
DELETE u
FROM users u
JOIN (
  SELECT email
  FROM users
  GROUP BY email
  HAVING COUNT(*) > 1
) dup ON dup.email = u.email;

ALTER TABLE users
  ADD UNIQUE KEY uk_users_email (email);
