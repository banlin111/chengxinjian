# 新会话完整交接提示词

整理日期：2026-11-11（Asia/Shanghai）。以下正文可直接复制到同一本机工作区的新聊天。配套核验记录见 [本轮交接](handoff-2026-11-11.md)。此文件可进入公开仓库，不含真实凭据、实例地址或订单标识。所有快照均需在新会话重新核对。

---

请接手并继续开发「澄心涧」微信小程序的 Java 后端。先核对实际工程、Git、运行环境和云端状态，再直接实施下一阶段。不要只返回计划，不要从零搭建，不要把旧交接当成当前进度。

一、目标与验收纪律

- 我负责后端，同事负责微信小程序及 Web 管理后台正式前端。允许维护已有独立联调探针，不接管正式前端。本期目标是需求完整、真实前后端联调、微信真机验收并可正式上线的小程序，APP 后续再做。
- 范围依据是工作区《澄心涧.pdf》及实际仓库 README。外层旧 README 不覆盖仓库版本；原型仅补充设计，不自动扩入商城、积分货币、优惠券、分销、佣金、秒杀、礼物/连麦、自动续费和离线下载。
- 严格分开报告：①代码存在；②自动测试通过；③真实供应商开发联调通过；④隔离云机部署/SSH 隧道验证；⑤公网 HTTPS 联调通过；⑥正式前端及微信真机通过；⑦上线验收通过。一个层级通过不代替后续层级，也不代表整个模块完成。
- 本地合成小文件、4 秒视频、IDE 下载或 60 个接口入口存在，均不能证明完整账号、媒资、会员、课程或播放模块可上线；不得用测试个数、契约个数算产品完成率。
- 外部资源不足时继续独立可做的代码、适配、测试和部署准备，不删功能、不放宽安全门槛、不以 Mock 成功替代真实验收。报告必须带日期、环境、素材规模、覆盖范围、失败及缺口。
- 给甲方的要求只列当前必须办理的事项，写平台入口、按钮、填写内容和交付结果；开发安装、代码与配置由你处理。已有密钥和工具先核对，不重复索取。

二、仓库、Git 与协作

工作区：/Users/liuxiaopang/Codex/小程序
实际仓库：/Users/liuxiaopang/Codex/小程序/chengxinjian
远端：https://github.com/banlin111/chengxinjian.git
协作分支：codex/backend-foundation

2026-11-11 交接开始时工作树干净，本地与远端同为 dd4875a4ef373f6ea054a8835ec9dfb6c203e77b，实际 git push 返回 Everything up-to-date。本轮随后只补交接文档，最新 HEAD 必须以实际 Git 为准。关键历史：3031f9b 为阶段五代码，2274752 保留原 CI 工作流，dd4875a 补隔离部署/恢复记录；29c057d 是阶段四，0120d62 是很早的文档基线。不要回退到历史 SHA。

新会话执行 git status、git branch -vv、git log、git ls-remote 核对。保留新修改，不 reset/clean，不覆盖同事代码，不重新克隆空工程，不强推、不合入 main、不擅自发布小程序。用户已要求提交并推送当前后端及交接文档，正常分支推送已有授权。

公开仓库禁止进入真实 .env、凭据 CSV、SSH 私钥、数据库/备份、截图/日志、工具运行时、真实 project.config.json。本机 Git 走钥匙串凭据助手、本地代理和 HTTP/1.1；这些是 .git/config 本机设置，不复制成项目配置。不输出凭据，不把 token 放 remote URL。gh auth status 未登录不等于 Git 无权推送。

曾尝试修改 .github/workflows/backend.yml，推送被已有凭据缺少 workflow scope 拒绝，随后用正常新提交恢复原工作流并成功推送。不要把此历史问题误报为当前不能推送；不要为普通代码推送重索令牌。Docker/Compose 验收已在云机执行，现有 CI 没有对应部署测试 job。

三、优先阅读与冲突优先级

