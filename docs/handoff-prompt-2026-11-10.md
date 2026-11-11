# 新会话可直接使用的完整交接提示词

> 历史快照，停留在阶段四。继续开发请使用 [2026-11-11 完整提示词](handoff-prompt-2026-11-11.md)；阶段五已新增课程/课节、审核图片、来源权益、课节播放并部署隔离云机，仍有明确上线验收缺口。不要按本文旧状态重建或回退。

整理日期：2026-11-10（Asia/Shanghai）。下方正文可直接复制到同一本机工作区的新聊天。只记录非敏感状态，任何真实密钥、密码、云账号标识和服务器连接资料均不写入此文件。

---

请接手并继续开发「澄心涧」微信小程序的 Java 后端。先核对现有工程、Git 和运行环境，再直接实施下一阶段，不要只返回计划，不要从零搭建或把旧交接中的历史状态当成当前进度。

一、目标、分工与验收口径

- 我负责后端，同事负责微信小程序和 Web 管理后台正式前端。不要接管正式前端页面；允许维护已有独立接口联调探针。本期目标是完整功能、前后端真实联调、微信真机验收并可正式上线的小程序，APP 放在后续。
- 以工作区《澄心涧.pdf》和实际仓库 README 为需求依据。外层较旧 README 不得覆盖仓库版本；两份高保真原型仅供补充，不自动扩入商城、积分货币、优惠券、分销、佣金、秒杀、礼物/连麦、自动续费或离线下载等额外范围。
- 必须区分“代码已写”“自动测试通过”“真实第三方开发联调通过”“真实公网环境通过”“正式前端及真机通过”“上线验收通过”。只能报告实际达到的层级。不能因为合成小文件或本地接口测试通过，就宣称整个账号、媒资、课程或支付功能已完成、可上线。
- 资源暂缺时继续独立可推进的开发，不能降标准、删除功能或用 Mock 成功替代真实验收。需要我配合时给具体操作；给甲方的要求只列其当前必须办理的事项，写清平台入口、按钮、填写内容与交付结果，方便微信转发。开发方安装、代码、配置由你处理。

二、必须使用的工作区与 Git

工作区：/Users/liuxiaopang/Codex/小程序
实际仓库：/Users/liuxiaopang/Codex/小程序/chengxinjian
远端：https://github.com/banlin111/chengxinjian.git
协作分支：codex/backend-foundation

后端实现已提交并推送，代码基线为 29c057d；交接文档随后另行提交，最新 HEAD 以实际 Git 为准。0120d62 只是旧的文档/契约基线，“后端主要为未提交文件”的描述已过时。新会话仍需执行 git status、git branch -vv、git log、git ls-remote 核对最新状态；不要回退到上述历史 SHA。保留后续新修改，不要 reset/clean、覆盖同事代码或重新克隆空工程。不要擅自强推、合入 main 或发布小程序。

仓库是公开的。真实配置、凭据 CSV、数据库、工具运行时、日志、截图、真实 project.config.json 不在 Git。GitHub 本机凭据使用 macOS 钥匙串；不要读取后回显，也不要把凭据嵌入 remote URL。该机 Git 使用本机代理和 HTTP/1.1，设置在本地 .git/config，不是需要给同事复制的项目配置。先检查现有授权，不索取聊天中的密码或令牌。

Git 已通过本地凭据助手验证可推送；gh auth status 不一定显示登录，因为没有给 gh 配置文件落盘令牌。不要仅凭 gh 未登录就重复申请授权；必要时只在获准用途的子进程环境临时使用已有凭据，不输出、不写源码。

三、优先阅读

