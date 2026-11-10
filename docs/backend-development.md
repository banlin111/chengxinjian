# 后端开发与交接

需求依据为根目录 README 与工作区《澄心涧.pdf》。前端工程由另一位同事负责，后端继续沿用 `/api/v1` 契约；新增兼容字段先改 contracts 源文件，再生成 OpenAPI 与 README。

2026-11-09 已整理供新聊天接续开发的完整交接提示词，见 [阶段交接](handoff-2026-11-09.md)。该快照区分历史验证与当前运行状态，并记录微信开发工具技能的实际可用性；不包含密钥内容。

## 本次落地范围

Java 21 / Spring Boot 3.5.16，模块化单体，Maven Wrapper 3.9.16。JDBC + Flyway 11.20.3 管理数据库；MySQL 8.4 是部署目标。H2 仅供本地快速开发和自动测试，同一份迁移还必须在真实 MySQL 通过集成测试。

已实现 29 个业务接口，另外提供 `/actuator/health` 及不暴露详情的 readiness/liveness 检查。服务接流量前需等待 `/actuator/health/readiness` 返回 UP，不能仅凭 HTTP 端口已打开或总体 health 就判定初始化已完成：

| 方法 | 路径（均加 `/api/v1`） | 实现 |
| --- | --- | --- |
| POST | `/uploads` | 限定私有上传票据、并发唯一预留及幂等快照 |
| POST | `/uploads/{id}/complete` | 核对摘要、原子写入校验任务和审计 |
| GET | `/assets/{id}` | 归属及数据范围内状态，隐藏内部存储信息 |
| POST | `/admin/assets/{id}/preview` | ready 后台资产的 120 秒私有签名地址 |
| GET | `/teachers` | 已发布讲师分页、搜索及当前用户关注状态 |
| GET | `/teachers/{id}` | 已发布讲师详情，隐藏账号和资产内部 ID |
| PUT | `/me/follows/{teacherId}` | 本人关注/取消关注，重复写幂等 |
| GET | `/admin/content/teachers` | 按权限和 own_content 范围查询 |
| POST | `/admin/content/teachers` | 创建、唯一用户关联、幂等快照和审计 |
| GET | `/admin/content/teachers/{id}` | 范围内详情 |
| PUT | `/admin/content/teachers/{id}` | version 乐观锁、范围和引用校验 |
| DELETE | `/admin/content/teachers/{id}` | 保留历史的归档及审计 |
| GET | `/agreements/{type}` | 当前及历史已生效协议；测试协议只在 local 可读 |
| POST | `/auth/wechat/login` | 真实 code2Session 适配，用户与微信身份、协议同意、独立用户会话 |
| POST | `/auth/refresh` | 用户会话双令牌轮换；旧令牌及跨 audience 凭据拒绝 |
| POST | `/auth/logout` | 撤销本人目标会话，其他设备继续有效 |
| GET | `/me` | 当前用户、主题、账号状态；尚无手机号/付费权益时如实返回空状态 |
| GET | `/me/settings` | 读取本人偏好及 version |
| PUT | `/me/settings` | 偏好校验、事务写入；传 version 检测冲突 |
| POST | `/admin/auth/login` | 密码校验、验证码接入边界、登录限流、会话创建 |
| POST | `/admin/auth/refresh` | 刷新时同时轮换 access/refresh，旧令牌失效 |
| POST | `/admin/auth/logout` | 持有有效管理员令牌，撤销对应刷新会话 |
| GET | `/admin/me` | 当前管理员及数据库中实时读取的角色、权限 |
| GET | `/categories` | 匿名可读，仅返回指定类型下已发布的分类 |
| GET | `/admin/content/categories` | 权限校验、分页、名称搜索、状态筛选 |
| POST | `/admin/content/categories` | 数据持久化、幂等响应快照、审计 |
| GET | `/admin/content/categories/{id}` | 分类详情 |
| PUT | `/admin/content/categories/{id}` | 全量替换业务字段、乐观锁、审计 |
| DELETE | `/admin/content/categories/{id}` | 归档、原因、版本检查、重复归档不重复写审计 |

分类接口需 `content:read` / `content:write`。分类是全局资源，`own_content` 账号不能访问全局分类管理接口；可通过匿名目录读取已发布分类。等课程模块建立后，分类类型变更和归档需增加引用检查；当前迁移还没有课程表，不假装已经完成相关校验。

共用的 AdminContentQuery 中 `categoryId/courseId/teacherId` 不适用于分类列表，传入时返回 400；已实现 `q/status/page/pageSize`。

