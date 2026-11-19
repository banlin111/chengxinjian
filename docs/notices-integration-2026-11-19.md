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

实现提交 `3b3554d097719d9feb403d34eacfba269bc5a289` 的 [GitHub Actions 44442047102](https://github.com/banlin111/chengxinjian/actions/runs/44442047102) 在本轮实际读取为 `completed/success`，`contracts` 与 `backend` 均成功；后者包含 H2、MySQL 与打包 HTTP 检查。后续仅补交接文档的提交不触发当前后端路径规则，不把代码提交 CI 的结果宣称为文档提交另跑了一轮 CI。

## 隔离云机部署、恢复和回滚实测

- 实现提交 `3b3554d097719d9feb403d34eacfba269bc5a289`，发布目录 `/opt/cxj/releases/3b3554d`，镜像 `cxj-api:3b3554d`。本机 JAR、上传 JAR、容器内 JAR 的 SHA256 均为 `c69f06407a26aaddd3971a8bd2bdfdf474f5695d296cfb6e4994bf557674f779`。
- 迁移前备份 `cxj-auto-20261119T020445196793Z.sql.gz`，SHA256 `fe7e12158ae967d3f0e5ab8dc91b1a8383db64ecfb61ad00f2ac8623bb5c5711`，完成异机拉取和校验后部署。云端 V1–V10 全部成功。
- 迁移后备份 `cxj-auto-20261119T020742168649Z.sql.gz`，SHA256 `98a81cb91d5738875becbce8913ea47777adaae6d0dac6433c8145bf596ef6ab`。恢复到新的隔离演练库，43 张表 CHECKSUM 和 V1–V10 一致，源库未改变，演练库已删除；异机副本 SHA256 再次核对。
- V10 只有两个新增表，不修改旧列或权限。实际短暂切回 `cxj-api:2563fa6`，在保留 V10 的数据库上启动并通过 readiness、课程目录检查，再恢复 `cxj-api:3b3554d`。最终复核新镜像、JAR 摘要、迁移、readiness、公告和首页响应均正确。此次回滚演练只覆盖无业务写入的隔离环境启动/兼容性；旧版不提供公告 API，不代表含真实新业务写入时可无条件回滚。
- 云端仍为 `mysql,prod`，非 root `app`、只读根文件系统、API 仅绑定 `127.0.0.1:18080`，没有对公网开放 HTTP。没有注入本地测试协议、管理员、固定验证码或真实供应商密钥。当前管理员、用户、公告、读记录均为 0，不能把空数据公开响应当成云端完整后台发布/用户已读验收。
- 重新建立本机 `18081` SSH 隧道（旧监听不存在），经绕过代理的 HTTP 检查：readiness 200/UP；公告、首页、课程、套餐 200/OK；不存在公告 404/NOT_FOUND；匿名管理列表与标记已读 401/UNAUTHENTICATED；隐私协议 503/AGREEMENTS_UNAVAILABLE，仍拒绝缺批准协议的正式注册。

证据保存在忽略的 `server/target/notices-staging-{prebackup,postbackup,restore,http,final}.json`、`notices-{prebackup,postbackup}-pull.json`、`notices-deploy.log`、`notices-staging-rollback.log`。这些是本机证据，不随 Git 分发；换电脑需依据本文记录或重新验证。

本机 `cxj_dev` 经只读查询仍为 V9，本轮没有运行本机开发应用；下次用新版启动开发服务前必须先备份 `cxj_dev`。本机 MySQL 已恢复监听 33306，`cxj_test` 为 V10；18084 打包探针已退出。云端当前镜像是本批代码版本。

## 仍需验收与后续工作

| 层级 | 本批实际状态 |
| --- | --- |
| 代码 | 公告 8 个入口、首页聚合、V10 已实现并提交 |
| 自动测试 | H2 / 原生 MySQL 各 126 项、Python 10 项、契约与打包 HTTP 检查通过 |
| 真实第三方开发联调 | 本批未重跑；没有真实短信或小程序验证码成功发送/校验的新证据 |
| 隔离云机 / SSH 隧道 | 部署、基础响应、迁移、备份恢复与上述回滚范围通过；认证业务全流程尚未在云端验证 |
| 公网 HTTPS | 未验收，域名仍缺 |
| 正式前端及微信真机 | 公告尚待前端接入；课程真机与授权长素材验收仍未完成 |
| 上线验收 | 未完成 |

后续可继续独立推进练习会话/白噪音/混音、搜索和运营配置等后端；涉及打卡阈值、完成口径及奖励的正式规则仍需确认。账号密码/找回/注销、支付与订单、直播等剩余范围仍按 README 保留。不得用本批公告进度折算整个产品完成率。

实名资质按用户安排由甲方后续配合。短信免费 200 条已于 11 月 16 日领取（本批未复查剩余条数），签名、模板及真机插件验证缺项见[账号交接](user-phone-integration-2026-11-16.md)；本批没有发送或付费，不重新购买。域名、批准协议、授权课程素材仍需后续提供。