1. 仓库 README.md、docs/backend-development.md。
2. docs/backend-stage4-2026-11-10.md、docs/media-upload-contract-proposal.md。
3. contracts/models.ts、contracts/api.ts、openapi.json，以及 server/ 实现、配置、四份迁移与测试。
4. docs/cloud-setup-2026-11-10.md、docs/agreements/ 下草案及 review-notes.md。
5. tools/wechat-login-probe/README.md。
6. /Users/liuxiaopang/Codex/小程序/澄心涧.pdf。
7. /Users/liuxiaopang/Codex/小程序/.agents/skills/wechat-devtools/SKILL.md 及 references/tool_reference.md。

检查有无新 AGENTS.md。handoff-2026-11-09.md、stage2/stage3 及云资源申请说明保留了历史信息；当前范围以本提示词、stage4 和实际代码为准。

四、现有技术与接口契约

- Java 21，Temurin 21.0.13+9；Spring Boot 3.5.16；Spring Security、Bean Validation、Spring JDBC；Maven Wrapper 3.9.16；Flyway 锁定 11.20.3。
- 模块化单体；正式数据库目标 MySQL 8.4，本机原生 8.4.11；H2 为快速开发/测试。Redis 仅有开发配置，尚未接入业务缓存；只有媒资持久化处理任务已落地，不能宣称通用 outbox、全部后台任务和生产运维都已完成。
- 契约版本 0.2.0，共 220 个 HTTP 方法/路径组合、159 个 schema，目前只有 29 个业务接口已有后端实现；其余 191 个不能视作已开发或测试。
- /api/v1；ID 字符串；金额整数分/CNY；时长秒；数据库 UTC，业务自然日 Asia/Shanghai；分页从 1 开始，默认 20、最大 100。
- 成功包为 code/message/data/requestId/serverTime；错误用真实 HTTP 状态和业务码。拒绝未知 JSON 字段、重复键、尾随 JSON、歧义类型转换。创建业务使用 UUID Idempotency-Key；更新使用 version，历史订单、权益、内容引用保留。
- 修改契约先改 contracts 源，再 npm run check，不只改生成文件。破坏性变更需两位开发者共同确认。formData 改字符串字典已由用户明确确认与前端达成一致，不能恢复旧五字段。
- 生成器已修复交叉类型漏字段、映射字典被错误关闭、泛型组件名不符合 OpenAPI 规范和只读预览被误要求幂等头的问题。保留修复；770 个对象结构的编译器核对不等于 220 个接口已实现。

五、已有后端接口（以下均加 /api/v1）

管理员与分类 10 个：
POST /admin/auth/login
POST /admin/auth/refresh
POST /admin/auth/logout
GET /admin/me
GET /categories
GET /admin/content/categories
POST /admin/content/categories
GET /admin/content/categories/{id}
PUT /admin/content/categories/{id}
DELETE /admin/content/categories/{id}

微信用户、协议与设置 7 个：
GET /agreements/{type}
POST /auth/wechat/login
POST /auth/refresh
POST /auth/logout
GET /me
GET /me/settings
PUT /me/settings

讲师与关注 8 个：
GET /teachers
GET /teachers/{id}
PUT /me/follows/{teacherId}
GET /admin/content/teachers
POST /admin/content/teachers
GET /admin/content/teachers/{id}
PUT /admin/content/teachers/{id}
DELETE /admin/content/teachers/{id}

媒资 4 个：
POST /uploads
POST /uploads/{id}/complete
GET /assets/{id}
POST /admin/assets/{id}/preview

另有 /actuator/health、/actuator/health/readiness、/actuator/health/liveness。必须等 readiness=UP 后再执行真实业务请求。

六、已有行为及不能误读的边界

认证：BCrypt，安全随机 opaque access/refresh token，数据库只保存 SHA-256 摘要；默认 2 小时/30 天，刷新同时轮换，旧值失效，退出撤销目标会话，账号禁用和权限变化在后续请求实时生效。不是 JWT，不因模板中的 TOKEN_SIGNING_KEY 要求甲方提供 JWT 签名密钥。

