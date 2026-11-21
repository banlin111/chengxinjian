# 练习录制与课程奖励联调

开发演示使用合成录音、真实微信开发身份、临时审核员和独立 MySQL。测试云机与正式服务的录制奖励默认关闭。甲方尚未确认奖励课程、库存、活动时间、发放期限、录制形式及时长，在确认前请保留关闭状态。录制审核可以独立使用。

## 小程序与后台交接

前端核对基线为 `codex/frontend@223b2ea`。以下路径相对于该分支，接口前缀为 `/api/v1`。用户令牌和管理员令牌各自保存在对应客户端。

| 接入位置 | 操作与参数 | 页面应呈现的结果 |
|---|---|---|
| `apps/mobile/src/pages/practice/index.vue` 录制入口 | 获得用户同意后录音或选取支持的媒体，按实际文件计算 SHA-256。用本人令牌 `POST /uploads`，purpose 为 `practice_recording`；按返回票据向对象存储提交文件，再 `POST /uploads/{assetId}/complete` 并轮询 `GET /assets/{assetId}` | 展示上传与处理状态。技术校验 ready 后才允许提交。生产 COS 表单须使用票据返回的 method、formData 和 fileField；本机探针专用 PUT 不用于正式 COS |
| 同页练习结果 | `POST /practice/submissions` 传 `practiceSessionId, assetId, text`。会话须本人已完成且有服务端有效时长，录制文件须本人所有 | 展示 pending。网络超时复用同一个 UUID Idempotency-Key 和请求体。重复上传相同字节不能产生第二份资格 |
| `apps/mobile/src/pages/profile/index.vue` 本人记录 | `GET /me/practice/submissions` 分页，`GET /practice/submissions/{id}` 刷新当前状态 | rejected 展示 reviewReason，重新录制后以新素材和新请求键提交。本次练习已有 pending 或 approved 时不再添加候选 |
| Web 管理后台待接入的录制审核页 | `GET /admin/submissions?status=pending`，`POST /admin/submissions/{id}/preview`，`POST /admin/submissions/{id}/review` 传 `decision=approve或reject, reason, version` 与 UUID 请求键 | 全局范围且具备 moderation:review 的管理员可取得 120 秒私有试听票。审核后刷新队列，冲突时重读状态。过期重新申请票，不持久化签名 URL |
| 练习页奖励入口 | `GET /rewards?submissionId={id}`。选择规则后 `POST /reward-claims` 传 `ruleId, submissionId` 与 UUID 请求键 | 使用 eligible、reason、claimId 决定按钮状态。关闭或无已发布活动显示暂无奖励。移除硬编码“审核通过得 200 积分” |
| 个人中心、课程详情 | `GET /me/reward-claims`、`GET /reward-claims/{id}`、`GET /me/entitlements`，以课程 ID 匹配 source=reward。领取后重读课程和课节的 access，再 `POST /playbacks` | issued 表示课程权益已入账；按钮按 access.allowed 显示“开始学习”。`pages/home/index.vue` 两处所有非 free 显示 VIP 的判断须修正，purchase 显示单课，领取不产生会员或支付订单 |
| Web 管理后台待接入的奖励配置与发放记录页 | `/admin/content/reward-rules` 支持分页、创建、详情、带 version 更新及归档；`GET /admin/reward-claims?status=issued&userId=…` | 需要全局范围与 rewards:manage 权限，新权限默认未分配。库存输入为总量，stockRemaining 为剩余量。发放后不能改课程或把总量降到已发量以下 |

上传、完成、提交、审核、创建规则、领取和申请课节播放沿用契约的幂等键；后台试听每次返回新短效票。分页使用 page、pageSize。授权页和接口的机器可读字段见 [TypeScript 契约](../contracts/api.ts) 与 [OpenAPI](../openapi.json)。

## 发放与审计

- V16 增加录制提交、录制课程奖励规则及领取表，与原有练习、素材和 entitlement_grant 关联。按用户与媒体摘要去重。每次练习最多留一条待审或已通过记录，驳回历史保留。
- 本批可配置 submission、course、requiresReview=true 的组合。录制审核本身不发放。发布规则还要求服务开关 RECORDING_REWARDS_ENABLED=true、课程可见、封面已审核。开始时间包含，结束时间不包含；审核通过时间与领取时间均须落在活动期内。
- 每份录制只能兑换一次。每人每门奖励课仅发一份，并受规则上限约束。已有生效单课权益时不占库存。领取事务锁定本人和活动规则，写入 source=reward 的课程权益、领取单、规则版本并扣库存。并发重试返回同一领取单。相同幂等键更换请求体返回 409。原单可在活动结束或关闭后重读。
- 本批课程权益的账本表达为无到期日。若甲方审批限期课程奖，需要先增加期限配置与验证，再启用对应活动。连续打卡、实物、积分、物流均有各自的业务条件。