其余 191 个契约接口尚待实现；OpenAPI 是全量目标，不代表当前实现清单。没有甲方账号时，不伪造微信登录成功、支付到账、短信发送或直播推流成功。

## 本机启动

常规环境安装 Java 21 后，仓库无需全局 Maven：

```sh
cd server
./mvnw -B -ntp verify
```

本次开发机已将校验过的 JDK/Maven 下载到仓库外的工作区 `.tools/`，可在仓库根目录运行 `./scripts/backend.sh test`；脚本会寻找该本地 JDK，不在仓库中写死个人绝对路径，也不会下载或提交开发工具。

本地 H2 启动：

```sh
# 在当前终端设置你自己生成的值，不要把真实密码提交到 Git。
export SPRING_PROFILES_ACTIVE=local
export LOCAL_ADMIN_USERNAME=admin
export LOCAL_ADMIN_PASSWORD='replace-with-your-local-password'
export LOCAL_CAPTCHA_TICKET='replace-with-your-local-random-ticket'
./scripts/backend.sh run
```

首次启动创建本地管理员；后续启动不会重置已有密码。密码 12~64 字符且 UTF-8 不超过 72 字节，验证码票据至少 16 字符。local 配置只监听 `127.0.0.1:8080`，H2 数据保存于 `server/.local/`。只有测试程序使用内存数据库。

登录请求体：`{ "username": "admin", "password": "你设置的本地密码", "captchaTicket": "你设置的本地票据" }`。使用返回的 accessToken 请求管理接口；不要将管理员令牌放进用户端请求。分类创建需提供 UUID 格式 `Idempotency-Key`，重复请求复用原值。

本地票据只是独立的开发环境接入方式，尚未实现供应商的人机验证码校验。非 local 环境登录会返回 503 `PROVIDER_UNAVAILABLE`，不会绕过校验；本次管理员登录不能宣称已完成生产验收。

## 真实 MySQL 与协作环境

已有 Docker 的开发者可以从仓库根目录准备 `.env` 并运行：

```sh
docker compose --env-file .env -f infra/compose.yml up -d
```

