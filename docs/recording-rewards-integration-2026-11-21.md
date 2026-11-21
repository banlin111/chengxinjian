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
