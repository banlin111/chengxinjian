请接手「澄心涧」微信小程序 Java 后端，先做不依赖甲方实名资质的开发，目标是正式小程序在公网 HTTPS、正式前端和微信真机上跑通完整业务并满足上线验收。请在现有仓库继续编码、验证、记录和推送。只要仍有能独立推进的后端工作，就继续做。前端同事负责正式小程序和 Web 后台，你负责后端、独立探针、真实联调验证和给前端的文件/接口交接单。

一、先确认接手的是哪个版本

工作区 /Users/liuxiaopang/Codex/小程序，实际仓库为其下 chengxinjian。远端 https://github.com/banlin111/chengxinjian.git，后端分支 codex/backend-foundation，前端分支 codex/frontend。本交接整理于 2026-11-19 上海时间。整理前后端分支尖端 f6d330ea9a0c9d36756df69bf029cae741e905a6 是部署证据文档，实际后端实现及云端镜像对应 3b3554d097719d9feb403d34eacfba269bc5a289。前端当时为 223b2ea4d30d18a9a6ee2655c8b526bbb65fad84。本文也将作为后续文档提交保存。所有 SHA 都是定位历史证据的锚点。

在实际仓库执行 git status、git branch -vv、git log 和 git ls-remote --heads origin，检查工作区与仓库中的 AGENTS.md。有更新就先核对差异，保留并衔接后续修改。不 reset/clean、不强推、不切回交接 SHA、不擅自合入 main。只读核对前端新提交，必要时另用 worktree。

先读仓库 README.md（范围以仓库版与工作区《澄心涧.pdf》为准）、docs/backend-development.md 最新章节、docs/notices-integration-2026-11-19.md、docs/user-phone-integration-2026-11-16.md（读到最后的资源复核）、docs/course-player-integration-2026-11-16.md、docs/backend-stage6-2026-11-11.md 和 docs/backend-stage5-2026-11-10.md。docs/handoff-prompt-2026-11-11.md 与 docs/handoff-2026-11-11.md 保留工具路径、环境与安全操作细节，后续进展以新文档和实际代码为准。针对本批工作核对 contracts/models.ts、contracts/api.ts、openapi.json、server 实现/测试及 Flyway 迁移。不要把契约接口当作现成服务，不按接口数估算完成率。原型里的商城、分销、积分等不自动纳入范围。

二、当前可复用的代码及验证边界

1. Java 21、Spring Boot 3.5.16、Spring Security/JDBC、Maven Wrapper、Flyway、MySQL 8.4.11；H2 用于快速回归。0.3.0 契约 224 个方法/路径，其中 92 个已有后端代码入口。数据库 UTC，业务自然日 Asia/Shanghai；/api/v1、字符串 ID、整数分/CNY、时长秒、分页从 1 起默认 20 最大 100；创建用 UUID Idempotency-Key，更新沿用 version。改契约先改 contracts 源再生成，破坏性变更先协调。V1–V10 已在云端应用，以新迁移继续。
2. 账号有独立的普通用户与管理员会话、微信 code2Session、协议版本证据、管理员安全初始化、RBAC 和审计、普通用户目录/状态/角色、绑定/换绑和二次验证代码。opaque token 只存摘要、刷新双令牌轮换、权限和禁用实时检查。非 local 缺批准协议拒绝注册。手机号冲突须返回 409 PHONE_ALREADY_BOUND，保留双方 ID、原号、会话、订单引用和各自权益，绝不能覆盖或合并。自动用例验证了两人并发抢同号一成一败、人工会员及单课播放权限保持原状；真实微信开发工具验证过 wx.login 身份复核。真实短信送达、插件票据、微信 getPhoneNumber 取号及真实双账号换绑仍待联调。密码、找回、注销等账号范围仍有缺口。
3. 内容已有分类、讲师、课程、课节、编辑审核、私有 STS/COS 上传、持久化验证任务、经审核图片、试听裁切、会员套餐和按来源独立授予/撤销权益。播放仅覆盖 lesson；签发与续签检查当前内容和权益，短效单对象 URL 最长 120 秒，试听仅下发已裁切成品。撤权后既发 URL 在剩余期限内仍可能可用。已有事件去重、有效时长限制、恢复位置、学习历史和课程/课节收藏点赞。人工单课授权不证明支付成功。合成图、4 秒视频和 IDE 下载只覆盖开发链路；真实长课程、弱网、锁屏后台、来电中断和撤权后的真机行为仍待验收。
4. 首页聚合、后台公告管理、定时发布可见性、用户分版本已读、归档后首页/列表/详情同步隐藏已有代码。公告本批新增 8 个方法/路径和 V10 两张表。公告需同事接正式页面；公开查询和已读写入使用不同入口。前端旧版 H5 首页曾实际显示合成课程与审核封面，撤审后移除；正式微信包曾在 IDE 走隧道读取首页空态。课程列表→详情→课节→播放的接入单已转前端，单课 purchase 被标为 VIP 的展示问题须核对前端后来是否修复。公告、换绑、正式播放器等不能笼统报告“前后端已打通”。
5. 当前实现提交历史回归为 H2/原生 MySQL 各 126 项无跳过、Python 10 项、契约、OpenAPI、打包 JAR 的本地 HTTP 检查通过；CI 对应该实现提交 https://github.com/banlin111/chengxinjian/actions/runs/44442047102 。11 月 19 日另有隔离云机验收：V10 部署、公告/首页等公开响应、匿名 401、正式隐私协议缺失 503；迁移后备份恢复到新演练库，43 表校验及 10 份迁移一致；在无业务写入情况下实测旧镜像启动并切回新版。云端当时没有管理员、用户、公告或已读业务数据。上述是分环境的历史结果，新会话按本次改动复验并分别注明。
6. 本提示词整理时仅重新核对了 Git 本地/远端、云端容器、SSH 隧道 readiness 和交接文档。应用 cxj-api:3b3554d、MySQL healthy、隧道 readiness UP。整理本身未重跑业务测试、供应商联调或真机验收。