`.env` 需自行设置独立的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`。该命令创建持久化 MySQL/Redis，仅将端口绑定到本机。当前账号、分类和登录限流以 MySQL 为事实来源；Redis 服务已列入开发环境，尚未接入业务缓存。

实际连接应用时将配置导出为环境变量（Spring Boot 不会自动加载 `.env`）：

```sh
export SPRING_PROFILES_ACTIVE=local,mysql
export DB_URL='jdbc:mysql://127.0.0.1:3306/chengxinjian?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export DB_USERNAME=chengxinjian
# DB_PASSWORD、LOCAL_ADMIN_PASSWORD、LOCAL_CAPTCHA_TICKET 也需设置。
./scripts/backend.sh run
```

MySQL 集成测试使用独立、可清空的测试数据库，**禁止指向生产数据库**：

```sh
export TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/cxj_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export TEST_DB_USERNAME=cxj_test
export TEST_DB_PASSWORD='your-test-database-password'
export TEST_DB_DRIVER=com.mysql.cj.jdbc.Driver
./scripts/backend.sh test
```

测试包含授权拒绝、角色/数据范围、封禁即时生效、令牌过期/轮换/撤销、登录限流、分页、发布/归档、未知字段和歧义 JSON 拒绝、幂等与并发重试、乐观锁、响应字段契约检查，以及认证数据库故障时的 503 响应。GitHub Actions 已编写 H2/MySQL 双数据库验证，但只有推送后才会实际运行。

构建完成后，在仓库根目录运行 `python3 scripts/smoke_backend.py` 可验证打包后的服务。脚本要求设置 `JAVA_HOME`、`DB_URL/DB_USERNAME/DB_PASSWORD` 和本地管理员密码/验证码票据；使用独立开发数据库，临时在 `127.0.0.1:18080` 启动真实 HTTP 服务，验证登录、创建、重复提交、发布、公开目录、归档与退出，最后停止服务。脚本会创建并归档测试分类，不用于生产环境；日志保存在忽略提交的 `server/target/http-smoke.log`。

## 继续开发顺序

1. 本轮已实现微信身份、用户会话及协议记录；完成真实微信联调，随后补齐手机号绑定、冲突、短信与账号安全。
2. 讲师、媒资、课程/课节、权益校验和播放授权，建立内容学习闭环。
3. 练习计时、打卡、录制审核和奖励履约。
4. 会员订单、虚拟支付、退款、来源权益账本及对账。
5. 直播回放、后台角色管理、审核、统计、任务和发布运维。

上述都在本期目标内，分批实施不删除功能。上线仍需真实第三方联调、完整角色与数据权限、生产初始化管理员方案、HTTPS/域名、监控告警、备份恢复和微信真机验收；Dockerfile/compose 是当前构建与开发环境基础，不代表已经部署上线。

## 当前需要甲方配合

2026-11-10 当前状态：小程序开发者权限已添加，真实开发工具登录与上传已验证；腾讯云私有开发桶及受限子用户已创建并完成真实上传。暂无服务器和域名。小程序认证、备案、支付、短信、内容审核和直播等能力仍需按实际接入分批核实，不因云账号或 AppID 存在就推定全部可用。

项目标识保存在根目录 `.env.wechat.local`，文件权限为 0600 且被 Git 忽略，不写入公开仓库。`WX_APP_ID` 是微信小程序身份，`TENCENT_CLOUD_APP_ID` 是腾讯云账户下的云 APPID，`TENCENT_CLOUD_UIN` 是腾讯云账号 ID，三者不可混用。该文件不会被 Spring Boot 隐式加载。显式使用 `python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat` 安全加载；仅向进程传入 WX_APP_ID/WX_APP_SECRET，其他云标识不混用。脚本拒绝符号链接、非本人文件及组/其他用户可读的文件，不打印配置值。

小程序 AppSecret 已保存至上述本地文件的 `WX_APP_SECRET`，只记录交接状态，不记录密钥内容；已检查文件权限和 Git 忽略状态，且已通过真实 wx.login/code2Session 联调验证。测试协议仍只用于 local，正式文本须甲方确认；开发工具成功不能替代真机与正式公网验收。

腾讯云使用甲方账号下的项目子账号或协作者身份，具体权限按接入的资源列明，不索要主账号密码或主账号 API 密钥。服务器、域名和存储资源的采购配置另列，不由云账号 ID 推定已具备；生产规格在有使用量预估后确定。

账号办理期间继续完成数据库、正式业务接口、权限和状态流转；外部服务适配及失败处理按正式要求实现，在取得真实配置后完成供应商联调验收。

会员价格、打卡阈值等沿用 README 的“待确认且可配置”处理，不将原型数字写成正式规则。测试数据只用于隔离测试库。

## 2026-11-09：微信用户阶段

新增 `V2__wechat_users_and_agreements.sql`，不修改 V1。普通用户、微信身份、用户会话、协议版本、协议同意、设置及一次性 code 摘要与管理员存储独立。AppID+OpenID 使用二进制唯一键区分大小写，业务主键为 UUID；UnionID 可缺失，不据其自动合并账号。用户及管理员 access/refresh 不能串用；登录与刷新入口独立检查凭据，不被旧 accessToken 阻断。封禁及 deletion_pending 在访问/刷新/登录中实时拒绝。

真实微信 HTTP 适配固定 HTTPS 官方地址，连接超时 3 秒、请求超时 5 秒，禁止自动重定向，不自动重试一次性 code。40029/40163 返回 400 WX_CODE_INVALID；40226 返回 403 WX_LOGIN_BLOCKED；供应商其他故障或缺配置返回统一 503。不回传供应商原始报文，不保留 session_key。用户令牌仍为 opaque token，数据库只保存 SHA-256 摘要。

外部请求不占用数据库事务。注册写入使用 READ COMMITTED + 微信身份行锁及唯一约束，避免 MySQL REPEATABLE READ 的旧快照遗漏并发已提交同意记录。创建用户、默认设置、协议证据、code 消费、会话和审计在同一事务提交，失败整体回滚。已在真实 MySQL 发现并修复该并发问题。另以四路并发老账号登录复现锁升级死锁，身份与限流记录改为无修改 upsert 直接取得排他锁；该场景纳入回归测试。

新用户必须提交当前隐私/用户协议两个版本；已同意当前版本的老用户可不重复提交；升级版本后缺少相应同意返回 AGREEMENT_REQUIRED，旧版本返回 AGREEMENT_VERSION_CHANGED。协议同意保留最初证据，不覆盖历史。local 自动创建明显标注的测试文本；非 local 忽略 test_only 记录，未配置正式文本返回 AGREEMENTS_UNAVAILABLE，不能用于正式注册。用户最新澄清：甲方尚无正式协议；开发方已在 `docs/agreements/` 拟好待确认草案，未启用。

Settings 新增可选 version，以保持 0.1 客户端兼容；响应始终带 version。建议前端 PUT 回传读取的 version，旧版本返回 409 VERSION_CONFLICT；省略 version 的旧调用采用事务串行、最后写入生效，不能宣称其也有旧版本检测。提醒字段目前仅保存偏好，不代表已实现发送任务；尚无已上线的结束音资产及选择配置时，非空 endBellAssetId 返回 400，不接收伪造引用。PATCH /me、手机号、密码、注销和会员权益尚未实现。

本地微信联调：

```sh
python3 scripts/prepare_wechat_probe.py
SERVER_PORT=18080 python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat
```

等 readiness=UP 后，开发者工具打开 `tools/wechat-login-probe`（自动生成的 project.config.json 不提交 Git）。该工程只有协议查看、真实 wx.login、本人信息/设置读取、刷新和退出；令牌与 code 只在闭包内存中，不放 page data、storage 或日志，不是正式前端工程。端口 9420 只能由一个自动化连接使用。不会上传、提审或发布。

独立测试：`./scripts/backend.sh test`；真实本机 MySQL：`python3 scripts/local_backend.py test --database-env ../.tools/mysql-test.env`；该辅助脚本只授权本机 33306 的 cxj_test，避免误连业务库。测试不加载真实微信密钥；WeChatGateway 为测试替身，真实供应商验收单独记录，不能混称。

本轮最终验收结果及外部依赖见 [微信用户阶段验收记录](backend-stage2-2026-11-09.md)。

## 2026-11-10：讲师基础与契约生成修复

新增 V3（content_teacher、teacher_follow、media_asset 元数据及外键），不修改 V1/V2。讲师可关联现有 active 用户，关联唯一，不自动创建管理员或合并账号。用户 roles 从未归档的讲师关联实时读取；归档保留原关联及关注记录。own_content 只可维护自身 teacherId，不能创建全局讲师、访问他人或更改绑定用户。课程建立后仍需添加课程/课节引用与归档一致性规则。

头像只允许已 ready 的 avatar 图片元数据，校验格式、10MB 上限、HTTPS 地址及归属；不允许用课程音视频充当公开头像。media_asset 目前只有存储元数据，尚无真实上传、文件探测、审核、转码和云端验证，不预置假文件。合同中的 nullable 头像可暂为空，完整头像功能继续随媒资推进。

修复 typescript-json-schema 0.65.1 在 noExtraProps + refs 下遗漏交叉类型字段的问题。scripts/generate_schema.cjs 使用同一 TypeScript 源的展开结果修复引用树，保留命名模型，再通过编译器核对全部 220 操作内 770 个对象的字段及必填性。没有改业务 DTO 或只手改生成 OpenAPI。修复了更新请求丢失业务字段、分页缺 page/pageSize、组合返回值丢失基础字段等问题。

本轮验证、接口清单与边界见 [讲师阶段验收](backend-stage3-2026-11-10.md)。云端授权当前待核实，可按 [开发授权核实说明](cloud-access-check-2026-11-10.md) 操作。腾讯云上传票据与旧通用字段存在差异，后续需双方先审阅 [具体契约提议](media-upload-contract-proposal.md)，本轮没有擅自改变 UploadTicket。

## 2026-11-10：开发存储开通

云端状态更新：已按用户明确授权代建上海私有加密 COS 开发桶、仅限该桶的编程子用户并保存受限密钥。真实 HeadBucket 返回 200；跨域规则尚未配置。详情见 [开发存储实测记录](cloud-setup-2026-11-10.md)，此前“云端授权待核实”为历史状态。私有配置 `.env.cloud.local` 尚未接入 Spring Boot；未上传文件、采购服务器或完成云端后端部署。

## 2026-11-10：真实媒资上传与服务端校验

以上“尚未接入/上传”是开通时的历史状态。现已通过 `--cloud` 受控加载本地私有云配置，完成真实 STS/COS 直传、隔离区文件验证、持久化处理任务、私有正式对象及后台预览。累计 29 个接口，上传契约经双方确认升级为 0.2.0。技术 ready 不等于审核发布，课程与用户录制继续保持私有。

真实开发工具已完成 wx.login → 用户会话 → wx.uploadFile → COS → complete → ready，不使用 Mock，不接管正式前端。测试、运行配置、默认技术上限、可恢复错误、真实上传证据及待完成边界见 [媒资阶段验收](backend-stage4-2026-11-10.md)。本机启动需加 `--cloud`；普通自动测试禁止加载真实云密钥。Dockerfile 已补 FFmpeg，尚未执行本机 Docker 验收。
