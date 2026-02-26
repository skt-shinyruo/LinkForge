# iam

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/iam.md](../wiki/modules/iam.md)

## 职责

- 多租户：tenants/users/user_roles
- 认证：JWT Bearer（管理后台/自助创建），可选 JWT HttpOnly Cookie 模式
- 授权：角色权限（租户管理员/普通用户；预留 sys_admin）
- OpenAPI：API Key 生命周期管理（创建/禁用/启用/轮换；服务端仅存哈希）

## 行为规范

### 认证与会话
- Bearer 模式使用 `Authorization: Bearer <token>`
- Cookie 模式下写接口需 CSRF 防护（双提交 cookie：`XSRF-TOKEN` + `X-XSRF-TOKEN`）

### 安全
- 任何密码仅存哈希（不落明文，不写入日志/知识库）
- API Key 仅在创建/轮换时返回一次明文；服务端仅保存 `key_hash`

## 依赖关系

```yaml
依赖:
  - api（协议落地与管理接口）
  - platform（跨服务 SSOT / 公共契约）
  - mysql
  - redis（可选：会话/节流/黑名单等）
被依赖:
  - shortlink
  - analytics
  - admin-ui
```

