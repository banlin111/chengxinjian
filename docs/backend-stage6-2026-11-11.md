# 阶段六：管理员安全、学习记录与隔离运维

2026-11-11，Asia/Shanghai。从 `codex/backend-foundation` 的 `66c87d0` 接续；起点本地与远端一致，工作树干净。用户明确仍无域名和真实课程素材，保留既定接口和真实素材验收要求。正式前端仍由同事维护。

## 实现与契约

新增 V8，保留已应用的 V1–V7。契约仍为兼容的 0.3.0 / 224 个方法：`AdminLogin` 新增可选 `captchaRandstr`，先改 contracts，再生成 OpenAPI/README。腾讯 Web 验证码模式须同时提交原始 `captchaTicket` 和 `captchaRandstr`；local 老客户端不受影响。新增 14 个已有契约入口，累计 74 个，剩余 150 个没有实现入口；入口存在不是模块交付或上线百分比。

| 方法 | 路径（均加 /api/v1） | 范围 |
|---|---|---|
| GET | /me/learning | 从已有 lesson_progress 读取课节学习记录 |
| GET | /me/favorites | 当前可见课程/课节收藏，分页及 kind 筛选 |
| PUT | /me/favorites/{kind}/{id} | 课程/课节收藏与取消 |
| PUT | /me/likes/{kind}/{id} | 课程/课节点赞与取消，返回当前计数 |
| GET | /admin/permissions | 权限字典 |
| GET、POST | /admin/roles | 查询、创建角色 |
| PUT、DELETE | /admin/roles/{id} | 版本更新、删除未使用角色 |
| GET、POST | /admin/accounts | 分页、创建管理员账号 |
| PUT | /admin/accounts/{id} | 版本更新账号、角色、讲师绑定及启用状态 |
| POST | /admin/accounts/{id}/reset-password | 带原因和幂等键重置密码、撤销会话 |
| GET | /admin/audit-logs | 按上海自然日、操作者和动作查询 |

这批后台接口不等于完整后台用户管理、功能模块开关或未来全部业务权限已交付；用户列表/封禁/手机号、练习/直播活动等仍需后续实施。

权限管理要求全局范围及 `rbac:read/write`，审计查询另需 `audit:read`。内容管理员默认不获得这些权限。不能授予或修改自己没有的权限；字典外、重复权限/角色拒绝；本人内容角色当前只支持已隔离的 content:read/write，并须绑定未归档讲师。账号范围由角色确定，角色变更同步账号范围并增加版本。禁用和重置密码撤销已有会话；角色权限后续请求实时读取。全局控制行串行化敏感变更，禁止移除最后一个启用且同时有 rbac:read/write 的全局管理员。角色仍绑定账号时不得删除。密码类请求的幂等摘要另经 BCrypt 保护，避免留下可快速暴力校验密码的 SHA-256 摘要；响应快照无密码。

学习记录只读取当前课节/当前资产的原有进度；素材替换不继承旧完成状态，数据库历史仍保留。撤权保留可见内容的学习记录，但不产生播放权益。`state=purchased` 当前筛选已有记录中拥有有效独立单课权益的课节，人工授权也在其中，不能解释成真实订单，也不是“所有已购但未播放课程”的列表。无记录不伪造 lastPlayedAt。冥想/回放尚未实现，相关收藏类型显式返回 503 FEATURE_UNAVAILABLE；错误类型返回 400。课程目录 favorite/liked/likeCount 已接真实关系。下架/撤审内容不在收藏和学习列表展示，允许取消原收藏/点赞。重复收藏不刷新 savedAt，重复点赞不累加；每个用户的并发写入串行化。完成判定沿用阶段五待产品确认规则，上报不发打卡或奖励。

## 非 local 安全初始化

`AdminBootstrapCommand` 为独立 Java 命令，没有 HTTP 初始化入口；不启动 Web、worker、local 种子或自动迁移。操作前先备份并应用 V8。仅连接显式配置的 MySQL，事务锁定一次性控制行；已有任何管理员或持久化标记已消费时拒绝。创建安全随机业务 ID、BCrypt 密码、全局系统管理员角色和审计，权限来自当前字典；以后新增权限不会默默授予旧角色。

