# 课程目录、详情和课节播放联调

前端基线为 `codex/frontend@223b2ea`，以下位置都在 `apps/mobile/src/`。验证使用同事分支构建的 H5，关闭 `VITE_USE_MOCK`，使用开发库真实上传、审核和发布的合成课程。首页首卡的接口字段为 `purchase`，浏览器确实显示 VIP。随后打开课程库，页面展示示例卡片，未发出课程列表请求。携带同一真实 UUID 打开详情，也未发出详情请求，页面显示“暂时找不到这门课程”。这三处可分别复现。

## 同事需要修改的位置

| 前端位置（链接固定到本次核对的提交） | 改动与接口 |
| --- | --- |
| [services/http.ts](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/services/http.ts#L8) | 当前只支持匿名 GET。补用户 Bearer、POST、Idempotency-Key 和业务错误处理。需要登录时展示当前协议并取得同意，调用 wx.login，将一次性 code 与协议版本交给 `POST /auth/wechat/login`。令牌失效时对并发请求共享一次 `POST /auth/refresh`，保存轮换后的两枚令牌，再用原幂等键重试原请求。 |
| [pages/home/index.vue](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/pages/home/index.vue#L139) | 第 139、167 行的首卡和新课卡都用“非免费”显示 VIP。使用下表的四种策略；第 49 行 openCourse 从真实 courseId 进入详情，或者先取得课节目录并让用户选择。 |
| [pages/courses/index.vue](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/pages/courses/index.vue#L13)、[services/course-preview.ts](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/services/course-preview.ts#L7) | 真实模式用 `GET /categories?kind=course` 和 `GET /courses` 替换静态筛选、特色卡和单条示例。按返回的 categoryId 查询，切换筛选回第一页，丢弃旧请求的迟到响应。分类名称来自服务端。第 50 行列表徽标也要改成 accessPolicy。点击卡片传真实 course.id。 |
| [pages/course-detail/index.vue](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/pages/course-detail/index.vue#L19) | 第 19–36 行的固定目录、本地 ID 查找、固定首节试听改为详情和课节列表两个 GET。目录用 lesson.id 作 key。第 65、81–94 行标签和按钮由本门课的策略、当前课节的 access 决定；`trial` 标试听，`locked` 显示锁定。 |
| [composables/usePlayerTransition.ts](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/composables/usePlayerTransition.ts#L40)、[stores/player-preview.ts](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/stores/player-preview.ts#L6)、[pages/player/index.vue](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/pages/player/index.vue#L21) | 微信同页展开状态和 H5 导航都传 courseId、所选 lessonId。当前 H5 路由只带 courseId；fromCourse 分支即使关闭 mock 也查示例。真实模式从同一课节恢复，无法找到时显示错误。 |
| [components/PlayerScene.vue](https://github.com/banlin111/chengxinjian/blob/223b2ea/apps/mobile/src/components/PlayerScene.vue#L34) | 替换课程总时长、百分比假进度及第 89 行布尔切换。申请票据后按 mediaKind 绑定音频或视频，进度条使用票据 durationSeconds 和真实媒体时间，按 resumeSeconds 定位，接入续签和事件。切换课节或关闭时释放旧播放器、监听器和定时器，防止旧票据回调写入新课节。 |

## 角标和操作

`course.accessPolicy` 描述销售方式；`lesson.access.reason` 描述该用户此刻可播放的范围。两者应分别展示。

| accessPolicy | 建议课程角标 | 没有完整权益时的说明 |
| --- | --- | --- |
| free | 免费 | 免费学习 |
| purchase | 单课购买 | 可按单课获取，会员身份不能单独解锁 |
| member | 会员课程 | 会员权益可学 |
| member_or_purchase | 会员可学 · 可单购 | 两种方式都可获取 |

当 `reason` 为 `free/member/purchased` 可申请完整版。人工授予单课权益也返回 `purchased`，按钮可写“开始学习”或“继续学习”。`trial` 只播放服务器裁出的试听对象，按 `access.trialSeconds` 展示试听长度。`locked` 不申请播放。目录的 `lesson.trialSeconds` 是配置值，试听成品可能尚未就绪。单价字段 `priceFen` 单位为分，支付接口尚待交付。

## 接口与顺序

路径统一加 `/api/v1`，返回读取成功 Envelope 的 `data`。目录 GET 支持匿名；携带用户令牌可读本人权益和恢复位置。

1. `GET /courses?page=1&pageSize=20&sort=newest`，可按 categoryId、teacherId、difficulty 等筛选。取 `data.items[].id`。页码从 1 开始，最大 pageSize 为 100。
2. `GET /courses/{courseId}` 与 `GET /courses/{courseId}/lessons?page=1&pageSize=20` 可以并发。课节按 sort、id 排序。封面撤审或父内容不可见时处理 404，目录为空时不启动播放器。
3. 用户播放需要用户会话。用 `GET /agreements/user`、`GET /agreements/privacy` 取得实际文本及版本，经用户同意后提交 `POST /auth/wechat/login`。正式协议缺失时服务端可能返回 503。后端登录返回 `accessToken/refreshToken`，后续播放接口传 `Authorization: Bearer <accessToken>`。
4. `POST /playbacks` 请求示例为 `{"target":{"kind":"lesson","id":"所选课节ID"},"platform":"wechat_ios"}`。platform 按实际客户端填写，支持 wechat_ios、wechat_android、wechat_harmony、wechat_windows。响应使用 playbackId、url、expiresAt、durationSeconds、resumeSeconds、mediaKind、heartbeatSeconds 和 access。签名地址只交给播放器，不放进路由、诊断日志、分享参数或持久缓存。
5. `POST /playbacks/{playbackId}/renew` 无请求体，在 expiresAt 前留出网络时间续签。成功后沿用播放会话并保留媒体位置。已过期或完整权益已降为试听时可能返回 410 PLAYBACK_EXPIRED，此时重新获取目录并申请新票据；无可播权益为 403 CONTENT_LOCKED，下架为 404。已发签名可能在剩余期限内仍可用。
6. `POST /playbacks/{playbackId}/events` 传 eventId、递增 sequence、kind、整数秒 positionSeconds、playedDeltaSeconds、speed、occurredAt。按 heartbeatSeconds 和真实播放/暂停/拖动/前后台/结束事件上报。拖动上报 seek、playedDeltaSeconds=0。用回执的 positionSeconds、creditedSeconds、completed 更新状态，creditedSeconds 为累计认可秒数。完成口径仍待产品确认。
7. 申请、续签和事件 POST 均带 UUID `Idempotency-Key`。每次新操作用新键；同一操作超时重试保留原键和请求体，事件也保留 eventId、sequence、occurredAt。409 检查事件顺序或键冲突。重新打开同一课节，以新票据的 resumeSeconds 恢复。学习历史可用 `GET /me/learning` 核对。

## 执行结果和复验

后端修复位于 `f8f9aa1`。新增回归用例先复现了完整版播到 24 秒、撤权后 5 秒试听目录仍返回 24 秒的问题。修复后目录和票据都返回 5 秒，学习历史保留 24 秒。Java 测试在 H2 和 MySQL 8.4.11 各 110 项通过，无跳过。此次未改契约或迁移。

独立微信探针从真实分类的课程列表读取 ID，获取详情和课节目录，用同一微信用户三次进入。每次都使用续签得到的真实 COS 地址下载临时 MP4，由微信开发者工具中的原生 video 组件播放；监听 play、timeupdate、ended，提交进度并重放同一结束事件，再退出、重新登录核对恢复位置。三个阶段均观察到媒体时钟抵达文件末尾。下表是同一用户、同一课节、同一资产本轮实测值。

| 时点 | 当前 reason | 票据恢复秒 | 视频实播至 | 服务端累计有效秒 | 历史完成状态 |
| --- | --- | ---: | ---: | ---: | --- |
| 未授予单课权益 | trial | 0 | 1 秒 | 1 | 否 |
| 人工授予单课权益 | purchased | 1 | 4 秒 | 4 | 是 |
| 撤销人工权益 | trial | 1 | 1 秒 | 4 | 是，沿用完整版已有记录 |

第三次先对先前的完整播放会话续签，得到 410 PLAYBACK_EXPIRED，再按最新目录取得独立试听对象。新请求和续签受当前权益约束；已有签名的剩余有效时间仍须计入撤权预期。

开发联调总计 86 项检查通过，含真实微信登录、STS/COS、FFmpeg、H5 首页、独立 IDE 播放和清理。首次完整流程遇到 IDE evaluate 调用失败，改成单次启动并轮询结果；第二次在第三段视频等待结束事件超时。最终版为每个播放会话分配独立临时视频文件，等组件移除后清理，重跑三段通过。文件为合成短视频，未测真实长音频、视频流式弱网和后台续播。正式课程页仍为本节开头的观测结果，未通过正式播放器或微信真机验收。

隔离云机镜像 `cxj-api:f8f9aa1` 已部署，JAR 与真实探针的 SHA-256 同为 `88456719d665af46b3be9ab0c08b71935568793da87ba46dd4712f79f05dd1d9`。本机 18080、SSH 隧道 18081 各验证就绪、匿名分类和课程列表、缺失课程详情与目录 404、匿名申请播放 401。云机仍为隔离配置，公网 HTTPS 与生产上线待验收。

复验用同事分支关闭 Mock 的 H5 构建、编译后的独立微信探针、独占的本机 cxj_test 和 17173/18082/9420 端口。按[本机运行说明](backend-development.md)设置 Node、微信 MCP 和 Chromium 路径，运行：

```sh
../.tools/cos-runtime/bin/python scripts/smoke_media_cos.py --run-real-cloud-upload --course-probe --mobile-project ../.tools/frontend-223b2ea/apps/mobile
```

项目路径换成实际前端 checkout 的 apps/mobile，H5 构建 API 基址为 http://127.0.0.1:18082/api/v1。结果与失败记录在本机忽略目录 server/target/course-flow-*，最新探针 JSON 包含 native_playback、fixture_cleanup。正式前端接入后，再从真实列表点击真实课程、选课节，在正式播放器上重复上述三种权益状态。