1. README.md、docs/handoff-2026-11-11.md、docs/backend-stage5-2026-11-10.md、docs/backend-development.md。
2. contracts/models.ts、contracts/api.ts、openapi.json，server/ 当前实现、配置、V1–V7 和测试。
3. infra/compose.yml、infra/compose.staging.yml、server/Dockerfile、server/.dockerignore、scripts/staging_release.sh、scripts/mysql_backup.py。
4. docs/backend-stage4-2026-11-10.md、docs/media-upload-contract-proposal.md、docs/cloud-setup-2026-11-10.md，理解已有上传基础。
5. docs/agreements/ 草案及 review-notes.md、tools/wechat-login-probe/README.md。
6. /Users/liuxiaopang/Codex/小程序/澄心涧.pdf。
7. /Users/liuxiaopang/Codex/小程序/.agents/skills/wechat-devtools/SKILL.md 及 references/tool_reference.md。

检查新增 AGENTS.md。11 月 9 日和 11 月 10 日旧 handoff、stage2/3/4 保留历史；旧文档里的“29 接口、没有课程/权益、头像未贯通、没有服务器、Docker 未实跑”等已被阶段五部分覆盖。当前以用户最新指令、实际代码、本提示词和阶段五证据为准，同时保留未通过的验收边界。

四、技术和契约

- Java 21 / Temurin 21.0.13+9；Spring Boot 3.5.16；Spring Security、Bean Validation、Spring JDBC；Maven Wrapper 3.9.16；Flyway 11.20.3。模块化单体，MySQL 8.4.11，H2 用于快速自动测试。
- Redis 只有开发配置，未接入业务缓存，隔离服务器未启动 Redis。上传校验和试听裁切有持久化任务，不代表通用 outbox、所有后台任务或完整运维平台完成。
- 契约 0.3.0，共 224 个 HTTP 方法/路径组合；生成器 161 个定义，OpenAPI 加 ErrorEnvelope 共 162 个 schema。当前只有 60 个业务方法/路径有实现入口，另 164 个没有实现；这 60 个也存在明确子功能限制，尤其播放只支持 lesson。
- /api/v1；ID 字符串；金额整数分/CNY；时长秒；数据库 UTC，业务自然日 Asia/Shanghai；分页从 1 开始，默认 20、最大 100。
- 成功 code/message/data/requestId/serverTime；错误真实 HTTP 状态及业务码。严格拒绝未知 JSON 字段、重复键、尾随 JSON 和歧义类型转换。创建用 UUID Idempotency-Key，更新用 version，历史订单/权益/内容引用保留。
- 修改契约先改 contracts 源，再 npm run check；不得只改生成文件。破坏性变更需双方确认。UploadTicket.formData 为字符串字典已获明确共同确认，不恢复旧五字段。保留生成器对交叉类型、字典、泛型组件名、只读预览幂等头的修复；785 个对象结构编译核对不代表 224 个接口已实现。

五、当前接口入口清单

以下均加 /api/v1；这是代码覆盖清单，不是上线完成清单。

原有 29 个：
- 管理员/分类 10 个：POST /admin/auth/login、/refresh、/logout；GET /admin/me；GET /categories；GET/POST /admin/content/categories；GET/PUT/DELETE /admin/content/categories/{id}。前三个缩写均位于 /admin/auth 下。
- 微信用户/协议/设置 7 个：GET /agreements/{type}；POST /auth/wechat/login、/auth/refresh、/auth/logout；GET /me、/me/settings；PUT /me/settings。
- 讲师/关注 8 个：GET /teachers、/teachers/{id}；PUT /me/follows/{teacherId}；GET/POST /admin/content/teachers；GET/PUT/DELETE /admin/content/teachers/{id}。
- 媒资 4 个：POST /uploads、/uploads/{id}/complete；GET /assets/{id}；POST /admin/assets/{id}/preview。

阶段五新增 31 个：
- 课程/课节 13 个：GET /courses、/courses/{id}、/courses/{id}/lessons；GET/POST /admin/content/courses、/admin/content/lessons；GET/PUT/DELETE /admin/content/courses/{id}、/admin/content/lessons/{id}。
- 处理与编辑审核 4 个：GET /admin/assets/{id}/processing、/admin/assets/processing-failures；PUT /admin/assets/{id}/review；POST /admin/assets/{id}/processing/retry。
- 会员套餐 6 个：GET /membership/plans；GET/POST /admin/content/membership-plans；GET/PUT/DELETE /admin/content/membership-plans/{id}。
- 权益 5 个：GET /me/membership、/me/entitlements；GET/POST /admin/users/{id}/grants；POST /admin/grants/{id}/revoke。
- 课节播放 3 个：POST /playbacks、/playbacks/{id}/renew、/playbacks/{id}/events。