在受控服务器终端运行 `python3 scripts/bootstrap_admin.py --database-env /私有/backend.env --jar /已验证/app.jar`。配置文件仅包含 DB_URL/DB_USERNAME/DB_PASSWORD，0600、本人持有、非符号链接。用户名、显示名、初始化原因交互输入，密码双次 getpass；通过子进程 stdin 传入 Java，不进命令参数、历史、环境变量或日志。不会自动生成并公布正式密码。可在私有容器终端直接使用 PropertiesLauncher 启动该命令，输入仍须走私有管道。已有账号不能用此工具重置或“重新成为首位管理员”，以后走受控重置接口。

初始化不绕过验证码。云机实测管理员数量为0，尚未创建负责人管理员；需确定账号负责人并在私有终端设置密码后才能使用。演练仅在可清理的本机 cxj_test 执行，命令 `python3 scripts/smoke_admin_bootstrap.py --run-isolated-bootstrap` 会清空该测试库，禁止并发运行或改成开发/云端业务库。

## 正式验证码接入与当前办理项

采用腾讯云 Web/App `DescribeCaptchaResult`，固定 HTTPS 官方端点、CaptchaType=9，独立验证码凭据；不借用现有 COS 子用户权限。连接/读写/整体超时有界，不跟随重定向、不自动重试一次性票据、不打印 SDK 报文。只接受成功且未报告恶意的结果；trerror 容灾票据不放行。票据 SHA-256 在独立事务唯一占用，供应商调用不占数据库事务；密码错误、供应商失败或超时后须重新完成验证码。缺参数、权限、套餐或服务故障返回 503；无效、过期、重复票据返回 400。此阶段不支持 OTP，传入仍返回未配置，不假装二次认证成功。

部署填写 CAPTCHA_MODE=tencent、CAPTCHA_APP_ID、CAPTCHA_APP_SECRET_KEY、CAPTCHA_SECRET_ID、CAPTCHA_SECRET_KEY；最后四项按服务器私有方式交付，只有 CaptchaAppId 可给前端。CaptchaAppId 不是微信 AppID、云 APPID 或 COS 桶 ID。可信代理和公网真实客户端 IP 还需在 HTTPS 入口就绪时实测，目前不信任客户端自报 X-Forwarded-For。

当前必须配合的事项按实际办理，不要求重复采购：

