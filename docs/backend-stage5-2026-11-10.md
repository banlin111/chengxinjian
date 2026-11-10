# 课程、课节、编辑审核与播放联调

2026-11-10，北京时间。起点为协作分支 `codex/backend-foundation` 的 `cd41648`。本轮开始时本地、远端分支一致，工作树干净。保留 V1–V4，新增 V5–V7。正式前端由前端开发者维护。

## 接口清单

以下路径均在 `/api/v1`。阶段四的 29 个方法继续有效，本轮增加 31 个方法，累计 60 个。契约 0.3.0 共有 224 个方法与路径组合。4 个新增管理方法先写入 TypeScript，再生成 README 和 OpenAPI。其余新增实现沿用原契约。另有一个返回 JPEG 字节的展示地址，不计入 JSON 业务接口数。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | /courses、/courses/{id}、/courses/{id}/lessons | 公开目录，可选用户会话 |
| GET、POST | /admin/content/courses、/admin/content/lessons | 分页、创建 |
| GET、PUT、DELETE | /admin/content/courses/{id}、/admin/content/lessons/{id} | 详情、全量更新、归档 |
| GET | /admin/assets/{id}/processing、/admin/assets/processing-failures | 上传校验和试听任务状态、失败列表 |
| PUT | /admin/assets/{id}/review | 人工审核编辑素材，携带 decision、reason、version |
| POST | /admin/assets/{id}/processing/retry | 带原因和 UUID 幂等键重试失败任务 |
| GET | /membership/plans、/me/membership、/me/entitlements | 上架套餐、当前会员和分来源权益 |
| GET、POST | /admin/content/membership-plans | 查询和创建套餐 |
| GET、PUT、DELETE | /admin/content/membership-plans/{id} | 详情、编辑、停售 |
| GET、POST | /admin/users/{id}/grants | 查看、人工发放独立权益 |
| POST | /admin/grants/{id}/revoke | 按来源撤销人工授权 |
| POST | /playbacks、/playbacks/{id}/renew、/playbacks/{id}/events | 课节授权、续签和上报 |

### 后台与前端联调

1. 内容管理员上传 `avatar`、`cover` 或 `course_media`，按 0.2.0 的 COS 字符串字典直传。技术状态 ready 后，具有全局范围、`content:read` 和 `moderation:review` 的审核员用现有后台预览检查内容，提交审核。权限字典增加审核权限和用户读写权限，默认内容管理员不会自动获得它们。
2. 审核通过的头像、封面仅从服务端验证的私有文件解码，重新绘制最长边 1280 像素的 JPEG，清除原文件元数据。数据库保存展示副本，`GET /media/images/{id}` 仅在审核有效时返回字节。撤回后再次访问返回 404。技术 ready 状态独立保留。用户私有录制及用户自行上传的图片不进入此编辑投放入口。
3. 创建课程时指定已审核封面、课程类型分类和讲师。发布课程要求讲师和分类已发布。创建课节时指定课程及管理员有权引用的课件，课节时长取自文件校验结果。公开目录仅统计已发布且已审核课节。讲师范围的列表、写入和幂等重放都检查当前绑定。类型变更、父项下架和归档有引用检查，内容操作保留历史版本的引用。课节不跨课程迁移。
4. 每一组素材和试听秒数只登记一项裁切任务。Worker 限时从私有源文件重新编码，移除原元数据，写入单独的私有 COS 对象。试听成品就绪后目录才返回可试听。任务租约可恢复，五次失败后可在失败清单查看原因并重试。格式不合格的上传需换合格文件重新上传。
5. 普通用户以课节 ID 调用播放接口。服务端检查账号、课程、分类、讲师、课节、媒资审核和当前有效权益。免费、会员、单课和可试听路径均在服务端判定。已有单课授权在课程改变销售方式后仍然有效。续签重新校验。匿名目录、用户资产详情、后台预览与用户播放使用独立的权限检查。
6. COS 读签名使用仅有单对象 GetObject 权限的临时凭据。签名最长 120 秒，并受当前权益到期时间限制。撤权会阻止新申请和续签，已签发的 URL 在剩余有效期内仍可能使用。播放器应按 `expiresAt` 提前续签。试听只签裁切对象。列表、课节、事件回执均不携带源文件地址。
7. 人工授予会员可用套餐配置天数或明确指定天数、永久授权。各次手动赠送从发放时起独立生效，撤销只影响其对应的来源。无默认价格或套餐天数。支付、退款、购买会员叠加延期与奖励发放仍需各自的可信来源和事务实现。测试中的单课 `purchased` 表示直接持有该课权益，本次来源是人工授予。
8. 新播放会话读取该账号、课节和当前文件的恢复位置。事件按 eventId、sequence 和请求键去重。服务端按接收时间间隔、倍速、真实时长和实际位置变化限制有效秒数，拖动事件只更新位置。当前实现按累计有效时长达到完整时长标记完成，试听本身不能标记课节完成。这是开发阶段判定，尚待产品确认。上报不发放打卡或奖励。

课程收藏、点赞功能尚未接入，当前目录相应字段为初始值。推荐排序尚无运营编排，当前以新建时间排序。该阶段的播放接口只实现课节目标；冥想、回放与混音等目标未实施。学习历史列表、全部后台用户管理、完整 RBAC 和生产人机验证仍在后续范围。

