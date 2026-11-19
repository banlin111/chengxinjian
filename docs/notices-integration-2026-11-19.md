# 公告后端与联调交接（2026-11-19）

本批沿用契约 0.3.0，实现既有公告 DTO 对应的 8 个入口和首页公告聚合，未修改正式前端。实名资质由用户后续协调甲方；本批不依赖短信，不发送短信、不购买资源。

## 实现及客户端调用

全部路径以 `/api/v1` 为前缀，返回现有响应包。ID 为字符串，列表页码从 1 开始、默认 20 条、最大 100 条。

| 方法与路径 | 输入 / 返回 | 权限及行为 |
| --- | --- | --- |
| GET `/notices` | `PageQuery` → `Page<Notice>` | 公开、可选用户登录，发布时间倒序，ID 倒序作为同时间的稳定次序 |
| GET `/notices/{id}` | → `Notice` | 草稿、未到发布时间、归档或不存在均为 404 |
| PUT `/me/notices/{id}/read` | 无请求体 → `OK` | 必须为有效普通用户会话，标记请求处理时的公告版本；重复调用不重复记录 |
| GET `/admin/content/notices` | `AdminContentQuery` → `Page<AdminNotice>` | `content:read` 且 `dataScope=all`；支持标题 `q` 字面子串和 `status` |
| GET `/admin/content/notices/{id}` | → `AdminNotice` | 同上，后台可查看草稿、定时及已归档公告 |
| POST `/admin/content/notices` | `AdminNoticeInput` → `AdminNotice` | `content:write` 且 `dataScope=all`，UUID `Idempotency-Key` 必填 |
| PUT `/admin/content/notices/{id}` | 完整 `AdminNoticeInput` 加 `version` | 同上，陈旧版本 409 `VERSION_CONFLICT` |
| DELETE `/admin/content/notices/{id}` | `{version, reason}` → `OK` | 同上，只归档；重复归档成功，不再次增加版本或审计 |

标题非空、最长 200 个 Java 字符，正文非空、最长 10000 个 Java 字符（补充字符按 UTF-16 单元计）。正文按纯文本使用，不将服务端字符串作为可执行 HTML 渲染。`publishedAt` 必须为带时区的 ISO-8601 时间，支持 1970 至 9999 年；数据库保存 UTC，页面按需要显示上海时间。定时发布为 `status=published` 加未来时间，服务端在每次查询中检查时间，不依赖客户端计时或调度器触发。

不支持的 `categoryId/courseId/teacherId` 管理列表筛选返回 400，避免忽略筛选产生误导。创建同键同体返回原始快照，同键不同体 409 `IDEMPOTENCY_CONFLICT`；重放仍重新核对当前权限和全局数据范围。`own_content` 管理员不能管理面向所有用户的公告。

`Notice.read` 来自 `(user_id, notice_id, notice_version)` 的持久化事实；匿名访问为 false，跨会话沿用本人状态，其他用户互不影响。更新公告增加版本，使旧版本的已读状态失效。已读表示客户端显式确认收到当前公告，不构成实际阅读时长、法律同意或协议接受证据。当前契约未提供“确认指定历史版本”的入参。

归档保留公告与读记录；列表、详情、首页及标记已读都检查当前发布状态。已读写入与编辑、归档使用同一公告行锁，首次并发确认不会出现重复主键或 500。禁用用户、撤销会话不能继续标记已读。

`GET /home` 的 `notices` 现为最新最多 3 条可见公告，带当前用户已读状态。冥想、banner、推荐编排、打卡等未因本批公告上线而自动完成。

## 同事前端需要接入的位置

核对远端 `codex/frontend@223b2ea4d30d18a9a6ee2655c8b526bbb65fad84`，本批不改前端文件：

- `apps/mobile/src/services/home.ts` 已有真实 `GET /home` 分支；关闭 `VITE_USE_MOCK` 后可获得 `Home.notices`，示例数据仍为空不代表后台没有公告。
- `apps/mobile/src/pages/home/index.vue` 尚无公告渲染，需要消费 `notices`，点击传真实公告 ID。
- 当前工程没有公告列表/详情页面或公告专用 service；需新增列表、详情调用，登录时在用户进入详情后发送已读 PUT，成功后刷新首页/列表状态。不能通过 GET 自动标记已读；404 时移除已撤回内容；收到 401 时按既有用户刷新流程处理。
- Web 管理后台工程未出现在该分支。正式后台需要接入上表 5 个管理接口；时间控件提交明确时区，更新带返回的 `version`，409 提示重新读取后编辑。创建重试复用同一 UUID。

## 数据迁移与验证边界

V10 仅新增 `content_notice`、`user_notice_read` 及索引和外键，不改 V1–V9、不生成默认公告、不新增或扩大角色权限。原手机号唯一约束、用户 ID、会员及课程权益保持原有模型。

自动测试使用 H2 和本机 MySQL 的隔离 `cxj_test`，测试用管理员、用户与公告均为测试数据，不等于真实用户或第三方验收。新增测试覆盖发布时刻及 +08:00 换算、首页/列表/详情一致可见性、跨会话/跨用户已读、并发首次确认、更新版本失效、并发编辑冲突、归档保留、权限即时变化与幂等重放、严格参数、字面搜索分页、禁用用户和撤销会话。

打包 HTTP 探针 `scripts/smoke_backend.py` 新增公告创建/幂等、定时隐藏、发布/首页/详情、归档隐藏；其运行配置为本机 `local,mysql`，使用本地测试管理员，不能作为云端管理员真实验证码验收。最终部署、恢复与执行结果在本文件后续记录。

2026-11-19 本机实际执行：H2 与 MySQL 8.4.11 全量各 126 项通过（均无跳过，其中公告新增 8 项）；Python 配置与运维测试 10 项通过；`npm run check`、OpenAPI 校验、打包 JAR 在隔离 `cxj_test` / 18084 的分类、讲师、公告 HTTP 流程通过。MySQL 原先停止，本轮从原数据目录恢复，未初始化新目录，未迁移或清理 `cxj_dev`。打包探针已停止、会话已退出、测试公告已归档。证据在忽略的 `server/target/notices-{full-h2,full-mysql,python,contract,openapi,http}.log`。

这些结果属于代码与自动化/本机 HTTP 检查。本轮未执行真实微信、COS、短信、验证码交互，也未执行正式前端或真机公告验收；原课程、账号和权益用例随全量回归通过，不代表长课程、购买支付等真实业务验收。