用户与管理员使用独立账号和会话存储、独立 audience；V2 已新增 app_user、wechat_identity、user_auth_session、协议版本/同意、设置、code 消费等结构。不要再次把用户塞入 admin_account。BearerTokenFilter 仅对明确的公共媒资路由兼容两类身份，其余身份隔离保留；过期 accessToken 不应阻断合法 refresh。

Settings.version 为兼容旧客户端而可省略，响应总带 version；传入时检查乐观锁，省略时事务串行、最后写入生效，不能宣称省略也能检测陈旧版本。提醒偏好目前只保存设置，不代表通知任务已发送。不要未经共同确认改成不兼容的必填字段。

微信：真实 HTTPS code2Session，AppID+OpenID 唯一，业务主键为 UUID，UnionID 可缺；处理超时、无效/已用 code、供应商错误和并发，事务写入用户、同意记录和会话，不保存/下发 session_key 或 AppSecret。注册需要当前协议同意证据；本机测试协议仅 local，非 local 排除 test_only，未配置正式协议则拒绝正式注册。

分类：权限、筛选、搜索、分页、发布/归档、乐观锁、幂等快照和审计；全局分类禁止 own_content 管理。课程尚无实现，建课程后还需补分类改类型、归档和课程引用的一致性。

讲师：唯一关联已有用户、发布/归档、关注、动态讲师角色，own_content 不能越权、创建全局资源或重绑他人。完整后台账号/角色/权限管理尚未做，不能把 content:read/write 和基础角色关联称为完整 RBAC 交付。

媒资：真实 COS/STS 直传，临时权限只允许一个隔离对象的 PostObject，签名绑定路径、类型、精确字节数、私有 ACL、加密和过期时间。客户端获得临时 ID/token/表单签名，不获得任何 SecretKey 或永久访问 ID。前端把 formData 字符串字典原样提交，file 放最后。

complete 只事务确认并投递校验任务，不把客户端上传报告当成功。Worker 有界下载，核对真实长度/MIME/SHA-256，图片完整解码，音视频核对容器、流、完整解码及秒数，再把已校验字节写入客户端不能覆盖的 validated 对象并置 ready。公开地址仍为 null；后台仅给合法范围的管理员 120 秒预览，普通用户文件保持隔离，后台不能借内容权限读取用户私有录制。

任务有数据库租约、重试、过期恢复、隔离对象延后清理及历史保留；不是完整任务平台。供应商故障最多自动重试 5 次，耗尽后 job=failed、资产仍 processing；服务恢复后可用新的幂等键再次 complete，已测试。必须补后续可观测和管理能力，不能把 processing 当完成。

技术默认上限：图片 10 MiB/16MP；媒体 512 MiB/4 小时；解码 120 秒。媒体上限可配置，这些不是甲方批准的录制奖励规则。FFmpeg 禁止网络协议、限制资源且不继承云/微信/数据库密钥环境。Java COS 5.6.283、STS SDK 3.2.6、WebP 解码 3.15.3；本机 FFmpeg 由已有 imageio-ffmpeg 0.6.0 提供。

特别注意：技术 ready 不等于内容审核通过、已转码、允许公开或有购买权益。教师头像引用当前要求可用 public_url，而真实上传资产仍私有/null，故“真实上传头像”尚未贯通正式讲师头像展示；应继续完成安全审核/图片投放，不要伪造公开 URL。后台预览不是付费用户播放鉴权。

七、迁移、测试证据与验收层级

