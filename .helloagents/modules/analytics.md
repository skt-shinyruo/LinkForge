# analytics

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/analytics.md](../wiki/modules/analytics.md)

## 职责

- 统计采集：Edge 侧轻量写入 Redis（PV/UV、active-set、维度/事件结构）
- 聚合落库：API 服务定时增量 flush（active-set 驱动），落 MySQL
- 报表查询：趋势、Top 链接、维度聚合、访问明细（短期留存）

## 行为规范

### 采集最小化
- 默认只采集必要 header 与营销参数（UTM 等）
- 避免采集敏感 query（token/手机号/邮箱等）

### 落库与调度
- flush 应为增量驱动，避免全量扫描
- 多实例部署时，定时任务需互斥运行（ShedLock/Redis）

## 依赖关系

```yaml
依赖:
  - edge（写入采集侧数据）
  - api（flush/查询接口）
  - mysql
  - redis
被依赖:
  - admin-ui（统计看板）
```

