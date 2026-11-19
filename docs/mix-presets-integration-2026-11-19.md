# 白噪音混音预设交接

混音页可以保存当前选中的音轨及各自音量，登录同一账号后从后端恢复。编辑发布的公共配方与用户自己的配方分开查询。前端可以复制公共配方的 tracks，改音量后保存为个人配方。接口前缀为 `/api/v1`。

## 接口

| 操作 | 请求 | 返回及行为 |
| --- | --- | --- |
| 查看公共配方 | `GET /mix-presets?page=1&pageSize=20` | 只列已发布且每条音轨仍公开可见的配方。会员音轨可以被列出，开始时再检查当前权益。后台预混成品尚未生成，backgroundAssetReady 为 false。 |
| 保存我的配方 | `POST /me/mix-presets`，用户 Bearer、UUID Idempotency-Key。请求体含 title、tracks | 返回 id、version、createdAt、updatedAt、title、tracks。新意图用新键，超时使用原键及原请求；同键变更内容返回 409 IDEMPOTENCY_CONFLICT。 |
| 恢复我的配方 | `GET /me/mix-presets` 与 `GET /me/mix-presets/{id}` | 分页读取或按 ID 恢复。每条轨道按保存时的顺序返回真实 noiseId 与 volume。撤审或权益变化后仍可恢复配置，开始练习会按当前状态重新校验。其他用户请求该 ID 返回 404。 |
| 更新及删除 | `PUT /me/mix-presets/{id}` 提交 title、完整 tracks、version；`DELETE /me/mix-presets/{id}` 提交 version | 更新返回新版本，旧版本写入返回 409 VERSION_CONFLICT。删除从本人列表隐藏，重复删除成功；历史练习保留原快照。个人接口无发布字段。 |
| Web 后台 | `/admin/content/mix-presets` 支持 GET 列表、POST 创建；`/{id}` 支持 GET、PUT、DELETE | 全局内容管理范围，按 content:read/write 授权。列表可按 q、status 筛选，创建传 title、tracks、status 和 UUID 幂等键，修改另传 version，归档传 version、reason。后台只管理公共配方。 |
| 开始及继续练习 | `POST /practice/sessions` 传 kind=noise、plannedSeconds、mixPresetId、tracks=[]。guided 另需 meditationId，可将预设用作环境音。GET 会话读取快照 | 返回解析后的 tracks、mixPresetId 和 mixPresetVersion。同一个创建请求重试返回原快照。如果前端展示了具体音轨并希望防止旧选择静默变更，可原样附 tracks；与当前配方不匹配返回 409 MIX_PRESET_CHANGED。按最新个人或公共配方开新会话，先检查每条音轨。 |

例如，保存请求的 tracks 可以是 `[{"noiseId":"<细雨的真实ID>","volume":0.35},{"noiseId":"<篝火的真实ID>","volume":0.7}]`。允许 1 至 5 条不同白噪音，音量范围 0 至 1，静音值 0 及原顺序均会保存。关闭的轨道请从数组移除。配方保存的是配置，音源始终从授权接口取得。用户令牌、私有存储凭据、媒体地址均不属于配方字段。

会话创建时锁定配方修订，按现有素材、用户权益逐轨绑定；任何一轨不可用则整次创建回滚。已经开始的会话按原音轨、音量和素材继续，领票、续签和上报仍复核实际音源及权益。前台对每轨调用 `POST /playbacks`，传 target.kind=noise、target.id、platform 和同一个 practiceSessionId。播放器按快照设音量，分别上报各自的播放事件，同时维护一条练习事件流。服务端对有效播放区间求并集，与活跃区间相交，扣除用户其他会话已入账的秒数。音量为 0 的白噪音轨道不提供入账证据。结束前先发各轨最后一条播放事件，日统计取 `GET /me/practice/stats`。后台预混、练习中实时改轨和结束铃仍待开发。完整的事件、续签和错误处理见[音频会话交接](practice-audio-integration-2026-11-19.md)。

## 前端文件

前端分支 `223b2ea`，下列文件以 `apps/mobile/src` 为起点。

| 文件 | 接入动作 |
| --- | --- |
| stores/mixer-preview.ts | 用真实 noises 目录 ID 替换 rain/fire 等本机 ID，滑块百分数在请求时除以 100、显示时乘以 100。本人配方从 /me/mix-presets 恢复。现存本机记录只有位置和演示 ID，未建立对应关系时请用户重新选择音源。 |
| pages/mixer/index.vue | “我的配方”接个人查询、新建和带版本更新。示例入口可列 /mix-presets。恢复后按数组顺序还原启用状态与音量，区分空列表、无权限和加载失败。定时设置继续从本次练习传 plannedSeconds；配方只保存标题、选轨和音量。 |
| services/http.ts、components/PlayerScene.vue、pages/practice/index.vue | 增加登录写请求及幂等重试。开始时使用服务端会话快照，对每轨创建播放器并绑定同一练习；以会话有效秒展示时长。403 提示会员权益，404 刷新音源，409 刷新配方或练习。 |
| pages/home/index.vue | 前端分支仍用“非免费”显示 VIP。purchase 显示“单课购买”，member 显示“会员”，member_or_purchase 显示“会员可学 · 可单购”。详见[课程交接](course-player-integration-2026-11-16.md)。 |
| Web 管理后台 | 同事分支目前只有 apps/mobile；后续编辑页按上表管理公共配方、选择已审核音轨、处理版本冲突，后台预混成品就绪前只提供前台试听。 |

## 复验

先运行仓库规定的 H2、独立 MySQL、Python、契约与 OpenAPI 检查。打包后设置既有 WECHAT_MCP_PYTHON 与 WECHAT_MCP_WRAPPER，用工作区 cos-runtime 的 Python 执行 `scripts/smoke_media_cos.py --loopback-mix`。该模式独占 cxj_test、18082 和独立 IDE 自动化项目，加载 FFmpeg、微信开发身份与测试库配置。对象只在临时回环存储，探针结束后释放。混音断言使用两个原生播放器各自的 onPlay、onTimeUpdate、onEnded、音量和事件时间，核对真实重叠与后台入账。证据位于 server/target/mix-preset-loopback/result.json。