- V1 管理员/分类基础，V2 微信用户/协议，V3 讲师/关注/媒资元数据，V4 上传预留/处理任务。已在本机 MySQL 应用；后续加 V5 等，不修改已应用迁移的内容。
- 2026-11-10 最近验证：H2 和真实 MySQL 8.4.11 各 83 项通过，无跳过；配置加载器 5 项通过；npm run check 通过；OpenAPI 3.0 规范校验通过。
- 打包 Java + 真实 MySQL 隔离库 + 正式 COS：60 个检查通过，使用合成 PNG/JPEG/WebP、WAV、MP4 小文件。覆盖路径/大小/公开 ACL 篡改拒绝、匿名拒绝、SHA/类型/解码失败、隔离覆盖不改变正式文件、幂等与清理。测试对象已删除并 HEAD 核对 404。
- 微信开发工具：真实 wx.login → 后端用户会话 → wx.uploadFile → COS → complete → ready，未使用 Mock；合成本地文件、云对象及会话已清理，临时 18083 服务停止，探针恢复 18080、未登录。
- 以上只是已列明的自动测试、真实供应商及开发工具联调：未完成正式前端整合、微信真机、公网合法域名、真实课程大文件/长时播放、支付、性能容量、故障恢复或整体上线验收。不能推广为模块全部可上线。
- 报告在 server/target/stage4-h2.log、stage4-mysql.log、stage4-contract.log、stage4-openapi-validation.log、media-cos-smoke/result.json、stage4-wechat-*.json；均忽略 Git。新会话读取历史报告不等于当天重新执行；明确写时间、环境和覆盖范围。GitHub Actions 实际状态需读取远端，不能拿本地报告代替。
- 代码提交 29c057d 的 GitHub Actions 已实际执行成功：contracts/backend 两个 job，包括契约生成、配置加载器、Ubuntu 的 H2/MySQL 集成测试和打包 HTTP 检查。运行 https://github.com/banlin111/chengxinjian/actions/runs/42770929765 。CI 不持有真实微信/COS 密钥，不替代真实供应商或真机验收；MySQL 服务容器通过也不等于应用 Dockerfile/compose 已验证。

八、资源与安全配置

已拿到并在本机登记的微信 AppID/AppSecret、腾讯云标识：仓库根 .env.wechat.local，0600、忽略且未跟踪。微信开发者权限已添加并验证过 IDE 登录；认证、备案、手机号、支付等具体能力仍需逐项核实。

已创建上海 ap-shanghai 私有、单 AZ、SSE-COS AES256 开发桶及 cxj-media-dev 编程子用户；仅该桶读写/校验/删除和 CORS 配置等 9 项 COS 权限。此子用户没有财务、服务器、全账号管理、创建/删除桶或修改公开权限；不能用 COS 密钥管理服务器。真实 STS/COS 已联调，不代表短信、直播、审核和转码服务已开通。

云配置在仓库根 .env.cloud.local，0600、忽略且未跟踪，含 bucket/region、受限 CLOUD_SECRET_ID/CLOUD_SECRET_KEY、FFMPEG_PATH。原始凭据 CSV 在 server/.local/cloud-bootstrap/private-credentials.csv，同样私有且忽略。不要回显、复制到前端、写入日志、公开文档或 Git。微信 AppID、云 APPID、云账号 ID 不混用。

这些文件不由 Spring Boot 隐式读取：scripts/local_backend.py 以 --wechat/--cloud 显式白名单加载，拒绝符号链接、非本人或不安全权限文件；自动测试拒绝 --cloud。环境值含 & 等符号，不要直接 shell source 或拼接执行。不要向我重索已有密钥。

甲方目前没有已批准的用户协议/隐私政策；此前“已有全文”已被后续澄清覆盖。开发方草案在 docs/agreements，待确认主体资料、客服、实际处理方式、版本和生效日期，不是批准文本，生产不能启用本地测试协议。

九、服务器与正式部署的最新指令

我最新反馈：服务器已就绪，甲方腾讯云账号已经在浏览器登录，可以按项目需要操作。不要再沿用旧文档“甲方没有服务器”的结论；但不能把登录控制台等同实例、SSH、HTTPS或应用已部署，需先只读核对现有服务器类型、地域、系统、实例状态、访问方式及项目归属。

