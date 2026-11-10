# 微信用户阶段实施与验收记录

日期：2026-11-09（Asia/Shanghai）。本记录为当天重新执行的结果，不复用 2026-11-06 的历史测试结论。

## 接手核对

实际仓库为 chengxinjian，分支 codex/backend-foundation，HEAD 0120d62。已有后端主要为本地未提交内容，已原样保留并继续开发；没有 reset、clean、重新克隆、提交或推送。项目内未发现新增 AGENTS.md。已读取交接、仓库 README、需求 PDF、开发说明、TypeScript 契约、OpenAPI、原有实现/测试，以及微信开发工具技能 0.9.21。

## 本轮新增

7 个业务接口：GET /agreements/{type}、POST /auth/wechat/login、POST /auth/refresh、POST /auth/logout、GET /me、GET /me/settings、PUT /me/settings，均加 /api/v1。连同原有 10 个，当前实际实现 17 个；全量契约仍为 220 个操作、158 个 schema。

新增 V2 迁移共 7 张表：app_user、wechat_identity、user_auth_session、wechat_code_use、agreement_version、user_agreement_consent、user_settings（V1 原有 10 张，总计业务表 17 张，不含 Flyway 历史表）。V1 未改写，迁移在原有 cxj_test、cxj_dev 上均通过校验并升级至 V2。

用户与管理员各自拥有独立账号和会话表。用户身份为 UUID，微信 AppID+OpenID 二进制唯一；UnionID 可空，不自动合并账号。用户会话 audience 固定 user，令牌随机生成、摘要落库；刷新轮换两种令牌，旧凭据失效，封禁即时检查，登出仅撤销本人指定会话。登录/刷新不被陈旧 Authorization 阻断。现阶段新微信用户无手机号和付费权益，返回 phoneBindingRequired=true、空手机号及非会员状态，如实反映当前数据。

code2Session 使用官方 HTTPS 地址，连接/请求超时为 3/5 秒，禁重定向、不自动重试 code。无效/已使用 code 为 400，风险拦截为 403，供应商故障及配置缺失为 503；不输出供应商原始报文。AppSecret 仅在服务器进程，session_key 不回传、不保存。用户登录按来源 IP 限流，不信任客户端伪造的转发头。

协议在事务内记录类型、版本、首次同意时间和 requestId。新用户必须同意当前隐私政策及用户协议；历史同意保留，版本升级需补充同意。local 测试文本显著标注未经批准，非 local 禁止使用；正式文本未配置时拒绝注册。正式协议尚无甲方批准版本，本轮已拟两份草案，位于 docs/agreements，未启用。

Settings 响应新增可选 version，前端传回版本可检测并发修改，冲突为 409；旧客户端省略版本的兼容行为为事务串行、最后写入生效。倍速、提醒时间、未知字段、歧义 JSON 和资产引用均有校验。提醒保存尚不等于提醒任务已实现；媒体模块建立前不接收伪造的结束音资产。

## 本轮实际发现并修复的问题

真实 MySQL 首次并发注册发现 REPEATABLE READ 旧快照读不到另一请求已提交的协议记录，导致重复键错误。注册事务改为 READ COMMITTED，同时用身份行锁和唯一约束串行归属创建；并发两个不同 code 只创建一个用户且两个登录均成功。此处不以 H2 通过替代真实 MySQL 证据。

补充四路并发老账号登录时，又在真实 MySQL 复现 duplicate INSERT 获得共享锁再升级 FOR UPDATE 的死锁。身份记录和限流记录改为无修改 upsert，直接取得排他锁，消除锁升级；保留四路并发回归用例。修复前的可复现失败保存在 server/target/stage2-mysql-concurrency.log。

保留原有有效管理员令牌用于用户端返回 403 的行为，失效凭据仍返回 401。用户令牌不能用于管理员路径，双方刷新令牌不能跨 audience 使用。

## 自动化与打包验证

| 验证 | 当日结果 | 本地证据 |
| --- | --- | --- |
| 接手前基线 | 20 项通过 | server/target/baseline-2026-11-09.log |
| H2 最终验证 | 43 项通过，0 失败/错误 | server/target/stage2-h2.log |
| MySQL 8.4.11 最终验证 | 43 项通过，0 失败/错误 | server/target/stage2-mysql.log |
| 私有配置加载器 | 4 项通过 | python3 scripts/test_local_backend.py |
| 契约生成检查 | 220 operations / 158 schemas | server/target/stage2-contracts.log |
| 打包 jar + 原生 MySQL + HTTP | PASS | server/target/stage2-http-smoke.log、server/target/http-smoke.log |
| Git 可见源文件密钥核对 | 未发现本地 WX_APP_SECRET | 扫描 Git 可见文件、服务端及 MCP 执行日志；不打印密钥 |

