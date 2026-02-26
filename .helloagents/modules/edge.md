# edge

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/edge.md](../wiki/modules/edge.md)

## 职责

- 提供公开跳转入口：`/r/{code}`
- 基于 Redis 缓存加速短码解析，必要时只读回源 MySQL 并写回缓存
- 在跳转链路做轻量统计写入（Redis 结构），由 API 服务增量 flush 落库
- 提供可配置的基础风控能力（可信代理链、安全取 IP、限流、黑白名单、bot 降频等）

## 行为规范

### 性能与依赖约束
- Edge 侧避免引入 JPA：只做只读回源（JDBC），降低依赖与启动成本
- 对明显非法短码快速拒绝，降低无效请求的资源消耗

### 安全与风控
- IP 获取必须经过可信代理链校验（按部署配置）
- 支持对不存在短码做负缓存（可配置 TTL），降低缓存穿透导致的回源放大

## 依赖关系

```yaml
依赖:
  - platform（跨服务 SSOT / 公共契约）
  - shortlink（解析/回源数据）
  - analytics（写入统计结构）
  - mysql（只读回源）
  - redis
被依赖:
  - 浏览器/外部访问者（公网跳转）
```