另有不计入 JSON 接口数量的 GET /media/images/{id} 返回 JPEG 字节，以及 actuator health/readiness/liveness。真实业务检查前必须等待 readiness=UP。

六、现有行为与必须保留的安全边界

认证与用户：BCrypt；随机 opaque access/refresh token，库内仅 SHA-256 摘要，默认 2 小时/30 天；刷新双令牌轮换，旧值失效，退出撤销目标会话；禁用和权限变化后续请求实时生效。不是 JWT，不要求甲方提供 TOKEN_SIGNING_KEY。用户与管理员独立表、会话及 audience，不把用户塞入 admin_account；过期 accessToken 不阻断合法 refresh。

微信 code2Session 真实 HTTPS，AppID+OpenID 唯一、UUID 业务 ID，UnionID 可缺；处理超时、无效/已消费 code、并发和供应商错误；不保存/下发 session_key/AppSecret。注册事务写用户、协议同意和会话；local 测试协议排除在非 local 环境外，正式协议未配置时拒绝注册。Settings.version 为兼容旧客户端可省略，响应总带；传入检测乐观锁，省略事务串行最后写入生效。提醒仅存偏好，未发送通知。

分类/讲师/课程：权限、筛选分页、发布归档、version、幂等快照、审计及 own_content 范围。全局资源不能被 own_content 越权创建或管理。讲师关联现有用户且唯一，动态角色，不随意重绑。阶段五补分类改类型/归档、讲师/课程/课节上下架及引用约束，保留历史媒资引用；课节不允许迁到别的课程。幂等重放重新检查当前数据范围。完整后台用户/账号/角色管理仍未交付。

上传：真实 COS/STS；临时 PostObject 只准隔离对象，绑定路径、类型、精确字节数、私有 ACL、SSE-COS 和期限。前端原样提交 formData 字典，file 最后；无永久 SecretKey/永久访问 ID。complete 仅确认任务，worker 有界下载核对长度/MIME/SHA256、完整图片解码或音视频流/容器/完整解码/时长，把已核验字节写入不可由客户端覆盖的 validated 对象。技术 ready、内容审核、可投放、可试听、购买权益分别校验。后台预览有数据范围，不能读取普通用户私有录制。

技术默认上限：图片 10 MiB/16MP，媒体 512 MiB/4 小时，解码 120 秒。不是甲方批准的录制规则，也未验证最大规格真实素材。FFmpeg 限制网络协议/资源且不继承云、微信、数据库密钥环境。本机 FFmpeg 由现有 imageio-ffmpeg 0.6.0 提供；Java COS 5.6.283、STS SDK 3.2.6、WebP 解码 3.15.3。

编辑审核/图片：全局 content:read + moderation:review 才可审核，默认内容管理员没有自动获得审核权限。仅接受管理员拥有的 avatar/cover/course_media，用户录制不进入该入口。已验证源图重绘为最长边 1280 的 JPEG、清除原元数据，数据库保存展示副本；/media/images/{id} 审核有效才返回、no-store、撤回后 404。必须配置真实 MEDIA_PUBLIC_ORIGIN；仅 local 可用回环 HTTP，无正式地址时不能伪造 URL。阶段五已有真实上传头像/封面的开发联调，尚无正式 HTTPS/前端展示验收。编辑人工审核不等于用户录制审核、平台内容安全或完整审核工作台完成。

上传与试听任务：数据库租约、重试、过期恢复、隔离清理；供应商故障最多自动重试 5 次，失败状态可查询并带原因/幂等键重试。上传 job failed 时资产可仍为 processing，不能报完成。失败管理只覆盖这些任务，尚无完整告警。试听按 asset+seconds 唯一登记，FFmpeg 另行裁切重新编码生成私有 trials 对象；只有成品就绪才允许试听，禁止签完整文件再让前端计时。

