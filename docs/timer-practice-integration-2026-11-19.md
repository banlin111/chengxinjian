# 纯定时练习与上海自然日打卡联调

基线为后端 `50b0751`、前端 `223b2ea`。V11 保存练习、事件回执、请求键和有效时间段，V12 保存用户显式打卡及其请求回执。沿用 0.3.0 契约，新增已有契约中的七个入口。后台用户详情、列表的总练习秒数取自持久化练习，首页和后台用户的连续天数读取实际打卡。手机号归属、换绑、会话撤销及权益模型沿用原实现。

## 调用顺序

路径前缀为 `/api/v1`。以下入口要求当前普通用户 Bearer。POST 需 UUID `Idempotency-Key`。返回现有 Envelope，读取 `data`。

| 操作 | 请求 | 返回与恢复 |
| --- | --- | --- |
| POST /practice/sessions | `{"kind":"timer","plannedSeconds":600,"tracks":[]}` | 返回 Practice、真实 ID、服务端 startedAt、version=1、heartbeatSeconds。600 为调用示例。guided、noise、音轨、预设或结束音资源返回 503 FEATURE_UNAVAILABLE。 |
| GET /practice/sessions/{id} | 当前登录用户的练习 ID | 读取最新状态、累计有效秒、version。换会话、换设备可用同一账号读写。其他用户读写均为 404。 |
| POST /practice/sessions/{id}/events | `{eventId,sequence,action,activeDeltaSeconds,occurredAt}` | action 为 heartbeat、pause、resume、finish、abandon。sequence 从 1 开始，下一条等于最新 Practice.version。activeDeltaSeconds 为自上一事件以来的活跃秒数，occurredAt 带时区。 |
| GET /me/practice/sessions | 可选 from、to、page、pageSize | 按**开始日**筛选本人记录，含各状态；开始秒和 ID 倒序。页码从 1 开始，默认 20，最大 100。 |
| GET /me/practice/stats | 必填 from、to，例如 2026-11-19，含首尾，最多 366 天 | 每日秒数、是否打卡、区间总秒、按**结束日**计的完成数、区间打卡天数、截至 to 的连续天数。无练习日明确返回 0。超过一年分段查询。 |
| POST /checkins | `{practiceSessionId}`，引用本人练习 | 用户主动打卡，仅检查请求处理时上海当天的累计有效时长。未配置规则返回 503 PRACTICE_RULE_UNCONFIGURED；未达标为 409 CHECKIN_NOT_ELIGIBLE。成功留存当天秒数、阈值和规则版本。 |
| GET /me/checkins | 必填 month，例如 2026-11 | dates 为该月实际打卡日期，checkedInToday、streakDays 为请求当日状态。尚未打今日卡时，昨天连续记录保留至今日结束。 |

创建和事件超时要复用原键、原体。相同 eventId/sequence/内容也可识别设备再次投递；重复返回首次结果快照，终态后重试亦然。相同标识、不同内容为 409 IDEMPOTENCY_CONFLICT。客户端应保留每个尚未确认事件的完整内容和原键，按序单路发送。收到陈旧回执时勿以较低 version 覆盖当前状态。有缺口返回 409 PRACTICE_SEQUENCE_CONFLICT；读详情，对照待发队列从缺失处补传。另一个设备抢先修改状态时刷新详情，切勿更换键盲目重放旧意图。

active 可暂停或直接结束。paused 仅可恢复、完成或放弃，暂停期间、恢复动作均不计秒。终态拒绝后续新事件，返回 409 PRACTICE_STATE_CONFLICT。finish 表示用户结束本次练习，允许提前结束。abandon 保留已收到的有效秒，排除在完成数之外。未确认或后台无事件的时间不会随 GET 自动增加。

## 服务端如何计时

存储精度为 Unix 整秒。每个事件先取客户端发生秒、服务器接收秒二者中较早的一秒为区间结束。早于上次已接受事件或超前服务器超过 30 秒返回 400。起点取上次事件、结束减本次声明秒数、单事件技术上限、服务器补传窗口共同允许的最晚起点；累计不超过计划秒数。长时间断网可恢复状态，超过核验窗口的历史时间不给时长。默认心跳 20 秒、单事件上限 60 秒、可核验最近 300 秒，均属传输和防异常计时口径。

同一用户写入先取得账号行锁，再在一个 READ COMMITTED 事务中检查重放、状态、已占时间段、剩余计划秒，保存新增区间及原始回执。重叠归首次已提交的会话，后到会话仍可补上中间空隙。按 `Asia/Shanghai` 的午夜分割半开区间；跨午夜的练习分别贡献两天，每一秒最多归一个会话。已确认时长是服务器限幅后的上报记录，不能证明用户实际处于冥想状态。

