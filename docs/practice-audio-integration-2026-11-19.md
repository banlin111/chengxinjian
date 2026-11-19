# 白噪音与引导冥想接口交接

适用于前端分支 `223b2ea`。下列路径均以 `/api/v1` 为前缀。素材审核、目录和练习共用同一份内容状态。练习记录、完成次数、打卡与奖励是不同的数据，前端应从相应接口读取。

## 目录和审核

| 使用方 | 操作及返回 |
| --- | --- |
| 冥想分类、详情 | `GET /meditations` 支持 page、pageSize、categoryId、teacherId、minSeconds、maxSeconds、memberOnly、sort。`duration_asc` 按时长排序，默认按创建时间倒序并以 ID 打破平局。暂无推荐编排，recommended 暂沿用此默认顺序。冥想无 difficulty 字段。`GET /meditations/{id}` 包含 summary、teacherId、durationSeconds、coverUrl、favorite、accessPolicy 和 access。 |
| 白噪音混音页 | `GET /noises?categoryId=…&page=1&pageSize=20` 返回真实音轨 ID、标题、封面、时长与权益。目录仅展示已发布、素材审核有效的内容。会员条目可见，播放需要当前有效会员。公开响应不含原文件地址。 |
| Web 内容管理 | `/admin/content/meditations`、`/admin/content/noises` 均支持 GET 分页、POST 创建，`/{id}` 支持 GET、PUT 全量更新、DELETE 归档。共同输入 title、categoryId、coverAssetId、assetId、accessPolicy（free/member）、status。冥想另传 summary 和可空 teacherId。更新附 version，归档传 version、reason，创建用 UUID Idempotency-Key。讲师按本人内容范围操作。 |
| 编辑审核 | 复用 `POST /uploads`、`POST /uploads/{id}/complete`、`GET /assets/{id}` 与 `PUT /admin/assets/{id}/review`。音频 purpose 分别为 meditation_media、noise_media。技术验证 ready 后才可送审，审核传 decision、version、reason。发布要求已审核封面和对应类型的已审核音频，且分类、关联讲师均已发布。撤审、归档或关联内容失效后，公开目录和新授权同步收回。审核音频的 publicUrl 为空。 |

## 会话与结算

1. 选择内容后调用 `POST /practice/sessions`。引导练习传 kind=guided、meditationId、plannedSeconds、tracks；白噪音练习传 kind=noise、plannedSeconds、tracks。每轨含 noiseId、volume（0 至 1）。白噪音需 1 至 5 条不同轨道，引导练习可带 0 至 5 条环境音。初始顺序、音量与具体素材版本在会话创建时绑定。恢复登录后 GET 会话可取回选择。前台混音的独立增删轨、预设、后台混流与结束铃仍待开发。
2. 每个音源独立 `POST /playbacks`，传 target（kind=meditation 或 noise、id）、platform 与上述 practiceSessionId。无绑定的单独播放可以领票和恢复位置，练习统计只接受已绑定会话的证据。票据仍只读一个对象，最长 120 秒，会员到期可更短。到期前 POST `/playbacks/{id}/renew`，届时已过期则重新领票，再按 resumeSeconds 恢复。新授权、续签和新播放事件都复核素材、授权及会话。已经下发的 COS URL 在剩余有效期内可能继续可读，已下载的字节无法追回。
3. 用真实播放器状态向 `POST /playbacks/{id}/events` 发送 eventId、sequence、kind、positionSeconds、playedDeltaSeconds、speed、occurredAt。同时向 `POST /practice/sessions/{id}/events` 发送自己独立的 eventId、sequence、action、activeDeltaSeconds、occurredAt。两路各自管理序号。新操作用新 UUID 键，网络重试保留原键和完整请求体。seek、foreground 不产生有效播放秒。倍速按媒体秒换算真实墙钟秒。
4. 后端对活跃练习区间与核验的播放区间取交集。引导练习由主引导音轨提供证据，白噪音由所选轨道提供证据。再按同一用户已有区间去重，按上海午夜拆分，并受计划时长上限约束。两路心跳先后顺序均可，后到的播放可能更新同一练习版本的 creditedSeconds；version 用于练习状态事件顺序。页面展示读取最新会话或本人统计，禁止累加 PlaybackAck.creditedSeconds、各音轨秒或重试快照。
5. 暂停先发播放进度再发 pause，保留同一会话 ID。暂停后晚到的有效证据仅能结算与已申报活跃时间相交的部分。恢复先发 resume。完成或放弃前先提交所有待发的播放事件，再发 finish/abandon，终态冻结。finish 申报正活跃秒、最后区间没有播放证据时返回 409 PRACTICE_PLAYBACK_PENDING，先补发播放后原样重试。如确实未播放，该段申报零。练习完成不自动打卡或发奖。
6. CONTENT_LOCKED（403）提示权益受限，NOT_FOUND（404）重新读取目录，PRACTICE_BINDING_CHANGED（409）刷新会话与素材，PLAYBACK_EXPIRED（410）重新申请票据。内容替换后需新建绑定当前素材的练习。历史有效秒保留。手机号冲突沿用原账号保护。

## 同事的文件与接入动作

以下相对路径从前端分支的 `apps/mobile/src` 起算。

| 文件 | 需要接入的行为 |
| --- | --- |
| services/meditation-preview.ts、pages/meditation/index.vue、pages/meditation-category/index.vue | 使用分类、冥想目录与真实 ID 替换静态心境和 demo 条目，分页、加载失败和空目录要可区分。首页 recommendedMeditations 暂无运营推荐。 |
| pages/meditation-detail/index.vue、components/PlayerScene.vue、pages/player/index.vue | 读取详情、当前 access，按上面的顺序建练习、领票并播放。权益提示和撤审依服务端结果；重试与换签保留播放进度。 |
| stores/mixer-preview.ts、pages/mixer/index.vue | 改用 noises 目录，提交实际选中的 ID 及音量，分别授权和上报；展示练习详情中的有效秒。现有演示开关及本机预设仍是前端状态。 |
| services/http.ts、pages/practice/index.vue | 补 Bearer、写请求、幂等键与刷新重试；恢复读取本人会话，历史与日统计走 /me/practice/sessions、/me/practice/stats。 |
| pages/home/index.vue | 首卡和课程卡目前统一给非免费课标 VIP。purchase 展示“单课购买”，member 展示“会员”，member_or_purchase 展示“会员可学 · 可单购”。播放按钮仍按 access 判断。详见[课程交接](course-player-integration-2026-11-16.md)。 |
| Web 管理后台 | 当前前端分支只有 apps/mobile。后续管理页接上表的上传、审核及发布，审核后的短效播放票据和私有后端配置均不得保存到前端项目。 |

## 重现开发联调

独占本机 cxj_test 与 18082、微信独立工程自动化端口。按[定时练习交接](timer-practice-integration-2026-11-19.md)设置 WECHAT_MCP_PYTHON 及 WECHAT_MCP_WRAPPER，先运行 H2、MySQL 与打包验证。使用工作区 cos-runtime 的 Python 执行 `scripts/smoke_media_cos.py --loopback-audio`。只载入微信开发身份、测试库和 FFmpeg 路径。本机临时存储适配器单独编译，用限时随机地址传送合成文件；退出进程即释放对象。生产 JAR 与镜像不包含适配器。

如明确核实 COS 可抵扣额度，可独立执行 `scripts/smoke_media_cos.py --run-real-cloud-upload --audio-probe`。使用同一套业务及 IDE 断言，切换为真实 STS/COS。每种模式的清理状态和结果分开写入 server/target，报告需标识所用存储。