权益与播放：来源独立、有效期明确的会员/单课授权；free/member/purchase/member_or_purchase；直接单课权在销售策略变化后保留。人工赠送从发放时独立生效，按来源撤销；并未实现订单支付、购买续期叠加、退款冲回、打卡奖励可信发放。测试 purchased 表示该次人工课程授权，不是发生了支付。

播放只支持 lesson。每次发放/续签检查用户、全部父内容发布状态、媒资审核和当前有效权益。读签名用单对象 GetObject 临时身份，最长 120 秒且不超过权益到期；撤权拒绝新申请/续签，已签 URL 在剩余期限仍可能使用。列表、事件回执不泄露完整私有地址，后台预览不代替用户授权。

学习事件按 eventId、sequence、请求键去重；按服务端接收间隔、倍速、媒体时长和位置差限制有效秒数，seek 只更新位置；按用户/课节/当前资产恢复位置。累计有效时长达到完整时长才标记完成，是待产品确认的开发规则；试听不标记完成，上报不发打卡或奖励。学习历史列表、收藏、点赞尚未接入，目录相关字段目前初始值；recommended 当前按新建时间排序，无运营推荐编排。冥想、回放、混音播放目标尚未支持。

七、迁移、证据与真实验收范围

V1 管理员/分类；V2 微信用户/协议；V3 讲师/关注/媒资；V4 上传任务；V5 课程/课节/编辑审核/权益；V6 试听/播放/进度/引用历史；V7 会员套餐/人工授权/users 权限及权益时间精度。V1–V7 已在本地开发库和隔离云库应用。后续加 V8 等，不改已应用迁移。

2026-11-10 的历史证据（11 月 11 日读取报告不等于当天重跑）：
- H2 和原生 MySQL 8.4.11 cxj_test 各 91 项通过，无跳过；配置加载器 5 项；npm run check 和 OpenAPI 3.0 校验通过。
- 打包 Java + 本机 MySQL + 微信 IDE + 真实 code2Session/STS/COS/FFmpeg，78 检查通过。合成 PNG/JPEG/WebP/WAV/MP4；4 秒视频裁成最多 1 秒且独立完整解码；同一微信用户试听→人工单课授权下载完整→撤权回到试听；IDE wx.downloadFile、seek 零计时、renew；并非真机长时实际播放。
- 临时会话退出、人工授权撤销、测试内容归档；隔离/validated/trials 对象删除并 HEAD404，fixture_cleanup=true。普通自动测试用供应商替身，只验证确定的逻辑/异常，不能计入供应商验收。
- cxj_dev 升级前备份、全新 schema 恢复核对 23 表行数/Flyway，删除演练库；云端空白隔离库备份恢复核对 35 表 CHECKSUM、7 迁移，删除演练库，异机副本 SHA256 匹配。只覆盖这些时点的数据库，未覆盖 COS、持续备份、生产灾备或业务数据补偿。
- GitHub Actions 代码提交 2274752 的 run 42788943206 和 dd4875a 的 run 42790638601 均 contracts/backend 成功。后者在 2026-11-11 通过 GitHub API 再次核实 completed/success。CI 包含契约、加载器、H2/MySQL 和打包 HTTP；无真实云密钥、不替代供应商/真机，Docker 验收在服务器上单独完成。新提交要读取其实际 Actions 状态，不拿旧 run 顶替。

证据均在忽略的 server/target/：stage5-h2.log、stage5-mysql.log、stage5-loader.log、stage5-contract.log、stage5-openapi.log、course-cos-smoke/result.json、stage5-cloud.log、stage5-restore.json、stage5-staging-http.json、stage5-staging-restore.json、stage5-ci.json。11 月 11 日新 SSH/HTTP 核验在 handoff-2026-11-11-check.json。换电脑不会随 Git 带走，缺报告时标明仅依据已提交记录。

11月11日交接文档更新后另行重跑npm run check通过，文档链接、diff格式和私有值泄露检查通过。工作流路径过滤不覆盖纯README/docs修改，交接提交不触发Backend checks；不能说新交接SHA重新跑过后端CI。

八、现有服务器、费用及实际部署

甲方已充值，用户已明确同意腾讯云条款，并授权为这一台联调机创建/绑定项目 SSH 公钥、用余额下单和部署；不要再次询问已授权步骤，也不要重复买服务器。