三、先推进的业务

建议第一批直接做“纯定时练习 → 有效时长 → 本人练习记录和按日统计”。这是开发顺序建议，先核对分支有无同事已写的实现。读 README §8.3 和 PracticeStart、PracticeEvent、Practice、PracticeStats、PracticeConfig 等 DTO。沿用契约 POST /practice/sessions、GET /practice/sessions/{id}、POST /practice/sessions/{id}/events、GET /me/practice/sessions 和 GET /me/practice/stats。明确本批实际支持的 kind，未接通的 guided/noise 不得返回伪造成功。优先实现持久状态及真实查询，统计字段按已存在的事实和配置计算，文档明确尚缺的数据来源。

从登录用户创建 timer 开始，处理 heartbeat、pause、resume、finish、abandon。验证本人权限、服务端有效时间、暂停与终态、请求重试、重复 eventId/sequence、网络补传、双设备重叠区间、跨上海午夜的按日分摊及跨会话读取。用可确定时间和原生 MySQL 并发验证“同一段时间只记一次”，用打包服务过实际 HTTP，再与已有正式前端提交比对并列出调用点。产品尚未批准学习完成比例、打卡阈值、奖励天数、录制形式和时长，保留可配置设计，普通计时或播放上报不得直接发奖。未配置正式规则时保持明确状态，勿将示例值当正式规则。

后续接白噪音/冥想的审核素材与授权播放、前台混音及预设、上海自然日打卡，再接录制审核、奖励发放和实物物流；搜索、运营推荐与后台配置也可独立推进。继续保留账号、订单支付退款对账、直播预约互动回放、统计监控等 README 剩余范围。第三方参数、正式素材或前端缺项只阻塞对应验收层级。若前端已有新代码，趁早做真实联调，不接管正式前端。

四、现有资源与操作边界