1. **验证码已有资源核实**：到 [腾讯云验证码控制台](https://console.cloud.tencent.com/captcha)，在“验证管理”查看是否已有项目；已有则提供应用状态及 CaptchaAppId，后台密钥私下配置。没有则点击“创建验证码”，验证名称填写“澄心涧管理后台”，客户端类型选择 Web/App，验证方式按后台登录选择普通验证（例如滑动拼图），点击“确定”；在验证列表查看密钥。域名限制待正式后台域名确定后精确配置。服务如要求付费，先核对当前余额、套餐及价格再另行确认，本轮未购买。服务端子账号仅授权 captcha:DescribeCaptchaResult，不能用 COS 凭据代替。交付结果是可用的验证码应用和受限服务端凭据，而非账号截图或主账号密码。
2. **域名**：仍由甲方准备其主体名下域名与备案；拿到实际域名/DNS 授权后再配置 HTTPS、API/媒资合法域名、精确 CORS 和图片公开源。当前不提供虚假的公开地址。
3. **协议**：审阅 docs/agreements 中草案，确认主体全称、客服渠道、实际采集处理方式、版本及生效日期。交付批准文本后启用；缺失时非 local 仍拒绝注册。

官方接口与字段依据：[服务端验证](https://cloud.tencent.com/document/api/1110/36926)、[Web 接入](https://cloud.tencent.com/document/product/1110/36841)、[开通流程](https://cloud.tencent.com/document/product/1110/36839)，2026-11-11 核查。真实验证码应用/签约/票据尚未联调，不按适配代码或替身测试宣称通过。

## 运维行为

发现本机 MySQL 停止而旧 readiness 仍 UP，已从既有 mysql-data 恢复，未初始化或清空 cxj_dev。readiness 新增 db 指标；数据库失败返回 503，liveness 不受数据库故障牵连，不公开详细连接信息。[Spring Boot 健康组说明](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)。

`staging_maintenance.py` 复用现有 mysql_backup.py，生成 root 私有逻辑全备、SHA-256、原子 manifest；只有新备份成功后才清理带匹配校验 manifest 的自动备份，至少保留两份，不删除旧人工备份。隔离机暂定保留 14 天；本机异机副本保留 30 天，均为开发环境运维值，不是正式灾备 SLA。每日上海时间 02:30 加最多五分钟抖动，由 cxj-backup.timer 执行。任务检查每五分钟查看 readiness、上传/试听失败和超期任务、磁盘、备份新鲜度，失败进入 systemd 状态、journal 和私有 JSON。

本机 `pull_staging_backups.py` 通过既有固定主机校验 SSH 配置拉取自动备份并逐份校验，LaunchAgent com.chengxinjian.staging-backup 每小时及登录加载时运行；电脑睡眠、关机或断网时不能拉取，尚不是始终在线的异机备份服务。云端 /var/backups/cxj、异机 server/.local/backups/automatic 均为私有目录；未声称完成静态加密、COS 备份或增量恢复。监控报告明确 externalNotificationConfigured=false，尚无外部收件人/渠道和送达验收。

`verify_staging_restore.py` 仅恢复到新建的 cxj_restore_*，在隔离环境静止写入条件下核对源库前后及恢复库逐表 CHECKSUM、迁移，最后删除本次演练库，不覆盖源库。源有写入或校验不一致即失败，不据此自动回滚。旧镜像回退必须先核对迁移和权限兼容性；本轮 V8 是增量结构，具体演练结果记录于下表，不能推及未来迁移或有新增业务写入的生产库。

## 本轮证据（持续更新）

| 验收层级 | 结果与限制 |
|---|---|
| 1 代码 | 本文 14 个入口、离线初始化、验证码适配、备份/监测脚本；部分业务类型限制如上 |
| 2 自动测试 | H2 与原生 MySQL 8.4.11 各107项通过，无失败/错误/跳过；脚本8项通过，契约224方法/785对象及 OpenAPI 校验通过；验证码供应商使用替身 |
| 3 真实第三方开发联调 | 微信 IDE + 真实 code2Session/STS/COS/FFmpeg 81 检查通过，包含新学习记录/收藏/点赞、跨登录会话及人工权益授予/撤销；合成 PNG/JPEG/WebP/WAV/4秒MP4，最多1秒试听。fixture_cleanup=true。不是腾讯验证码、支付或真机验收 |
| 4 隔离云机/SSH 隧道 | 旧版本状态已重查；每日备份、每五分钟监测已安装并首次执行成功，异机副本已核 SHA-256。已部署 cxj-api:0c09472，JAR 与本机 SHA-256 相同；升级前恢复核对35表/V1–V7，升级后恢复核对38表/V1–V8，各自源库前后与恢复库 CHECKSUM 一致并移除演练库。暂回退2274752后基础检查通过，再恢复0c09472；9项隧道路由检查通过。没有云端已登录业务验收，因无正式验证码/协议/凭据 |
| 5 公网 HTTPS | 未通过；无域名 |
| 6 正式前端/微信真机 | 未通过；本轮只维护独立探针 |
| 7 上线验收 | 未通过；其他既定业务、真实素材容量/长播/弱网/后台音频及外部条件仍有缺口 |

部署提交 `0c09472ee01d01c5ef328425dd1fddf1c364b6af` 已正常推送，[GitHub Actions 42948814939](https://github.com/banlin111/chengxinjian/actions/runs/42948814939) 的 backend/contracts 均 completed/success。部署目录 /opt/cxj/releases/0c09472；实际镜像 ID 前缀7aff865cfd74；非root/只读根目录、API只绑定127.0.0.1:18080、数据库无宿主映射。云端实际 mysql,prod，未注入 local 密码/票据、微信/COS密钥或媒资公开源，CAPTCHA_MODE=unavailable。云机启动不代表新管理接口已通过真实登录验收。

新版本第一次隧道逐路由请求曾超时；云机内部服务正常，后续重新实测9项通过，超时未伪报为通过，也未以旧检查顶替。监测使用不存在的容器名作独立故障演练，systemd-run 返回非零且私有报告 task_inspection_failed，恢复正确容器后状态ok。外部告警仍未配置。备份定时器与本机每小时拉取均已加载，两个自动全备的异机副本校验通过；尚未经历完整14/30天保留周期。开发库升级前已私有备份，现有数据目录保留并迁移到V8，本机18080 readiness UP。

本地证据在忽略的 server/target/stage6-* 及 course-cos-smoke/result.json；初始化报告覆盖8项打包非local检查。历史阶段五结果不能替代本轮执行。本轮未购买新云资源，未上传、提审或发布小程序。