本地模块测试通过后，应继续公网环境、正式前端、真实数据与微信真机的验收。真正需要服务器部署及云端付费服务时，先告诉我此阶段要部署什么、缺哪项资源、现有余额/实例状态及具体费用，我负责通知甲方充值。不要擅自充值或支付，不反复要求甲方购买已有资源。完成可独立推进的部署脚本、镜像、迁移、备份和回滚准备，再提出具体需求。

生产仍需补安全管理员初始化、正式人机验证码、批准协议、域名/备案/HTTPS/微信合法域名、精确 CORS、数据库与私网配置、监控告警、备份及恢复演练。Dockerfile/compose 已有，Dockerfile 补 FFmpeg；尚未在本机 Docker 实跑，不能当部署验收完成。当前授权不等于可以自动上传小程序代码、提审或正式发布。

需要跨电脑、正式前端、真机或回调联调时，应及时推进隔离的公网测试环境，不必等 220 个接口全部写完。公开环境禁止使用 local profile、固定开发验证码或本地管理员初始化来绕过生产缺口；正式协议未批准也不能伪装已批准放行注册。具体阻塞项与独立开发并行推进。

十、本机工具与复查命令

已有工具在仓库外，优先复用，不重复安装：
JDK：/Users/liuxiaopang/Codex/小程序/.tools/jdk-21.0.13+9/Contents/Home
Maven：/Users/liuxiaopang/Codex/小程序/.tools/apache-maven-3.9.16
MySQL：/Users/liuxiaopang/Codex/小程序/.tools/mysql-8.4.11-macos15-arm64
数据目录：/Users/liuxiaopang/Codex/小程序/.tools/mysql-data
管理配置：/Users/liuxiaopang/Codex/小程序/.tools/mysql-admin.cnf
测试环境：/Users/liuxiaopang/Codex/小程序/.tools/mysql-test.env
开发环境：/Users/liuxiaopang/Codex/小程序/.tools/backend-local.env
Node：/Users/liuxiaopang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node
便捷启动器：/Users/liuxiaopang/Codex/小程序/.tools/bin/
COS/验证辅助 Python：/Users/liuxiaopang/Codex/小程序/.tools/cos-runtime/bin/python
GitHub CLI：/Users/liuxiaopang/Codex/小程序/.tools/gh/gh（官方 v2.103.3，已核对发布校验和）

最近本机 MySQL 为 127.0.0.1:33306，socket /tmp/chengxinjian-mysql.sock，cxj_dev 为开发库、cxj_test 为可清理测试库；后端 127.0.0.1:18080，readiness 已核对 UP。新会话先查实际监听，不重复拉服务；若停了恢复已有实例，绝不重新初始化 mysql-data。默认 PATH 可能没有 mvn/node/npm/docker，不因此重复装整套环境。

在实际仓库根执行：
./scripts/backend.sh test
python3 scripts/local_backend.py test --database-env ../.tools/mysql-test.env
python3 -m unittest discover -s scripts -p test_local_backend.py
../.tools/cos-runtime/bin/python -m openapi_spec_validator openapi.json
../.tools/cos-runtime/bin/python scripts/check_cos_access.py

契约检查先将外层 .tools/bin 与上述 Node 所在目录加入本次 PATH，再 npm run check。需要真实云上传复查时执行 ../.tools/cos-runtime/bin/python scripts/smoke_media_cos.py --run-real-cloud-upload，仅接受本机 cxj_test、合成文件、限定对象清理；不要与数据库集成测试同时运行。

服务未运行时：
SERVER_PORT=18080 python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat --cloud
等 /actuator/health/readiness=UP。不要把开发库、生产库当可清理测试库，也不把历史日志当本轮重新验证。

十一、微信开发工具技能：已安装且曾真实使用，不要重新判断为“只有文件未连通”

WaterTian/wechat-devtools-mcp skill 0.9.21 在：
/Users/liuxiaopang/Codex/小程序/.agents/skills/wechat-devtools/SKILL.md