新增测试覆盖首次/重复登录、并发创建、AppID 隔离及大小写、UnionID 不合并、重复 code、协议升级/历史/未来版本、非 local 拒绝测试协议、注册整体回滚、封禁/注销处理中拒绝、令牌 audience 隔离、过期/轮换/刷新重放/并发刷新、会话归属、设置版本冲突、严格 JSON、OpenAPI 字段、供应商解析/超时/错误码等。供应商自动化测试使用替身，仅以下真实联调部分调用正式微信接口。

私有配置加载器只在显式 --wechat 时读取 .env.wechat.local，仅传入 WX_APP_ID/WX_APP_SECRET；校验本人所有、0600、普通文件、不跟随符号链接，不执行文件内容，不回显配置。& 等字符按普通值传递。测试数据库辅助命令限定本机 33306/cxj_test。

## 真实微信联调结果

从官方地址安装微信开发者工具 Stable ARM64 2.02.2609250，并安装 uv 与 wechat-devtools-mcp 0.9.21；使用现有 Node 24.22.1。当前聊天未直接暴露微信工具，已通过标准 MCP stdio 客户端实际调用同一技能工具，而不是编造工具结果；未读取内建 MCP Token。

用户确认已添加开发者权限，IDE 实际 is_login=true。用户在设置→安全开启服务端口后，CLI 可用。独立最小工程为 tools/wechat-login-probe，包含生成的 project.config.json；没有把 server/ 当作小程序路径。自动化 start 结果 verified=true、TCP/WS 均就绪；compile 的 errors/wxml_errors 为空、automator_verified=true；page_data 确认 pages/login/index。9420 全程串行使用。

首次启动检查曾报告 IDE GitLens 插件远程图标网络错误。经查看实际项目页面、重新打开后启动检查正常（project_opened/cdp_ready/miniprogram_targets_ready 均为 true），随后重新 start/compile/page_data 验证，没有继续依赖报错的启动结果。

真实流程：

1. 后端连接 cxj_dev，readiness=UP；小程序读取两份当前 local 测试协议。
2. 在明确测试语境下同意测试文本；真实 wx.login 获取 code 并发送至本地后端，服务端调用正式微信 code2Session；首次登录返回 isNewUser=true。
3. GET /me 与 GET /me/settings 成功，状态 active、主题 paper、设置版本 1。
4. POST /auth/refresh 成功轮换双令牌，再次读取本人成功。
5. POST /auth/logout 成功，再次真实 wx.login → code2Session 登录，isNewUser=false。
6. MySQL 核对仍只有 1 个用户、1 个微信身份、2 条协议同意记录，两个会话分别记录，未重复注册。
7. 最后退出第二次会话；修复并发死锁后重启最终后端，再用真实微信复验登录、读取、刷新和退出，全部成功，active_user_sessions=0。未打印 code、令牌、OpenID、session_key 或密钥。

真实 MCP 结果的本地副本位于 server/target/wechat-real-acceptance.log（仅状态，不含 code/令牌/密钥）。

这证明本次本地配置能够完成真实微信登录；不是真机、公网 HTTPS、正式协议、支付或上线验收。未使用 wx API Mock，未上传、提审或发布。原生 MySQL 与后端联调服务目前保持本机运行，API 监听 127.0.0.1:18080；最终实例日志为 server/target/stage2-local-final.log；旧日志中的进程 143 是主动重启或切换端口时的终止。

## 当前边界与接续

- 尚未完成正式协议确认、未成年人核验、手机号/短信、改绑、找回密码、注销执行与数据保留任务；这些仍在总范围。协议草案不能在相关承诺未实现时直接发布。
- 管理员生产验证码和安全初始化、完整 RBAC 仍未实现；既有 local 管理员仅用于开发。
- 讲师、媒资、课程/课节、会员/购买权益、播放授权及后续学习/练习/支付等仍需分阶段实施，本轮未宣称已完成。
- 尚无服务器/域名，COS/短信/直播/支付等资源未确认开通；不因账号存在推定可用。本轮真实登录不需要云主账号密码或主账号 API 密钥。
- GitHub Actions 已补充配置加载器检查，尚未推送触发；Docker/compose 尚未在本机执行。
- 当前甲方只需审阅协议草案，补充主体全称、联系地址、有效客服/隐私联系邮箱电话，并确认适用年龄方案；销售价格、录制和奖励细则在各功能启用前分批索取。

官方参考：[code2Session](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-login/api_code2session.html)、[开发工具下载](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。官方页面已于当日直接读取核对接口地址和主要错误码；公开网页抓取器无法打开时，通过官方 HTTPS 页面实际读取，没有依赖社区文章代替官方规范。