2026-11-10 已购买并部署上海轻量应用服务器 cxj-api-staging：Ubuntu 24.04.4 LTS，x86_64，2 CPU/8GB、120GB SSD、8Mbps、每月1200GB流量，3个月实付360元，自动续费关闭，到期2027-02-10 17:17:53（上海时间）。购买后页面余额40元是当时快照，后续付费前重新核实；本次授权不覆盖任意额外购买、充值或新服务。实例地址/ID/订单不进入公开 Git，连接使用下述本机私有 ssh_config，无需向我索要密码。

用户已明确“暂未准备域名，先部署隔离环境”。现有实例已足够继续开发联调准备，不以缺域名停下全部工作；尚无公网 API HTTPS、备案/DNS/微信合法域名验收。

实际云机：Docker 29.1.3、Compose 2.40.3、Temurin21.0.13+9、FFmpeg8.0.1；应用非 root、只读根文件系统、tmpfs、cap_drop、no-new-privileges；镜像 cxj-api:2274752，发布目录 /opt/cxj/releases/2274752。本地代码 JAR 与容器 JAR 在部署时核过 SHA256 一致。文档提交更新不代表自动部署新镜像。

容器 cxj_staging-app-1 / cxj_staging-mysql-1；MySQL8.4.11 数据库 chengxinjian，空白独立联调库，已有字典但无开发管理员；应用 mysql,prod profiles。MySQL 无宿主机端口映射；应用只映射云机 127.0.0.1:18080。/etc/cxj/staging.env 为 root0600，不 cat 输出；Redis 未启动。没有复制本机微信/COS密钥，没有测试协议/固定验证码/local 管理员，没有 MEDIA_PUBLIC_ORIGIN。

本机连接配置 server/.local/staging-access/ssh_config，别名 cxj-staging；私钥同目录 cxj_stage_ssh，0600、Git 忽略；known_hosts 固定校验。通过 ssh -F server/.local/staging-access/ssh_config cxj-staging 访问；不关闭主机校验、不输出私钥，不给同事复制个人私钥。

2026-11-11 10:08 上海时间重新核验：云机两容器持续运行，MySQL healthy，readiness UP。旧本机18081隧道已停止，本轮恢复后实测：课程/套餐200 OK；匿名管理员和/me401；隐私协议503 AGREEMENTS_UNAVAILABLE。本机18080开发服务 readiness UP。本次是 SSH 隧道的云端 HTTP 核验，不是公开 HTTPS 或正式业务全流程验收。

本机18080=本地开发；本机18081=到云机18080的 SSH 隧道。隧道不是永续服务，每次查实际监听；仅无监听时恢复：
ssh -F server/.local/staging-access/ssh_config -o ExitOnForwardFailure=yes -fNT -L 127.0.0.1:18081:127.0.0.1:18080 cxj-staging
检查可用 curl --noproxy '*' http://127.0.0.1:18081/actuator/health/readiness，避免环境代理误导。新会话不要盲目重复启动。

云端备份 /var/backups/cxj/、核验报告 last-verified.json；本机异机副本 server/.local/backups/，均私有。scripts/mysql_backup.py 支持私有 --client-config 或 --docker-container；restore-drill 只允许新 cxj_restore_* 库，拒绝覆盖现有库。迁移前备份并校验，恢复核对后再决策。旧镜像只在验证兼容当前迁移/权限后才可回滚；不能直接把带阶段五引用的库交给阶段四旧代码写入，不能覆盖有新业务写入的库。

后续云配置/部署用 infra/compose.staging.yml 和 scripts/staging_release.sh，保留镜像/提交证据。域名就绪后实施 DNS/HTTPS/精准 ADMIN_ALLOWED_ORIGINS/MEDIA_PUBLIC_ORIGIN/微信合法域名/COS CORS，再按需安全配置相应环境的微信及云权限。公开环境禁止 local profile/固定验证码/测试协议；不能为“联调成功”放宽注册或媒体权限。

九、私有配置与外部资源