已安装官方 IDE：/Applications/wechatwebdevtools.app
CLI：/Applications/wechatwebdevtools.app/Contents/MacOS/cli
MCP 包运行时：/Users/liuxiaopang/.local/share/uv/tools/wechat-devtools-mcp/bin/python
已有调用包装：/Users/liuxiaopang/Codex/小程序/.tools/wechat_status.py
有效小程序工程：仓库 tools/wechat-login-probe（含被忽略的真实 project.config.json）。不能指向 Java server/。

新会话先读技能并重新发现已暴露工具；首先真实调用 wechat_ide(action='status')，核对 CLI、Node、服务端口、工程、登录、版本和 CDP。此前验证：服务端口已由用户开启、IDE 已登录、9420 verified、compile/page_data 成功；这是历史状态，不代替新会话检查。

若当前仍无直接 MCP 工具，可复用真正的 stdio MCP 包装：
/Users/liuxiaopang/.local/share/uv/tools/wechat-devtools-mcp/bin/python ../.tools/wechat_status.py wechat_ide '{"action":"status"}'
包装会把参数放入 params 对象；不要编造 MCP 返回，也不要去搜官方 MCP token 文件。官方 MCP available 且已接入时按技能分流，不重复操作。

自动化前 start 并确认 verified；改代码后 compile → page_data，导航后核对 expected_path=pages/login/index；9420 独占。不要未验证就 open/compile 循环，失败按技能排查，最多重试 3 次。曾有 call_method 的旧对象上下文错误，可用技能允许的 evaluate 调真实页面方法后核对 page_data。普通微信客户端不是 IDE。

探针只做本地测试协议、真实登录/会话及合成图片上传，不取相册/摄像头，不把 code/token/上传票据放进 page data、storage 或日志，不是正式前端。开发工具本地合法域名校验关闭，绝不能作为真机/正式合法域名验收结论。Mock 仅用于异常测试，不能替代真实登录/支付验收。

十二、下一阶段直接推进

1. 快速核对 Git、当前接口/迁移、服务与真实资源状态，确认私有配置仍安全；先修本轮发现的实际问题，不重做已完成的微信/会话/上传基础。
2. 按既有 DTO/OpenAPI 建课程、课节与媒资引用的一致性、讲师数据范围、上下架和历史引用保留。加新迁移，不改 V1-V4。并行补媒体审核/安全图片投放和可用的处理失败管理，使真实头像/封面等引用可完成业务闭环。
3. 继续会员/单课来源权益模型、免费/试听/会员/购买校验及用户播放授权。列表、课节、上传与后台预览不能绕过购买检查；不给未授权用户完整私有文件或仅前端计时的“试听”。随后学习进度、事件去重和恢复播放。
4. 每个纵向功能都做必要自动测试、真实 MySQL、真实供应商、正式前端联调与真机/公网验收；缺外部条件时明确阻塞的验收层级，并继续可做的实现。需要实际部署或充值时按上面的最新服务器指令通知我。

仍在本期范围：手机号/短信/密码/换绑/找回/注销、完整后台用户与 RBAC、练习/白噪音/混音、上海自然日打卡、录制审核和奖励、课程/实物领奖和物流、会员订单/支付/退款/对账、直播预约互动禁言回放、公告推荐、统计、任务、监控、备份恢复及上线验收。

沿用待确认的可配置设计：月/季/年/终身四档一次购买；课程 free/member/purchase/member_or_purchase；录制先审核；课程和实物两类奖励。价格、套餐时长、打卡门槛、奖励天数、录制形式/时长/隐私、运费、退款、注销保留和回放保留都不是正式批准规则。手机号冲突不能覆盖旧账号或随意合并交易权益；支付、平台类目、审核、资质与费用到接入时查最新官方资料。

现在开始核对并实际继续开发。每阶段更新实现清单、开发说明和验收证据，清楚报告实际达到的环境、范围、失败及仍缺条件；不要只留下 TODO、计划，或把局部本地成功当功能已上线。