## 开发演示

使用已配置的 WECHAT_MCP_PYTHON 和 WECHAT_MCP_WRAPPER，在独立微信开发者工具工程 `tools/wechat-login-probe` 打开 `pages/login/index`。在仓库根目录用工作区 cos-runtime 的 Python 执行 `scripts/smoke_media_cos.py --loopback-rewards`。该模式只接本机 18082、cxj_test、回环对象存储与微信开发身份。操作前须有本批打包 JAR、FFmpeg、测试协议和空闲端口。

学员端使用真实 wx.login 和 HTTP，分三次登录完成提交、查看驳回与重新提交、查询资格与并发领取。后台端通过独立 HTTP 客户端检查审核队列、读取并逐字节核对试听文件、提交审核并查看发放记录。页面逐步显示实测结果；完整成功后生成可离线打开的 `server/target/recording-rewards-loopback/demo.html`。开发端演示专用活动及测试身份在结束后停用，对象随进程释放。

## 2026-11-21 验证记录

| 层级 | 本批结果 |
|---|---|
| ① 代码 | 应用提交 d620c3e5001f1a2ccd9c93b0f30ee4c5ff055622。V16 新增 16 个入口，累计覆盖 139/230。手机号冲突保护沿用原实现。 |
| ② 自动测试 | H2、独立 MySQL 8.4 各 144 项通过，包含同人并发重领、两人争末份、审核重试和同课跨规则重复领取。10 项 Python 测试通过。TypeScript、230 项契约生成与 OpenAPI 校验通过。打包 JAR 实际 HTTP 校验匿名访问和管理员代领的拒绝分支。 |
| ③ 真实第三方开发联调 | 独立微信开发者工具真实登录，学员提交、审核端读取私有媒体、驳回重提、批准、并发领取、领取前后课节权限切换均实测。领取单相同，课程权益一份，授权下载 6,364 字节的合成课节。临时活动已归档、测试授权已撤销；本次 COS 请求数为零。存储、管理员验证码使用本地开发实现。本次未采集真人声音。 |
| ④ 隔离云机与 SSH | 从 f8170e9 / V15 升至 d620c3e / V16。云机 JAR 和本机探针 JAR 的 SHA-256 均为 d0f1572daa9e8f4fade34778ff17f0e823416cf562fa26260cdf1c1212c14772。readiness 为 UP，应用绑定 127.0.0.1，数据库无宿主端口。SSH 隧道检查 16 个受保护入口匿名均返回 401。保留 1 个已启用管理员，新增权限获授权角色 0，录制、活动、领取和权益均 0，服务开关 false。迁移前后各备份；迁移后临时库恢复校验 58 张表、V1 至 V16，源库保持一致。 |
| ⑤ 公网 HTTPS | 此服务没有本批验证通过的外部 HTTPS 业务域名、媒体域名和微信合法域名链路。 |
| ⑥ 正式前端及真机 | 对 223b2ea 正式前端编译产物运行开发者工具与 Chromium。练习页仍显示“审核通过得 200 积分”。H5 点击“录制上传练习心得”和“全部奖励”只弹预留说明；该流程网络记录仅有成功的首页 GET。Web 管理页面、正式录制链路和微信真机尚待对接验证。 |
| ⑦ 上线验收 | 可演示本机开发闭环。本次未覆盖正式素材与授权、真实对象存储全链路、真机麦克风授权和中断重传、正式审核后台、甲方奖励规则与上线签收。 |

实现提交的 GitHub 检查见 [Backend checks](https://github.com/banlin111/chengxinjian/actions/runs/44865497521)。本机证据均在 Git 忽略的 server/target 下，包括 recording-rewards-loopback/result.json、demo.html（同目录）、reward-h2.log、reward-mysql.log、reward-python.log、reward-jar-smoke.log、reward-staging-state.json、reward-staging-http.json、reward-staging-restore.json、reward-official-ide.json 和 reward-h5-result.json。