已有 .env.wechat.local 保存 AppID/AppSecret 和云标识；.env.cloud.local 保存 bucket/region/受限 CLOUD_SECRET_ID/CLOUD_SECRET_KEY/FFMPEG_PATH；原始 CSV 在 server/.local/cloud-bootstrap/private-credentials.csv。11 月 11 日核验均0600、本机本人持有、非符号链接、Git忽略且未跟踪；真实 project.config.json 也忽略且未跟踪，当前0644，不含后端云密钥。不要输出配置值，不写入日志/前端/Git。

scripts/local_backend.py 用 --wechat/--cloud 白名单加载，Spring Boot不自动读文件；拒绝权限不安全或非本人文件；自动测试禁止 --cloud。值可含 & 等字符，不 shell source/拼接执行。微信 AppID、腾讯云 APPID、账号ID各不相同。

已有上海私有单AZ SSE-COS AES256开发桶，cxj-media-dev子用户只限该桶的上传/读取/校验/删除/CORS等9项权限，无财务/服务器/全账号/建删桶/改公开权限；不能用COS密钥管理服务器。2026-11-10 HeadBucket200，GetBucketCORS为404 NoSuchCORSConfiguration；Web后台直传需实际源确定后精确配置并验证，禁止以全开放规则凑成功。真实STS/COS可用不代表短信、直播、转码或内容安全服务已开通。

微信开发者权限和 IDE 登录曾真实验证；认证、备案、类目、手机号、支付能力需逐项核实，不根据AppID存在推定。甲方没有已批准协议，docs/agreements只是草案；需要主体/客服/实际采集处理方式/版本/生效日期，不能当正式文本启用。

十、本机工具与验证命令

复用已有工具，不因默认PATH找不到而重复安装：
- JDK：/Users/liuxiaopang/Codex/小程序/.tools/jdk-21.0.13+9/Contents/Home
- Maven：/Users/liuxiaopang/Codex/小程序/.tools/apache-maven-3.9.16
- MySQL：/Users/liuxiaopang/Codex/小程序/.tools/mysql-8.4.11-macos15-arm64
- 数据目录：/Users/liuxiaopang/Codex/小程序/.tools/mysql-data；管理配置同级 mysql-admin.cnf
- 测试/开发配置：同级 mysql-test.env、backend-local.env；本机127.0.0.1:33306、socket /tmp/chengxinjian-mysql.sock；cxj_test可清理、cxj_dev保留。
- Node：/Users/liuxiaopang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node
- 便捷启动器：/Users/liuxiaopang/Codex/小程序/.tools/bin/
- COS/OpenAPI Python：/Users/liuxiaopang/Codex/小程序/.tools/cos-runtime/bin/python
- GitHub CLI：/Users/liuxiaopang/Codex/小程序/.tools/gh/gh，v2.103.3。

仓库根常用检查，按改动选择且不得并发争用cxj_test：
./scripts/backend.sh test
python3 scripts/local_backend.py test --database-env ../.tools/mysql-test.env
python3 -m unittest discover -s scripts -p test_local_backend.py
../.tools/cos-runtime/bin/python -m openapi_spec_validator openapi.json
../.tools/cos-runtime/bin/python scripts/check_cos_access.py

契约检查先把外层.tools/bin及上述Node目录加入本次PATH，再 npm run check。服务停了才执行：
SERVER_PORT=18080 python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat --cloud
等待readiness=UP；MySQL若停了恢复已有数据目录，绝不重新初始化。

真实云/课程探针仅限本机cxj_test、合成素材和限定对象清理，需独占18082及IDE9420；先阅读脚本并确认无其他测试使用同库。设置 WECHAT_MCP_PYTHON=/Users/liuxiaopang/.local/share/uv/tools/wechat-devtools-mcp/bin/python 和 WECHAT_MCP_WRAPPER=/Users/liuxiaopang/Codex/小程序/.tools/wechat_status.py 后执行：
../.tools/cos-runtime/bin/python scripts/smoke_media_cos.py --run-real-cloud-upload --course-probe
不能对开发/云端业务库运行清理型集成测试，也不把历史日志当新执行结果。

十一、微信开发工具技能

