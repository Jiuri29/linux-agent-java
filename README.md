# kylin-aiops-agent（Java 版）

面向麒麟操作系统的安全智能运维 Agent 的 Java 重写版，移植自 [diaofenyuan/Linux-agent](https://github.com/diaofenyuan/Linux-agent)。

- 后端：Spring Boot 3.x + Java 17，内嵌 Tomcat，默认监听 `127.0.0.1:18080`
- 存储：与原版一致的 JSON/JSONL 本地文件（无外部数据库）
- 前端：复用原仓库 `frontend/`（Vue 3 + Vite）构建产物，后端静态托管

## 环境要求

- JDK 17+
- Maven 3.8+

## 快速启动

```bash
mvn spring-boot:run

# 或打包运行
mvn -DskipTests package
java -jar target/kylin-aiops-agent-1.0.0.jar
```

健康检查：

```bash
curl http://127.0.0.1:18080/api/health
```

## 目录结构

```
src/main/java/com/kylin/agent/
├── ApiApplication.java      # 入口（对应 backend/ops_agent_api.py）
├── config/                  # AppProperties（OPS_AGENT_* 环境变量）、WebConfig
├── web/                     # 统一响应、错误码、异常、请求ID、分页
├── security/                # 会话、PBKDF2 密码、CSRF、RBAC（identity.py/rbac.py）
├── repository/              # JSON 文件访问（backend/repositories/*）
├── service/                 # 业务服务（backend/services/*）
├── controller/              # 路由适配（backend/api/*_routes.py）
└── audit/                   # 审计 JSONL Hash 链（audit/store.py）
```

## 与原项目的模块映射

| 原项目（Python） | 本项目（Java） |
| --- | --- |
| `backend/ops_agent_api.py` | `ApiApplication` + Spring MVC |
| `backend/core/response.py errors.py` | `web/ApiResponse` `web/ErrorCode` |
| `backend/core/context.py pagination.py` | `web/ReqCtx` `web/PageResult` |
| `backend/identity.py rbac.py` | `security/*` |
| `backend/repositories/*` | `repository/*` |
| `backend/services/*` | `service/*` |
| `backend/api/*_routes.py` | `controller/*` |
| `audit/store.py` | `audit/AuditService` |
| `realtime_monitor.py alert_rules.py os_collectors.py` | 待建：`monitor/` |
| `executor/ guardrails/ mcp_tools/ agent/` | 待建：`executor/ guardrails/ mcp/ chatops/` |

## 配置

沿用原版环境变量名（前缀 `OPS_AGENT_`），未设置时默认写入 `./.runtime/`：

`OPS_AGENT_DATA_DIR`、`OPS_AGENT_AUDIT_LOG`、`OPS_AGENT_AUTH_STORE`、`OPS_AGENT_ASSET_STORE`、`OPS_AGENT_SERVICE_CENTER_STORE`、`OPS_AGENT_INCIDENT_STORE`、`OPS_AGENT_API_CONFIG`、`OPS_AGENT_EXECUTION_STORE`、`OPS_AGENT_GUARDRAILS_STORE`、`OPS_AGENT_GUARDRAILS_APPROVAL_STORE`、`OPS_AGENT_TERMINAL_LOG_DIR`、`OPS_AGENT_ORCHESTRATION_DIR`、`OPS_AGENT_MCP_STORE`、`OPS_AGENT_MCP_INVOCATION_LOG`、`OPS_AGENT_CHATOPS_STORE`、`OPS_AGENT_ALERT_RULES_CONFIG`、`OPS_AGENT_ALERT_STATE_STORE`、`OPS_AGENT_METRICS_HISTORY_STORE`、`OPS_AGENT_LATEST_SNAPSHOT_STORE`、`OPS_AGENT_TEST_RUN_DIR`、`OPS_AGENT_PORT`、`OPS_AGENT_BIND`、`OPS_AGENT_FRONTEND_DIR`

## 已实现

- 统一响应 `{ok, code, message, data, meta, request_id}` 与稳定错误码（ok/validation_error/not_found/unauthenticated/permission_denied/csrf_required/internal_error 等）
- 请求 ID 过滤器（`X-Request-Id`）、审计日志 Hash 链（SHA-256，支持 `/api/v1/audit/verify` 校验）
- 认证链路：`GET /api/auth/bootstrap` → `POST /api/auth/init-admin` → `POST /api/auth/login`
- HttpOnly Cookie `ops_session` 会话、写请求 `X-CSRF-Token` 校验、RBAC 权限点拦截
- PBKDF2WithHmacSHA256 密码哈希、登录失败锁定（5 次锁 15 分钟）

## 路线图

1. ✅ 工程骨架 + 统一响应/错误码 + 健康检查
2. ✅ 认证/会话/CSRF/RBAC + 审计 Hash 链
3. ⬜ 资产中心 → 服务目录 → 事件工单
4. ⬜ 监控采集（/proc + 只读命令）+ 告警规则
5. ⬜ 受控终端 / 文件边界 / 护栏 / 结构化审批执行器
6. ⬜ ChatOps / MCP 工具 / 执行编排 / 自动化验收
7. ⬜ 契约测试对齐原版 `tests/contract`、`tests/api` 断言
8. ⬜ systemd/RPM/离线包交付（jpackage）

## 运行测试

```bash
mvn test
```

## 接入前端

克隆原仓库并构建前端后，将产物目录通过环境变量指向即可：

```bash
git clone https://github.com/diaofenyuan/Linux-agent ../Linux-agent
npm --prefix ../Linux-agent/frontend install
npm --prefix ../Linux-agent/frontend run build
OPS_AGENT_FRONTEND_DIR=../Linux-agent/frontend/build mvn spring-boot:run
```
