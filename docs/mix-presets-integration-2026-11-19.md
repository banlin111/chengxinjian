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

## 2026-11-19 实测记录

| 层级 | 本批证据与边界 |
| --- | --- |
| ① 代码 | 从后端 33457cd 接续，实现提交 f8170e93e4ca0168f56526376255054afce46c19。前端仍为 223b2ea4d30d18a9a6ee2655c8b526bbb65fad84。V15 增加配方及有序选轨两张表。个人配方私有，公共配方受编辑发布及审核状态控制；会话固定配方版本、音量和素材。 |
| ② 自动测试 | H2 与原生 MySQL 8.4 各 141 项，均无失败和跳过；Python 10 项、TypeScript 契约生成（229 个方法、164 个 schema）、OpenAPI 校验通过。新增覆盖双账号隔离、创建并发重试、版本冲突、删除后历史会话、静音轨、五轨顺序和与编辑并发启动、撤审、会员权限，以及用配方启动后多轨跨上海午夜去重。已核对实现提交的 [GitHub Actions](https://github.com/banlin111/chengxinjian/actions/runs/44462270069)，contracts、backend 和打包 HTTP 均成功。后续纯文档提交按当前路径规则不另触发 CI。 |
| ③ 真实第三方开发联调 | 独立微信 IDE 工程通过真实 wx.login/code2Session，保存后退出，重新登录同一账号从后端恢复。两段独立合成 MP3 由两个 InnerAudioContext 同时播放，音量 0.35、0.70；编辑原配方后，已建会话保留两轨。两个播放器实测重叠 3059 毫秒，各确认 3 秒，并发上报并各自重试后，会话入账 3 秒，日统计增加 3 秒、1 次完成。免费引导与临时会员白噪音回归亦通过。打包服务、真实 MySQL、FFmpeg 及临时回环对象存储共通过 29 项探针检查，测试授权和素材已清理。一次重跑在单轨引导的 ended 等待阶段超时，清理完成；保持机器唤醒后重新跑通全部检查。此次没有调用 COS、短信或购买套餐；较早的真实 COS 课程证据见[课程交接](course-player-integration-2026-11-16.md)。 |
| ④ 隔离云机及 SSH | 复用已租实例，V14 迁移前已备份并校验异机副本；部署镜像 cxj-api:f8170e9，云库 V15。容器与本机探针 JAR SHA-256 同为 51dc2334ccd3b91593415952b73b088df3a7a2cae173d58bab8b1968453df5e8。重新建立 18081 隧道后有 18 项 HTTP 断言通过，含公开空目录 200、匿名个人读写及后台访问 401、缺批准协议 503。应用仅映射云机回环地址，MySQL healthy 且无宿主端口。云端管理员、用户、配方及练习仍为零，未进行云端已登录的业务写入。迁移后备份已恢复到新演练库，55 张表校验和及 15 次迁移匹配，源库未变化，演练库已移除，异机副本已验证。本机 cxj_dev 只读复核仍为 V9。 |
| ⑤ 公网 HTTPS | 尚缺业务域名备案、DNS、证书、微信合法域名和对应配置。本次使用 SSH 回环隧道。 |
| ⑥ 正式前端及真机 | 正式 H5 构建在 Chromium 经隧道读取首页空态。实点“保存当前配方到本机”，移除音轨，再点“恢复本机配方”，还原了 60%、35%，随后打开的仍为助眠演示。业务请求只有 GET /api/v1/home。正式微信包在 IDE 打开首页及混音页，核对“我的配方”和“暂无音源”提示。正式页的服务器保存与播放、Web 管理操作和微信真机本次均未验证。 |
| ⑦ 上线验收 | 未完成。仍需正式客户端接入、真实授权素材、已核实额度的 COS、公网 HTTPS、批准协议和微信真机前后台、来电、弱网等验收。后台预混成品未生成。 |

本机证据见 server/target/mix-{h2,mysql,python,contract,openapi}.log、mix-preset-loopback/result.json、mix-timeout.json、mix-staging-{before,state,http,prebackup,postbackup,restore}.json、mix-h5-result.json、mix-formal-ide.json 和 mix-ci.json。原始日志及备份保留于 Git 外。