Spring 属性 `app.practice.min-timer-seconds/max-timer-seconds` 可限定计划时长，未配置时接受正 32 位整数；`max-event-seconds/backfill-seconds` 可调技术边界。隔离配置默认 `PRACTICE_CHECKIN_SECONDS=0` 且 `PRACTICE_RULE_VERSION` 为空，表示规则待批准。启用须同时提供正阈值和非空版本。配置测例中的 12 秒只用于测试。有规则时 Practice.checkinEligible 描述账号上海当天合计是否达标，是否已打卡单独读取。重试同一打卡键可跨午夜取回原回执；当天同账号其他成功请求返回 alreadyCheckedIn=true，新的一天须发起新意图。只存显式当日打卡；已发布的打卡事实和秒数在后续配置变更后保留。当前 rewards 为空，练习、播放和打卡均不会授予课程、实物或积分。

## 正式前端接入单

核对同事分支 `223b2ea` 后，本批未修改正式前端。

| 文件（apps/mobile/src/ 下） | 接入点与预期 |
| --- | --- |
| services/http.ts | 现有匿名 GET。补登录 Bearer、POST 与 UUID 键、并发共用一次刷新、用原请求重试；401 进入刷新或重新登录，409 依据业务码处理，503 规则待确认单独展示。 |
| pages/practice/index.vue | startPractice 当前打开 sampleCourses[0]。纯定时入口使用创建参数，保留真实 ID；按返回间隔上报 heartbeat，暂停/恢复/退出持久化事件，重新进入读详情或本人记录恢复。用 stats 和月历替换静态连续 6 天、里程碑及示例奖励；用户主动点击时提交 checkins，成功刷新首页和月历。 |
| components/PlayerScene.vue、composables/usePlayerTransition.ts | 当前是样例课程播放控制。区分纯定时与课节播放器，timer 不申请课节播放票据；前后台切换保存同一 ID、序号、发生时刻和待发事件。按钮与队列等待服务端确认，练习秒数以回执为准。 |
| pages/profile/index.vue | 替换静态“打卡 128 天”，当前连续天数读月历；按日期区间显示有效总时长，历史列表分页读本人记录。 |
| pages/home/index.vue | 登录态 GET /home 可显示本人 checkedInToday、streakDays，打卡完成后刷新。139、167 行仍将非免费全部显示 VIP；依 accessPolicy 将 purchase 显示为“单课购买”、member 为“会员课程”、member_or_purchase 为“会员可学 · 可单购”、free 为“免费”。课节按钮按 lesson.access.reason 和真实 lessonId 处理。 |

Web 后台分支中未见管理工程。现有 GET /admin/users、/admin/users/{id} 返回真实总练习秒数及连续天数，仍需全局 user_directory:read。公告的管理和用户已读详见[公告交接](notices-integration-2026-11-19.md)，播放器和购买标签详见[课程交接](course-player-integration-2026-11-16.md)。

## 验证与证据

隔离测试使用 H2 和本机 33306 的 cxj_test。独立探针复用已有微信 IDE 包装器、测试库和打包 JAR，独占 18083 / 9420。按交接文件设置 WECHAT_MCP_PYTHON、WECHAT_MCP_WRAPPER 后运行 `python3 scripts/smoke_phone_reauth.py --practice`。仅加载测试库与微信白名单配置。探针通过 wx.login 建立用户会话，暂停后退出并重新用同一微信账号登录，核对详情、重试、完成、列表、统计、默认规则拒绝打卡并退出。测试记录保留在可清理的 cxj_test。证据文件位于本机忽略的 server/target/practice-*，最终执行结果另列于下。

本机最终全量测试 H2、原生 MySQL 8.4.11 各 133 项，无失败、错误或跳过；其中七个新增集成场景覆盖并发创建、并发重复事件、跨设备重叠和间隙、时钟与计划上限、过期补传、跨午夜、换登录会话、终态重试、打卡并发/跨日重放及断签。手机号冲突保护和人工权益回归随全量通过。旧手机号测试首次全量执行时进程经过约 991 秒，5 分钟凭证已过期导致预期 400 实际 403；该测试改用固定时钟，再次全量通过。应用的凭证到期和手机号归属逻辑未变。Python 配置/运维 10 项通过，契约 224 个方法 / 785 个对象结构、OpenAPI 校验通过。上述验证不使用生产数据。

真实微信开发联调使用开发者工具独立工程、微信 code2Session、端口 18083 的打包服务和 cxj_test。暂停后重试、退出再登录、恢复并完成，练习列表中 version=4，明细增加 3 秒，日统计也增加 3 秒和 1 次完成；显式打卡返回未配置。证据为 `practice-real.json`、`practice-probe.log`。本次没有使用短信、验证码、COS 或付费资源。