- 复用上层 .tools 中的 Temurin 21.0.13+9、Maven、Node、MySQL、cos-runtime、gh、微信 IDE 包装器。默认 PATH 缺工具时先查 11 月 11 日交接。MySQL 在 127.0.0.1:33306，停机时恢复原数据目录。cxj_dev 最近只读核实为 V9，启动新版前备份；自动测试用 cxj_test（V10，可清理）。严禁以开发库或云端业务库运行测试清库或探针。18080 是本机开发，18081 是 SSH 隧道，两者先查监听。
- 隔离服务器为已购上海腾讯云轻量 cxj-api-staging，到期 2027-02-10 17:17:53 上海时间。服务器部署已获授权。用 ssh -F server/.local/staging-access/ssh_config cxj-staging，保留主机校验。云机 mysql,prod，应用映射仅 127.0.0.1:18080，数据库无宿主端口，Redis 未接入业务。检查隧道用 curl --noproxy '*' http://127.0.0.1:18081/actuator/health/readiness。云端没有生产凭据或批准协议，公网域名尚缺。迁移前备份，恢复仅到全新演练库，使用现有 staging_release、staging_maintenance、verify_staging_restore、pull_staging_backups 和 mysql_backup 脚本，先读用法。回滚先检查新迁移/权限/现存业务数据兼容性。
- 微信/COS/手机号私有参数仅用 scripts/local_backend.py 白名单加载。不要读取到聊天、回显、提交或复制到前端。保留服务器私钥、备份和 root 私有环境文件的访问限制。用微信 IDE 时先读工作区 .agents/skills/wechat-devtools/SKILL.md 及 references/tool_reference.md，实际调用 status，必要时复用已有 stdio 包装器。真实云课程和手机号复核探针分别见上述联调文档，独占 cxj_test、各自端口及 IDE 9420，不与集成测试并发。
- 已有上海私有 COS 开发桶、受限存储身份、已验证的微信开发者权限、单独的短信/验证码后端开发子用户。短信已免费开通，11 月 16 日快照显示免费包 200 条剩余，到期 2027-02-16；小程序验证码免费体验记录为 11 月 16–23 日，使用前核实现状。短信实名资质、签名、正文模板缺项由我后续通知甲方配合。手机号后端用小程序验证码 DescribeCaptchaMiniResult，后台 Web 人机验证另行配置。不要重新下单，旧余额及配额不能当实时值。需付费先核实已有资源、缺口和确切金额并告知我。
- 公网联调仍需域名/备案、DNS/HTTPS、微信合法域名、精确 CORS、MEDIA_PUBLIC_ORIGIN 和相应环境凭据；正式注册需批准的协议；长课和弱网实测需授权素材；微信手机号/支付等需分别核实平台能力。缺项按需向我列平台入口、步骤、填写内容和交付结果。不要上传小程序、提审或正式发布。

五、每批交付方式

从仓库根使用 ./scripts/backend.sh test 和 python3 scripts/local_backend.py test --database-env ../.tools/mysql-test.env 分别跑 H2 与 MySQL。运维/配置验证用 python3 -m unittest discover -s scripts -p 'test_*.py'。契约验证使用已有 Node 和 .tools/bin 的 PATH 跑 npm run check，再用 ../.tools/cos-runtime/bin/python -m openapi_spec_validator openapi.json。结合改动做打包 HTTP、隔离部署、相应真实联调。涉及第三方时按实际账号、素材、日期和端点记证据。

更新实现清单、开发说明、前端文件/接口清单和证据路径。提交并正常推送到 codex/backend-foundation，核对远端 SHA 与对应 CI。每次报告分别写：①代码已写及范围，②自动测试，③真实第三方开发联调，④隔离云机与 SSH 隧道，⑤公网 HTTPS，⑥正式前端及微信真机（具体页面与机型），⑦上线验收。用“本次实测 / 历史证据 / 尚待验证”注明来源，单列失败与所缺条件。本地模拟、合成素材、小文件、IDE、云机小流量测试或接口数量都不能作为整个业务已实现、已验收或可正式上线的依据。


六、同日后续实施记录

上述 V10、分支 SHA 和优先任务为接手时基线。本日稍后已完成纯定时练习、服务端去重有效秒、本人记录、上海日统计及显式打卡第一批，实际代码与隔离部署为 5a4affb，云端 V12，本机 cxj_dev 仍为 V9。续接请先读 [练习与打卡交接](timer-practice-integration-2026-11-19.md) 的七层实测记录、前端文件和未完成项，再核对最新分支。正式页面及微信真机练习业务仍待接入验收。

同日第三批已增加经审核的冥想/白噪音目录、绑定播放与练习有效秒结算，实施提交 b6cafbb，隔离云库 V14，本机 cxj_dev 只读复核 V9。继续工作请先读[音频练习接口与七层证据](practice-audio-integration-2026-11-19.md)。微信 IDE 音频本轮来自回环适配器；当前仅允许使用已核实的免费额度，真实 COS 音频复验需先核对相应额度。

随后一批实现公共及个人混音配方、重登恢复和按配方快照启动练习。实施提交 f8170e9，隔离数据库 V15，本机 cxj_dev 仍为 V9。接续请先看[混音配方接口、前端接入与验证](mix-presets-integration-2026-11-19.md)。本次两个真实 IDE 播放器使用本机对象存储；当前 COS 仍只允许使用已核实的免费额度。