已安装 WaterTian/wechat-devtools-mcp 0.9.21，技能在工作区.agents/skills/wechat-devtools/SKILL.md，必须先读其references/tool_reference.md并重新发现工具。官方IDE /Applications/wechatwebdevtools.app，CLI其Contents/MacOS/cli；运行时和包装器见上一节。有效小程序工程是 tools/wechat-login-probe，不能指向Java server。

首次真实调用 wechat_ide(action='status')，核对CLI/Node/端口/项目/登录/版本/CDP。此前IDE登录、9420verified、compile/page_data和真实探针均通过，仅代表历史状态。无直接MCP工具时复用真实stdio：
/Users/liuxiaopang/.local/share/uv/tools/wechat-devtools-mcp/bin/python ../.tools/wechat_status.py wechat_ide '{"action":"status"}'
包装器将参数放入params，不编造返回、不搜官方token文件；官方MCP可用则按技能分流。

自动化前start并确认verified，代码变化compile→page_data，导航核对expected_path=pages/login/index；9420独占；失败按技能最多重试3次，不open/compile循环。旧call_method上下文错误可按技能用evaluate调真实页面方法并核对状态。探针只做测试协议/真实登录上传/合成课节授权下载事件，不取相册摄像头，不把code/token/上传票据放page data/storage/log。IDE关闭本地域名校验不能替代真机合法域名。普通微信客户端不是IDE。

十二、接下来直接实施的优先级

用户已认可优先安全管理员初始化和学习记录闭环。本次交接只整理现状，尚未开始这两项新开发，不把建议当已实现。

1. 快速核对Git、本机/云端服务、当前配置和协议缺口，先修实际问题，不重做已存在的会话/上传/课程/权益/课节播放基础。
2. 补非local安全管理员初始化、正式人机验证码适配/失败策略及必要权限管理，使隔离后台能够真实登录联调。先完成可独立写的实现与测试；供应商参数或服务开通缺失时具体列出，不用固定验证码或local种子绕过。生产管理员初始化应一次性受控、审计、避免密码进入命令历史/日志，具体按现有架构设计并验证。
3. 按既有DTO补学习历史列表、收藏和点赞，完善课程→权益→播放→事件→历史→恢复位置，继续验证并发/去重/跨会话/权限变化；正式前端由同事接入。不要另建一套学习进度表绕开现有LessonPlayback事实。
4. 补定时备份、保留/异机策略、失败任务可观测和告警，做恢复演练和明确回滚步骤。已有一次空库恢复证明不等于长期可恢复。
5. 域名/协议准备与开发并行。甲方现在需要准备归属其主体的域名及备案，确认草案主体/客服/实际隐私处理/版本；收到后你配置DNS/HTTPS/合法域名/精确CORS及环境凭据。给具体官方入口和操作，采购或云付费前核实当日状态/余额/费用并说明；不重复购买现有实例。
6. 和同事按0.3.0契约及实际60入口联调，不要求对方把全部224当现成。取得公网条件后及时做真实前端和微信真机、真实授权素材的大文件/长时/弱网/后台续播/撤权验收，不必等全部接口写完才联调。
7. 继续本期全部剩余业务：手机号/短信/密码/换绑/找回/注销，完整后台用户/RBAC，练习/白噪音/混音，上海自然日打卡，录制审核和奖励，课程/实物领奖及物流，会员订单/支付/退款/对账，直播预约互动禁言回放，公告推荐/搜索/统计/任务/监控，以及最终上线验收。

待确认可配置设计继续保留：月/季/年/终身四档一次购买、四种课程策略、录制先审核、课程和实物奖励。价格、套餐天数、学习完成口径、打卡门槛、奖励天数、录制形式/时长/隐私、运费、退款、注销保留和回放保留均非正式批准规则。手机号冲突不能覆盖旧账号/合并交易权益；支付方式、平台类目、审核/资质/费用接入时查最新官方资料，旧README建议不代替当时规则。

最终上线门槛包括真实完整业务流程、正式协议、管理员和权限安全、生产密钥/私网/HTTPS、真实支付回调和对账退款、性能容量、真实素材、监控告警、定期备份恢复/故障演练、正式前端及微信真机、平台审核和发布确认。当前尚未通过整体上线验收。每阶段更新接口清单、开发说明和证据；不要只留TODO、计划，不能把局部本地成功报告为功能已交付上线。