## 验证与证据

| 层级 | 本轮结果 |
|---|---|
| 契约与配置 | `npm run check` 核对 224 个方法内的 785 个对象结构。生成器统计 161 个定义，OpenAPI 组件含额外 ErrorEnvelope 共 162 项。OpenAPI 3.0 校验通过。私有配置加载器 5 项通过。 |
| 自动测试 | JDK 21、H2 和原生 MySQL 8.4.11 隔离库分别通过 91 项，无跳过。覆盖目录、父项引用、审核撤回、讲师越权、幂等重放、版本冲突、过期和并存权益、按来源撤权、试听限制、续签、事件去重、恢复进度及处理任务重试。自动用例中的存储替身只用于权限和错误路径。 |
| 真实开发联调 | 打包 Java、本机 cxj_test、微信 IDE、真实 code2Session、腾讯 STS、上海私有 COS、FFmpeg。78 个检查通过。合成图片完成审核、重新绘制、头像引用和撤回；4 秒合成视频裁切，另行完整解码其 1 秒衍生文件。相同微信账号先试听、获人工单课权益后下载完整文件、撤销后恢复试听。IDE 发起签名下载、上报 seek 和续签。临时登录退出、人工权益撤销、测试内容归档，云对象含试听副本删除后逐个 HEAD 核对 404。 |
| 本地升级与恢复 | cxj_dev 升级前生成 0600 备份，在全新隔离 schema 恢复并逐表核对 23 张表行数及 Flyway 版本，然后删除演练 schema。开发库实际迁移至 V7，18080 readiness=UP，课程及套餐路由返回 OK。 |
| 公网、正式前端、真机、上线 | 此轮均待验收。开发工具使用本地端口，下载通过不代表真实设备播放、长时续播、商业课程容量、真机后台音频、退款或支付已验收。 |

本机证据在被 Git 忽略的 `server/target/` 中，包括 `stage5-h2.log`、`stage5-mysql.log`、`stage5-loader.log`、`stage5-contract.log`、`stage5-openapi.log`、`course-cos-smoke/result.json`、`stage5-restore.json`。真实供应商复测仅在独占本机可清理的 cxj_test、18082 和 IDE 自动化端口时执行。已配置的 stdio 解释器和包装器路径分别通过 `WECHAT_MCP_PYTHON`、`WECHAT_MCP_WRAPPER` 传入，再执行 `../.tools/cos-runtime/bin/python scripts/smoke_media_cos.py --run-real-cloud-upload --course-probe`。独立探针不持久化任何业务凭据。GitHub Actions 不持有微信或 COS 密钥。

## 公网部署准备

2026-11-10 只读查看当前登录的腾讯云控制台，总览显示 CVM 和轻量运行中均为 0 台，可用余额为 0 元。用户确认由甲方后续充值，到账后开发方创建隔离实例。现有开发 COS 的 HeadBucket 成功，GetBucketCORS 返回 NoSuchCORSConfiguration。Web 管理后台直传需在正式测试源确定后按源配置并实际验证 CORS。

- 在确定的主机或 CI 构建处执行 `./scripts/backend.sh test`，用 `docker build -t cxj-api:<已验证提交> server` 生成镜像，保留对应 Git SHA 和镜像 ID。`server/.dockerignore` 限制构建上下文，只纳入打包 JAR 和 Dockerfile。
- 运行 `scripts/staging_release.sh /部署机上的私有环境文件 cxj-api:<已验证提交>`。Compose 叠加 `infra/compose.staging.yml`，只启动 MySQL 与应用。数据库仅在容器内部网络可达，API 监听主机回环，入口反代负责 TLS。应用使用 mysql、prod profiles；FFmpeg、非 root 用户、只读根文件系统和临时文件空间在镜像/运行配置中。运行脚本会检查 readiness 和课程接口。
- 部署环境文件包含 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、基础 Compose 解析所需的 `REDIS_PASSWORD`、`MEDIA_PUBLIC_ORIGIN` 和精确的 `ADMIN_ALLOWED_ORIGINS`。按需安全注入该环境的微信与 COS 配置。环境文件由部署账号持有，权限 0600，使用 Docker Compose 环境文件的引号规则。测试域名、备案、HTTPS、微信合法域名、正式协议、管理员初始化、人机验证、监控和备份仍需实际接入。
- 迁移前在限制访问的目录执行 `python3 scripts/mysql_backup.py backup --client-config /私有/mysql.cnf --database chengxinjian --archive /私有备份/本次.sql.gz`。保存输出 SHA-256。恢复演练使用 `restore-drill --database cxj_restore_唯一后缀 --archive 同一文件 --sha256 校验值 --client-config /私有/mysql.cnf`。工具只创建新的演练库，已存在同名库会拒绝恢复。生产备份须另行实现异机保存、加密、保留周期和定期恢复验证。
- 若上线候选运行异常，先保留现场并停止向故障版本导流。仅在旧版本已验证兼容当前数据库和权限约束时切回其镜像。发生不兼容迁移时停止写入，从验证过的备份在独立库恢复，核对迁移后新增的业务数据并完成补偿，验证旧版本后再切换。不能仅启动阶段四旧代码来管理已有阶段五引用，也不能对有写入的库直接覆盖恢复。
