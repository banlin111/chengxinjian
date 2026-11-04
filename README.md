# 澄心涧：微信小程序与管理后台协作 README

版本：0.1.0（开发契约草案）｜整理日期：2026-11-04｜两人开发

**本期范围：实现《澄心涧.pdf》思维导图的全部业务功能，先交付微信小程序及 Web 管理后台；后端采用 Java / Spring Boot，三份原型的配色都保留为可切换主题。APP 排在后续阶段。**

本文定义目标方案与接口，不代表应用已开发完成。尚无甲方答案的业务数字均需确认，原型中的价格、用户量、奖励及文案不能作为正式配置。本文、接口类型和 OpenAPI 均为同一版本的草案，双方确认后冻结。

## 1. 阅读顺序与文件

1. 甲方看第 2、3 节：准备资源并确认业务边界。
2. 两位开发者看第 4～9 节：技术方案、分工、开发约定、关键流程与验收。
3. 第 11 节开始是完整接口附录：每个接口的方法、地址、查询参数、请求体、返回类型，以及全部字段定义。

配套文件：`contracts/models.ts` 是字段定义，`contracts/api.ts` 是接口定义；`openapi.json` 是可导入 Swagger Editor / Apifox 的接口契约；`.env.example` 是后端配置清单。这里的 TypeScript 是跨端 JSON 契约的表达方式，**后端实现仍是 Java**，按其字段建立 Java DTO 即可。

## 2. 向甲方索取的资源清单

### 2.1 开工前就要拿到

| 资源 | 甲方提供或完成 | 开发者需要的权限/产物 | 何时需要 |
| --- | --- | --- | --- |
| 主体资料 | 营业执照、主体名称、法人/管理员、业务联系人、注册邮箱 | 用于主体认证、备案、服务签约；敏感资料直接提交官方平台 | 立即 |
| 微信小程序 | 甲方主体注册，确认认证、备案、服务类目 | AppID；将两人加入项目开发者；添加测试体验成员 | 立即 |
| 管理员配合 | 指定能扫码确认的甲方人员 | 认证、权限开通、支付签约、审核发布时配合 | 全程 |
| 云账号 | 腾讯云或阿里云的甲方实名账号与费用预算 | 项目子账号；计算、存储、日志、数据库等必要权限 | 开发期 |
| 域名 | 甲方名下的域名、备案信息 | DNS 修改授权、证书配置权限；确认 API/CDN/直播子域名 | 联调前 |
| 素材与版权 | Logo、图标、字体、图片、课程、音视频、白噪音、提示音 | 可使用的原文件、商业使用授权、内容目录 | 立即 |
| 讲师资料 | 姓名/艺名、头像、介绍、资质及合作授权 | 讲师与课程、直播的对应关系 | 内容录入前 |
| 规则及验收 | 一位能拍板的人、需求清单、验收标准、工期、维护范围 | 书面确认记录；变更清单 | 立即 |

账号、域名、支付账户及生产资源应归甲方。开发者使用成员权限、子账号和独立密钥，不要用开发者个人主体长期代持，也不需要索要甲方管理员微信密码。AppSecret、支付密钥不得通过 README、Git 或前端包交接。

### 2.2 联调及上线前必须具备

| 能力 | 甲方需开通/提供 | 实施说明 |
| --- | --- | --- |
| 小程序虚拟支付 | 虚拟支付签约、结算账户、支付管理员、OfferID、商品配置；后端安全获取沙箱/生产 AppKey | 课程和会员按当前平台规则接虚拟支付，不能拿普通实物支付商户号直接替代 |
| 普通微信支付 | 仅当确认增加实物收费、付邮费等场景时开通；商户号与小程序关联 | 本期奖励建议免费包邮，不因此默认加一套实物收费商城 |
| 短信 | 短信服务、签名、验证码模板、预算及审核 | 保留思维导图的手机号注册、密码登录、换绑及找回密码 |
| 手机号授权 | 小程序对应手机号能力开通状态、费用预算 | 微信一键手机号与短信是不同能力；登录 code 与手机号 code 也不同 |
| 云直播/回放 | 直播服务、播放与推流域名、回放存储、流量预算、类目权限 | 云服务开通和微信小程序直播组件/插件权限是两回事 |
| 对象存储/CDN | 存储桶、访问策略、媒资域名、转码、备份预算 | 付费音视频、用户录制文件保持私有；通过短效签名 URL 访问 |
| 物流 | 发货人员、承运商、快递查询服务及其账户/API、发货/退货地址 | 只有运单号不能保证有完整物流轨迹，需要查询服务或承运商接口 |
| 内容审核 | 审核负责人、审核时限、第三方内容安全服务预算 | 评论、上传录制内容、直播提问均需审核及投诉处理机制 |
| 订阅消息 | 可用模板及后台配置 | 用户主动授权后才能发送，不能保证每天无限推送 |
| 隐私与协议 | 用户协议、隐私政策、会员协议、退款政策、注销规则、客服渠道 | 开发者按实际采集字段配置微信隐私指引，政策由甲方确认 |
| 上线素材 | 名称、简称、头像、简介、客服、真实备案信息、审核说明 | 不能把原型里的虚构备案号、销量和讲师资质直接发布 |

### 2.3 服务器采购建议（估算起点，非承载承诺）

- 开发环境两人本机运行 Java、MySQL、Redis；公共测试环境建议独立于生产，不能拿生产数据直接测试。
- 小规模试运营可从 1 台 4 核 8 GB 应用服务器、托管 MySQL、Redis、对象存储和 CDN 起步。若预算只能单机部署数据库，也必须独立备份并说明单点故障风险。
- 正式承载配置由压测决定；若要求不中断服务，应配置多实例应用、负载均衡和数据库高可用。
- 音视频通过云直播/点播/CDN 分发，不让 Java 应用服务器承担全部视频转发或转码。
- 询问预计注册用户、日活、峰值并发、同时直播人数、每月场次和时长、素材总量、每月上传量、备份保留天数。
- 视频粗算：2 Mbps × 100 人 × 1 小时约 90 GB 下行流量，未含协议开销。直播成本往往比业务 API 更值得先核预算。
- 要甲方明确服务器、数据库、存储、CDN、直播、短信、物流、认证及支付手续费由谁支付；购买前核对当日官方报价，不把促销价写成长期成本。

### 2.4 后续 APP 阶段再准备

Apple Developer、App Store Connect 权限、Android 应用市场企业账号、应用签名证书、APP 备案、微信开放平台移动应用、支付宝商家及 APP 支付接入信息；iOS 数字内容支付需按届时 App Store 规则另行适配。小程序 AppID、DCloud 应用标识、微信开放平台移动应用 AppID、iOS Bundle ID 不能混为一谈。APP 的支付宝、推送、离线播放和原生音频能力另设平台适配验收，本期先保留服务端业务模型。

## 3. 需求范围与甲方待确认项

### 3.1 来源及冲突处理

- 思维导图是本期功能范围依据，用户已明确要求全部做；原文技术栈写的是“建议”：Flutter / React Native、Spring Boot / Node.js、MySQL + Redis、云直播、对象存储。
- 原型 V1 有 30 屏；V2 两版各 22 屏，提取页面内容对比显示主要差别为颜色与风格描述。
- 三套配色都实现；功能整合为一套。不能把三份原型做成三个独立应用。
- 原型新增商城零售、购物车、积分货币、优惠券、二级分销、佣金提现、秒杀、礼物/连麦、离线下载、自动续费等，不因出现在图上就自动计入思维导图范围。**需甲方追加确认与估时**。
- 思维导图已有的实物奖品、课程奖励、上传录制奖励及快递轨迹必须实现；无需为了奖品履约先做完整电商商城。
- 未确定规则在测试环境可用明确标记的演示值，生产不得默认套用原型数字。

### 3.2 必须确认的业务问题

| 问题 | 已发现的冲突/缺失 | 本契约处理 |
| --- | --- | --- |
| 三套主题 | V1 浅色、V2 松石夜色、V2 墨绿 | 全部实现；默认墨绿是建议，甲方可更改 |
| 导航 | 原型不同页底栏不一致 | 建议统一：首页、课程、练习、直播、我的；冥想在练习中；不为未确认商城留正式主入口 |
| 会员定价 | 月卡 25/28 元；终身会员与连续包年混用 | 本期月/季/年/终身四档，一次购买；自动续费另确认 |
| 会员时长 | 日数还是自然月未给出 | 每个套餐配置 durationDays；终身 expiresAt=null 且 lifetime=true；不凭空写死 30/90/365 |
| 课程权益 | 会员免费与单课买断并存 | 支持 free/member/purchase/member_or_purchase；首节/限时试听逐节设置 |
| 永久权益 | “一次解锁终身”未定义停服/下架 | 甲方写清服务存续、内容下架和退款安排；技术上使用无到期时间授权 |
| 打卡 | V1 10 分钟、V2 21 分钟；连续天数不一致 | 后端配置阈值；上海时区自然日；不自动补签；跨日、断签按第 8 节建议确认 |
| 奖励 | 7/21/30/49/100 天及积分数字混杂 | 以 reward-rules 正式配置为准；本期仅实物和课程奖励，不默认增加积分钱包 |
| 录制 | 思维导图为冥想过程，原型写心得 | 录音/视频形式、时长上限、审核、隐私范围需甲方确定；接口两种媒体都能接入 |
| 退款 | 是否已学习/已使用会员可退，未规定 | 后端按批准政策校验；首版接口全额退款；部分退款如需另扩展；Apple 订单单独处理 |
| 奖品履约 | 库存、发货时限、地区、运费未知 | 建议包邮且显式确认地址后领取；禁止静默使用旧默认地址发货 |
| 回放 | 是否录制、生成、保留、谁能看 | 每场 replayEnabled 控制；生成后按该场权限；章节首版人工配置 |
| 背景混音 | 五轨叠加与后台播放容易被当作同时承诺 | 先验收前台混音与单轨后台；后台预混音需明确开发与性能方案 |
| 账号注销 | 原型“7 天冷静期”“退出后保留30天”并非已批准政策 | 由甲方确认保留与注销政策；登出不删除学习数据；法定交易保留记录脱敏处理 |
| 内容性质 | 原型有“绿度母”“回向”“治好了失眠”等内容 | 甲方明确一般健康放松还是实际宗教/医疗服务，并核对资质及文案；不能靠换标题规避真实类目 |

二级分销并不因“只有两级”自动得到可上线结论；若后续追加，需要另确认推广关系、佣金计提、退款冲回、提现权限和相应业务审核。本期不开发该财务链路。

## 4. 开发工具、模板与技术方案

### 4.1 HBuilderX 到底是什么

微信官方平台是[微信公众平台](https://mp.weixin.qq.com/)，用于注册、成员、类目、备案、审核和发布；官方调试上传工具是[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。

**HBuilderX 是 DCloud 的 IDE，uni-app 是跨端开发框架，不是微信官方的业务模板。**可以用 HBuilderX 创建 uni-app 项目，也可以用官方 CLI 模板配合其他编辑器；最终编译为微信小程序工程，使用微信开发者工具调试、预览、上传。

有固定工程结构、页面生命周期、平台 API 和脚手架，但没有能自动满足本项目全部功能的“微信固定模板”。uni-ui 等组件能减少基础 UI 工作，不负责会员、直播、支付或奖励业务。原型 HTML/Tailwind 页面也不能原样作为原生小程序发布。

### 4.2 本项目建议技术栈

| 部分 | 选择 | 理由与边界 |
| --- | --- | --- |
| 用户端 | uni-app + Vue 3 + TypeScript + Pinia | 先微信小程序，后续复用部分 APP 页面与业务；平台差异仍需适配 |
| 小程序工具 | HBuilderX + 微信开发者工具 | HBuilderX 编译，微信工具调试及上传；CLI 也可使用 |
| 管理后台 | Vue 3 + TypeScript + Vite + Element Plus | 普通 Web 管理后台，单独构建和部署 |
| 后端 | Java 21 + Spring Boot + Spring Security + Bean Validation | 用户已确认 Java；建议先用团队熟悉且兼容的 Spring Boot 3.5 线，开工锁定补丁版；不无条件追最新大版本 |
| 数据 | MySQL + Redis；Flyway 数据库迁移 | MySQL 为账务和权益事实来源；Redis 处理缓存、限流、短期会话 |
| 媒体 | 腾讯云或阿里云对象存储、CDN、直播/点播 | 选择一家主供应商，封装接口，避免自研流媒体服务器 |
| 架构 | 模块化单体 + 后台任务 + 事务 outbox | 两人开发优先减少服务运维数量，不从微服务/Kubernetes 起步 |
| 契约 | 本 README + TypeScript DTO + OpenAPI 3.0 | 两人共用一份接口；Java DTO 由对应 JSON schema 落地 |

[Spring Boot 官方系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)与[DCloud 快速上手](https://uniapp.dcloud.net.cn/quickstart.html)已查阅。确切版本和 lockfile 在建立实际工程时提交；本目录只含文档及契约，并没有伪装成已可运行的小程序。

### 4.3 创建与运行小程序

可选 HBuilderX“新建项目 → uni-app → Vue 3”，或使用官方 `dcloudio/uni-preset-vue` 的 `vite-ts` 分支。确认所有 `@dcloudio/*` 编译包版本匹配，提交锁文件。

```powershell
# 以下是正式开发工程建立时的步骤，不表示本目录已有 apps/mobile
git clone --branch vite-ts --single-branch https://github.com/dcloudio/uni-preset-vue.git apps/mobile
cd apps/mobile
npm install
npm run dev:mp-weixin
# 将 dist/dev/mp-weixin 导入微信开发者工具
# 在 manifest.json 的 mp-weixin.appid 配置甲方小程序 AppID
npm run build:mp-weixin
# 上传构建产物 dist/build/mp-weixin，完成体验版、提审、发布
```

HBuilderX 可视化创建工程的默认编译目录通常是 `unpackage/dist/dev/mp-weixin`，不要和 CLI 工程的 `dist/dev/mp-weixin` 混用。开启微信开发者工具服务端口便于 IDE 拉起。真机验收必须关闭“跳过域名校验”，不能仅凭模拟器正常判断上线可用。

## 5. 两人任务划分

以下用 A=前端、B=后端，具体姓名由你们填写。**后台页面也属于前端开发；后端不是仅写几个接口，还包括任务、部署和第三方回调。**

| 模块 | A：前端交付 | B：后端交付 | 合并验收 |
| --- | --- | --- | --- |
| 工程基础 | 小程序/后台工程、路由、请求封装、错误态、三主题 | Spring Boot、迁移、鉴权、日志、环境隔离、OpenAPI | 共用契约版本；体验版连测试 API |
| 账号 | 微信与手机号登录、验证码、密码、换绑、协议、注销页 | code 换身份、短信限流、密码哈希、会话、解绑/换绑、注销流程 | 拒绝授权仍可浏览；重复手机号不覆盖旧账号 |
| 首页搜索 | 问候、继续播放、推荐、新课、通知、统一搜索 | 内容聚合、搜索、推荐配置、已读状态 | 课程/冥想/讲师/直播均可检索 |
| 课程学习 | 分类、筛选、目录、试听、播放器、倍速、收藏、评论、分享 | 课程/课节、权限、签名地址、进度、评论审核、收藏 | 未购买拿不到完整媒资；下次恢复进度 |
| 静心练习 | 引导、定时器、提示音、前台混音、独立音量 | 会话、时长计算、素材、混音配置、统计 | 前后台切换不重复计时；断网能恢复 |
| 直播 | 列表、预约、直播间、点赞提问、讲师回复、回放 | 云直播、鉴权、WebSocket、禁言、录制回调、回放 | 开播/强制下线/关闭评论/回放权限一致 |
| 会员支付 | 套餐、单课购买、支付结果、订单、退款申请 | 报价、订单、虚拟支付、回调、查询补偿、权益、退款、对账 | 重复回调只发一次权益 |
| 奖励物流 | 打卡、录制上传、审核状态、领奖、地址、物流轨迹 | 打卡门槛、奖品库存、审核、唯一领取、课程发放、物流 | 一次资格不能领两份；实物与课程分流 |
| 个人中心 | 学习分类、收藏、记录、统计、设置、安全 | 查询聚合、偏好保存、数据保留策略 | 退出再登录记录存在 |
| 后台用户/会员 | 查询、封禁、等级、学习/直播记录、赠送/延期页面 | 用户管理、权限、赠送审计、到期任务、会员统计 | 有权限才能改，赠送有来源可追踪 |
| 后台内容 | 课程/分类/素材/讲师/推荐/公告维护页面 | CRUD、上下架、转码状态、引用检查 | 已购内容不因物理删除而丢失 |
| 后台直播/审核 | 排期、开关、推流凭据、禁言、回放、评论/举报审核 | 供应商集成、回调幂等、审核、强制断流 | 讲师仅能操作自己内容 |
| 后台财务/数据 | 订单、退款、财务导出、日活/留存图表 | 退款状态机、账单、导出、统计口径、任务 | 报表与订单支付流水可核对 |
| 后台权限 | 角色/账号/模块开关/范围管理页面 | RBAC、数据范围、审计、最后管理员保护 | 隐藏菜单之外，API 本身也拦截 |
| 发布交接 | 真机测试、审核截图、操作演示 | 域名证书、部署、备份恢复、监控、密钥交接 | 共同走完整验收单 |

## 6. 三套主题与平台适配

主题枚举固定为 `paper`（浅色宣纸）、`turquoise`（松石夜色）、`forest`（墨绿）。

| Token | paper | turquoise | forest |
| --- | --- | --- | --- |
| background | #F7F5EF | #0F1D1F | #0F1E18 |
| surface | #FFFFFF | #16302F | #16302A |
| primary | #0E2A26 | #2C8C82 | #2F6B52 |
| accent | #6F8F7E | #3FB3A3 | #57A184 |
| gold | #C9A25A | #E9C08A | #C9A063 |
| text（建议补齐） | #0E2A26 | #F7F5EF | #F7F5EF |
| textMuted（建议补齐） | #65736B | #A9C3BE | #ADC2B5 |

前三份原型中的主色已采集；缺失的语义色由前端补齐后让甲方确认。采用共享 theme tokens，页面不得散落硬编码颜色。切换主题同步更新导航栏、TabBar、播放器、弹窗、骨架屏、图标和图表，保留足够文字对比度。用户选择写入 settings，未登录先存本地，登录后按服务端设置同步。

同一业务仅一份状态与 API。`platform/wechat` 封装登录、手机号、虚拟支付、分享、订阅消息、录音、背景音频；未来 APP 新增适配器。微信胶囊与安全区通过平台尺寸获取，不固定使用原型 375×812 像素。

## 7. 保证前后端可以合并的约定

### 7.1 目录与职责

```text
project/
  apps/mobile/          uni-app 用户端，A 负责
  apps/admin/           Vue 管理后台，A 负责
  server/               Spring Boot，B 负责
  contracts/            双方共同评审的 API 和 DTO
  openapi.json          从契约生成，禁止手工改生成文件
  docs/                 决策、验收、部署说明
  infra/                部署与环境模板，B 负责
  README.md
```

当前交付目录尚未包含 apps/server 的实现。开始实施时建立单一 Git 仓库，功能分支开发，PR 合入 `main`；禁止各做一个完整压缩包到最后再拼。A 不直连 MySQL，B 不让前端猜数据库列名。每完成一个纵向功能就联调，不等全部页面做完。

### 7.2 HTTP 与数据格式

- API 前缀固定 `/api/v1`，测试/生产使用不同域名；OpenAPI 默认域名是示例 `.example`，需替换。
- JSON UTF-8；字段统一 camelCase；ID 全部字符串，避免 Java Long 超过 JS 安全整数；金额为整数分，币种 CNY；时长为秒。
- 时间使用带时区 ISO-8601；后端存 UTC，业务日归属 `Asia/Shanghai`；纯日期 `YYYY-MM-DD`，月份 `YYYY-MM`。
- 分页 `page=1` 起、`pageSize=20` 默认，范围 1～100，返回 `{items,page,pageSize,total}`；空列表是 `[]`，不是 null。
- `?` 表示可省略；`null` 是明确无值。请求中未知字段拒绝；PATCH 只改传入字段，空 PATCH 拒绝；PUT 完整替换业务可写字段。
- `Authorization: Bearer <accessToken>`；用户与管理员 token 分离 audience。客户端只持自己的 token，任何密钥不返回客户端。
- 短 access token 建议 2 小时、refresh 30 天；刷新 token 轮换并撤销旧值；封禁/注销/改密按策略撤销会话。具体 TTL 以返回值为准。
- 列表公共字段可匿名看；响应中的 `access`、favorite、progress 为登录后的个性化信息，不能用公共 CDN 缓存整份个性化响应。
- GET 不产生发奖/开通/购买等写入。POST 写操作带 `Idempotency-Key`（UUID）；PUT/DELETE 本身幂等。支付、退款、领奖的业务唯一约束长期保存，不能仅依赖 Redis 过期键。
- 同一用户/接口/幂等键，参数相同返回原结果，参数不同返回 409 `IDEMPOTENCY_CONFLICT`；超时重试保持同一键。新支付尝试经后端确认旧尝试终态后才能生成新支付单号。
- 乐观锁 `version` 由服务端加一；版本不符 409 `VERSION_CONFLICT`；管理员编辑先 GET 当前版本。

成功统一 HTTP 200；异步任务返回 HTTP 200 + Job，前端轮询，不把任务已创建当成已完成。示例：

```json
{"code":"OK","message":"success","data":{"success":true},"requestId":"req_demo_001","serverTime":"2026-11-04T21:00:00+08:00"}
```

失败使用真实 HTTP 状态，不把所有错误包成 200。格式：

```json
{"code":"VALIDATION_FAILED","message":"请求参数不正确","data":null,"requestId":"req_demo_002","serverTime":"2026-11-04T21:00:00+08:00","details":[{"field":"plannedSeconds","reason":"超出允许范围"}]}
```

| HTTP | 业务码示例 | 前端处理 |
| --- | --- | --- |
| 400 | VALIDATION_FAILED / SMS_INVALID / WX_CODE_INVALID | 对应字段提示，不盲目重试 |
| 401 | UNAUTHENTICATED / TOKEN_EXPIRED / WX_SESSION_EXPIRED | 合并并发刷新；微信 session 过期重新取得 code；重试一次 |
| 403 | FORBIDDEN / CONTENT_LOCKED / ACCOUNT_BANNED / FEATURE_DISABLED | 显示无权限或解锁入口，不显示底层密钥或异常栈 |
| 404 | NOT_FOUND | 展示下架/不存在；跨用户私有资源也可返回404 |
| 409 | VERSION_CONFLICT / ORDER_STATE_CONFLICT / ALREADY_CLAIMED / PHONE_ALREADY_BOUND | 刷新状态；避免重复提交 |
| 410 | QUOTE_EXPIRED / PLAYBACK_EXPIRED | 重新报价或重新鉴权取播放地址 |
| 422 | REWARD_NOT_ELIGIBLE / STOCK_EMPTY / PAYMENT_UNAVAILABLE / DELETION_BLOCKED | 说明未满足条件，不伪造成功 |
| 429 | RATE_LIMITED | 尊重 Retry-After，按钮冷却 |
| 500/503 | INTERNAL_ERROR / PROVIDER_UNAVAILABLE | 展示 requestId，允许查询现有结果后再重试 |

### 7.3 输入校验和权限

- 手机号首版中国大陆 11 位；验证码 6 位、建议 5 分钟有效、60 秒重发；验证码用途、手机、会话绑定，错误次数和 IP/账号限流。captchaTicket 来自选定验证码服务，服务端验证。
- 密码建议 12～64 字符，支持密码管理器，使用安全哈希；API 不回传密码或哈希。昵称 1～32 字符、评论 1～1000、直播消息 1～300，清洗富文本与危险链接。
- 音量 0～1、最多 5 轨且 noiseId 不重复；倍速允许 0.5/0.75/1/1.25/1.5/2，客户端不支持的档位禁用。
- 头像/封面允许 JPEG/PNG/WebP，建议最大10MB；录制格式/时长/字节上限由 moderation 配置明确；服务端检查真实类型、大小和转码结果，不信客户端 MIME。
- 用户只能读写自己的地址、订单、退款、学习、录制、资产和奖励；管理员查看录制及完整收件地址另需权限并审计。
- 普通用户、会员、讲师是不同维度：会员来自有效权益，不是前端自报 role；管理员登录与用户登录分开。讲师后台使用 own_content 范围，只能维护自己的内容、回复自己课程/直播，不可看全站财务。
- 后台权限按 `模块:read/write/review/export` 分配；例 `content:write`、`live:write`、`moderation:review`、`finance:read`、`finance:refund`、`fulfillment:write`、`users:write`、`rbac:write`。role 持久化必须校验 permission 字典，不能接收任意越权字符串。
- 直播推流凭据仅拥有直播管理权限且数据范围匹配的人可读取；用户端只拿播放地址。任何用户不能通过传 userId 切换身份。

## 8. 关键流程与服务端约束

### 8.1 登录与账号合并

微信登录：`wx.login → POST /auth/wechat/login → 后端 code2Session → 自有 token`。OpenID 按 AppID 区分；UnionID 不保证存在；业务主键用 userId。手机号授权单独调用绑定接口，不能把手机号授权 code 交给登录换身份接口。

手机号注册保留“验证码 → 设置密码 → 同意协议”。短信登录允许创建用户，但必须同意当前协议；小程序支付前账号必须绑定本 AppID 下的微信身份。手机号已属于另一账号返回 `PHONE_ALREADY_BOUND`，不得自动覆盖旧账号或把权益按手机号随意合并。若要合并，另做双身份验证与交易权益迁移流程，首版由客服人工核验处理。

改密、换手机号、注销需要短效 reauthToken，绑定 userId、purpose、TTL 并一次消费。更换手机号先验证旧身份再验证新手机号。未绑定手机号的微信账号注销验证方案需同时支持微信近期重新登录，不能把无手机号用户永久挡在注销外；该扩展见接口附录补充。

### 8.2 内容与播放

分类、课程、课节分离；列表及目录不带永久音频 URL。`POST /playbacks` 验证免费/试听/会员/单课权益、上下架及账号状态，返回短效签名 URL。试听使用裁切资源或等效服务端限制，不能只在前端数秒后暂停完整音频。

每 15～30 秒、暂停、退出、播放结束上报事件；eventId 去重、sequence 单调递增，拖动进度不能增加已学时长。完成率建议达到90%后标记，最终阈值需确认。后台更新时长采用已验证媒体时长；不信前端自报总时长。

小程序 `BackgroundAudioManager` 全局唯一，配置 `requiredBackgroundModes: ['audio']` 并完成审核；后台不能按前台方式随意调用控制 API。前台多音轨和后台单轨分别真机验证。建议后台混音使用有限预混配方输出单条音轨；任意五轨实时后台混音不属于未经验证就能承诺的能力。[微信背景音频说明](https://developers.weixin.qq.com/miniprogram/dev/api/media/background-audio/wx.getBackgroundAudioManager.html)

### 8.3 练习、打卡与录制奖励

会话状态：`active ↔ paused → completed / abandoned`，终态不再新增时长。同一用户多个设备重叠练习不重复累计。guided 会话把播放进度和练习计时关联，不能各加一次。

服务端按开始/结束时间、暂停记录、心跳序号与合理增量计算 creditedSeconds。后台网络可能暂停，返回前台可补传事件并做时间上限核验；单纯服务端心跳不能证明实际冥想，客户端自报也不能直接兑换奖品。对后台间隔使用可验证播放进度、墙钟时长上限和异常规则；高价值奖励要求审核。

按北京时间跨午夜拆分时长；`POST /checkins` 检查当天有效练习达到配置阈值。相同 userId+日期唯一；重复点击返回已有打卡，不发第二份奖。连续打卡按自然日计算，漏签重新计数，补签若需要另确认。

录制流程：申请上传凭据 → 直传私有桶 → complete 服务端检查 → processing/ready → 提交关联练习 → pending 审核 → approved/rejected → 达标后领取。审核通过不等于上传接口成功；驳回必须有原因。录制默认仅本人和授权审核人员可见，不自动公开到社区。

奖品领取对 userId+ruleId+资格批次做唯一约束，库存使用数据库原子扣减；重复回调/审核不重复发放。课程奖生成 entitlement；实物奖创建零元 reward order，确认地址并冻结地址快照后发货。reward-claim 不使用普通购买支付。收件地址修改不改变已发货订单的历史地址。

### 8.4 会员、订单、虚拟支付与退款

`报价 → 创建订单 → 创建支付尝试 → wx.requestVirtualPayment → 服务端通知/主动查询确认 → 事务发放权益 → 查询订单展示成功`。前端 success 只能提示支付已提交，不能据此开会员。

截至核查日，微信官方文档要求数字内容购买接虚拟支付，覆盖 Android/iOS 等；iOS 有独立开通和客户端条件。当前文档列出 iOS15+、微信8.0.68+、大陆 App Store 账号等要求，调用前做能力检测。具体费率按类目及后台生效规则确认，**不要把普通实物微信支付费率套到课程价格上**。[产品说明](https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment.html)、[技术服务费](https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment/devplan.html)

虚拟支付 `signData` 是后端签好的原始 JSON 字符串，前端原样传递，不重新序列化；`paySig` 和用户态 `signature` 均由服务端生成。AppKey、session_key 不下发客户端。wxCode 换 session_key 时必须验证换出的 OpenID 属于当前账号。道具模式使用官方 `short_series_goods`，不要因命名含 series 就误认为只能用于短剧；商品是否获准以甲方开通类目为准。

报价与下单校验商品、价格版本、平台可售能力；默认报价5分钟、待支付30分钟是实现建议。权益以服务器配置发放，客户端不能传 amount/paid/VIP。价格变动使旧报价失效。支付单业务号满足供应商长度及字符限制，单个订单可保留多次尝试，但只允许一次成功履约；关单与迟到支付通过查询/补偿处理，出现额外实收进入退款及对账队列。

订单：`pending_payment → paid → fulfilling → completed`；未付款可 cancelled/closed；退款状态独立记录。购买会员从 `max(now, 有效到期时间)` 顺延，终身会员无到期日；已购课程重复购买拒绝或返回已有权益。每笔来源独立保留 entitlement/grant，不直接覆盖用户一个 expiresAt 而丢失来源。

退款：`requested → approved → processing → succeeded`，可 rejected/failed；首版全额退款，重复申请返回同一处理中记录。**Apple 路由由用户通过 Apple 对应渠道申请，商户不能假设能主动按普通微信单退款**；API 返回 route/instructions 并进入 external_pending，后端处理退款问询和终态通知。退款只撤销该单产生的权益，不抹掉其他购买/赠送来源。会员叠加后的退订影响需要按来源账本重算并留审计。[微信虚拟支付退款说明](https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment.html)

支付通知与主动查询都必须校验签名/解密、AppID、支付业务号、用户、环境、商品、实付金额；扣款记录、订单变更、权益发放与 outbox 在一个数据库事务提交。定时补查 pending 订单、补发失败权益、每日对账。支付成功但未履约要可恢复，不能让用户再次付款解决。

### 8.5 直播与回放

直播状态：`scheduled → live → ended`，可 cancelled/forced_offline。管理员“开始”表示允许开播，实际有流状态以云事件和探测确认；强制下线同时阻断新播放鉴权、关闭会话、调用供应商断流能力。

join 同时校验账号、会员、直播状态，返回短效播放 URL、一次性 WebSocket ticket。票据不放日志；连接后首帧 auth，校验 ticket 与 liveId 和用户绑定，5秒未鉴权断开。30秒心跳，90秒无心跳下线，重连重新 join；传 lastEventId 恢复，超出保留窗口发送 resync_required 后重取列表。消息通过 REST 提交、WebSocket 推送；clientMessageId 去重。禁言与关闭评论由后端拦截，讲师回复验证归属。对已连接者的封禁/会员到期发送 access.revoked，并限制播放签名有效期。

云直播资质需核对对应类目允许的原生组件/插件方案，不能把能播放普通 MP4 作为实时直播验收。[腾讯云小程序直播方案](https://cloud.tencent.cn/document/product/1078/37707)

每场 replayEnabled 决定是否录制和发布；云录制回调生成 processing 回放，转码和审核完成后 ready。关闭回放时停止录制/阻止公开，已产生资源按保留策略处理。转课程复制媒资引用与权限配置，禁止把原会员回放意外变成免费。自动生成录像不代表自动生成准确章节，章节首版后台维护。

### 8.6 后台、统计与任务

课程/分类采用软删除或归档，被课节、已购权益、订单引用时拒绝硬删除。会员免费不使用0元伪支付订单，播放直接按会员权益检查；手动赠送/延期均记录操作者、原因与来源。

统计口径：DAU=当天有有效登录访问的去重 userId；新用户=当天首次注册；冥想时长=去重后的 creditedSeconds；播放量=有效播放会话（建议至少30秒，短素材按完成计算）；直播观看人数=场次内去重 userId；留存=注册 cohort 在第1/7/30天活跃人数/该 cohort 人数，未到观察日返回null。财务区分实收、退款、供应商手续费、净收和未结算，不能把支付流水直接叫利润。

后台任务必须包含：短信过期清理、会员到期与提醒、直播预约提醒、订单关单/支付补查、权益补发、退款重试、账单对账、回放处理、物流同步、上传孤儿文件清理、注销执行、统计聚合、备份及恢复演练。任务失败进入可查询日志/告警，手动重试仍保持幂等。

### 8.7 第三方回调（不是前端接口）

| 地址 | 来源与用途 | 认证、响应约定 |
| --- | --- | --- |
| `GET /callbacks/wechat/messages` | 微信配置服务器校验 | 按微信签名校验后返回 echostr；不使用业务 Envelope |
| `POST /callbacks/wechat/messages` | 虚拟支付发货、退款、退款问询等官方事件 | 验证消息签名并按安全模式解密；保持供应商原始 XML/JSON；成功按官方 ErrCode/ErrMsg 响应 |
| `POST /callbacks/media/{provider}` | 直播开关流、录制完成、转码事件 | provider 只允许已配置腾讯云/阿里云；按供应商协议验签、防重放、eventId 去重 |
| `POST /callbacks/logistics/{provider}` | 物流订阅状态 | 验签、运单归属核验、事件去重、按事件发生时间排序 |

上述路径不带 `/api/v1`，配置为 HTTPS 公网回调；前端不可调用。供应商外部报文严格采用所选产品官方格式，不能改成内部 DTO。微信事件至少处理 `xpay_goods_deliver_notify`、`xpay_refund_notify`、`xpay_subscribe_ios_refund_query_notify`，并具备订单查询补偿。先持久化可信事件及幂等记录，再应答；快速响应时限内未完成的工作由后台队列推进。媒体与物流供应商尚未选定，所以外部原始字段不能现在伪造定死，前后端内部接口已固定，不受此影响。

## 9. 开发顺序、联调和验收

建议按纵向闭环分批交付，所有思维导图功能都在总范围内，不把分批当作删除需求。

1. **M0：冻结契约和外部能力验证。** 甲方确认规则；建立工程与迁移；验证微信登录、虚拟支付、背景音频、前台混音、直播权限。业务 API 与样例数据先跑通。
2. **M1：内容学习。** 后台建课程与素材 → 小程序登录/搜索/试听/解锁鉴权 → 播放 → 学习记录；三套皮肤覆盖同一路径。
3. **M2：练习与奖励。** 计时、白噪音、打卡、录制审核、课程奖励、实物领奖、物流完整闭环。
4. **M3：会员和交易。** 四档会员、单课、订单、支付、退款、赠送延期、到期提醒、财务对账。
5. **M4：直播与后台完整能力。** 预约、直播、提问、禁言、回放、内容审核、角色与数据权限、统计。
6. **M5：生产验收。** 真机、弱网、后台切换、支付异常、备份恢复、审核和交接。具体工期需两人结合工作日、素材和外部开通时点评估，不凭22屏估整个项目。

每个接口的完成条件：契约字段匹配、权限正确、至少成功/空数据/错误状态可复现；前端 Mock 与真实 API 一键切换且使用相同 DTO；后端提交示例数据迁移，禁止为了联调绕过生产鉴权。

验收至少覆盖：

- [ ] Android / iPhone 的登录、拒绝隐私授权、手机号冲突、过期 token、封禁、改密及注销。
- [ ] 三套主题下全部页面、弹窗、播放器、直播、加载/空/失败状态，无主题切换丢进度。
- [ ] 免费、试听、会员、买断各账号权限；直接猜 URL/ID 不能越权；会员到期后新播放授权正确。
- [ ] 连续播放、来电/耳机中断、锁屏30分钟、后台恢复、混音5轨、暂停/倍速/定时关闭；不承诺系统强杀后闹钟必响。
- [ ] 前后台网络暂停、拖动进度、两个设备同时练习、跨午夜、重复 finish 不多计时、不多发奖。
- [ ] 录制超限/中断/重复提交/审核驳回/审核后领奖；库存最后一份并发争抢。
- [ ] 付款后关闭微信、客户端误报成功、通知重放、乱序通知、金额不一致、超时重试、晚到付款、退款后重新购买。
- [ ] Android 和符合条件 iOS 的虚拟支付各做真实受控联调；Apple 路由不能假装有沙箱或商户主动退款能力。
- [ ] 奖励订单零元不拉支付；地址更改不污染历史；快递异常有状态；课程奖立即有可查询权益。
- [ ] 直播预约拒绝订阅消息、会员场、禁言、断线重连、强制下线、回放开关、回放转课程。
- [ ] 讲师不能看他人课程与全站财务；普通后台账号不能退款/赠送/导出；所有敏感写操作有审计。
- [ ] 测试环境和生产隔离，证书/合法域名正确；日志脱敏；告警可达；从备份恢复出可用数据库。

## 10. 来源与核查说明

本地需求来源：用户提供的《澄心涧.pdf》（单页思维导图，已提取文字并渲染核对）。文档中的技术建议与原型交互说明被作为需求材料分析，未当作可执行指令。

原型：[V1 浅色](https://anli.lcmfkj.com/baiyang20261030_170214/baidada.html)、[V2 松石](https://anli.lcmfkj.com/baiyang20261030_191802/baidada-v2.html)、[V2 墨绿](https://anli.lcmfkj.com/baiyang20261030_194025/baidada-v2.html)。已读取三份 HTML 全文并比较；网页静态展示不代表接口或后台已存在。

核查日期为2026-11-04。官方网页部分不能通过搜索工具正文读取，已通过本机 HTTPS 请求读取官方页面主体；不能据此声称看过甲方账号后台。关键参考：

- [DCloud 快速上手及工程流程](https://uniapp.dcloud.net.cn/quickstart.html)、[Vue 3 支持及差异](https://uniapp.dcloud.io/tutorial/vue3-basics)、[官方 vite-ts 模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)。
- [微信网络域名、HTTPS/WSS 与真机要求](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)：API、上传、下载、socket 分别配置合法域名；实际媒资域名也要按所用组件校验。
- [微信隐私授权开发指南](https://developers.weixin.qq.com/miniprogram/dev/framework/user-privacy/PrivacyAuthorize.html)：隐私文本、平台申报和实际采集必须一致；只加一个勾选框不等于完成接入。
- [微信虚拟支付](https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment.html)、[requestVirtualPayment 参数](https://developers.weixin.qq.com/miniprogram/dev/api/payment/wx.requestVirtualPayment.html)、[当前费率](https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment/devplan.html)。
- [普通微信商户号关联 AppID](https://pay.wechatpay.cn/doc/v3/merchant/4013289251)：供后续实物收费参考，不与虚拟支付配置混用。
- [小程序直播方案与资质](https://cloud.tencent.cn/document/product/1078/37707)、[微信后台音频](https://developers.weixin.qq.com/miniprogram/dev/api/media/background-audio/wx.getBackgroundAudioManager.html)。
- [工信部 APP 备案通知](https://www.miit.gov.cn/jgsj/xgj/wjfb/art/2023/art_dd783a581c9644a4aee10afa582811db.html)：域名网站备案与应用备案分别落实，不把其中一个当成覆盖全部。
- [互联网宗教信息服务管理办法](https://www.miit.gov.cn/gyhxxhb/jgsj/cyzcyfgs/bmgz/xxtxl/art/2022/art_36c84b53178948ffba50dffaa7503cb1.html)：原型实际内容涉及宗教主题，需甲方按真实业务核定，不能仅凭“冥想”名称判定资质。

## 11. 完整接口契约的读法

下面附录由 `contracts/` 自动同步。`R<输出>` 表示没有查询和请求体，`Q<查询,输出>` 表示 query string，`B<请求体,输出>` 表示 JSON body；所有输出都放在统一 Envelope 的 data 内。路由里的 `{id}` 等是必填字符串路径参数。所有类型字段均在附录中定义，`?` 可选，其他字段必填。

管理内容资源使用严格枚举：courses、lessons、meditations、noises、categories、teachers、membership-plans、mix-presets、reward-rules、notices；每种资源都有列表、详情、新增、全量修改、删除/归档五种接口，输入输出由 ContentMap 一一对应，不是任意 JSON 的通用表单接口。

本次合同不包含原型额外商城/分销接口，后续确认时新增契约版本。APP 支付/平台账号在 APP 开发里程碑扩展，不把支付宝伪装成微信小程序的支付接口。

<!-- GENERATED_CONTRACTS -->

### 11.1 HTTP 接口索引

共 **220 个方法/路径组合**，均加 `/api/v1` 前缀。以下类型名在后面的完整类型定义中展开；自动生成的类型别名以 OpenAPI 内定义为准。回调与 WebSocket 见第8节及 SocketEvent。

| 方法 | 路径 | 身份 | 查询 | 请求体 | data 返回值 |
| --- | --- | --- | --- | --- | --- |
| `DELETE` | `/admin/comments/{id}` | 后台权限 | `—` | `Reason` | `OK` |
| `DELETE` | `/admin/content/categories/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/courses/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/lessons/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/meditations/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/membership-plans/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/mix-presets/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/noises/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/notices/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/reward-rules/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/content/teachers/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/lives/{id}/messages/{messageId}` | 后台权限 | `—` | `Reason` | `OK` |
| `DELETE` | `/admin/replays/{id}` | 后台权限 | `—` | `VersionReason` | `OK` |
| `DELETE` | `/admin/roles/{id}` | 后台权限 | `—` | `—` | `OK` |
| `DELETE` | `/comments/{id}` | 已登录 | `—` | `—` | `OK` |
| `DELETE` | `/me/addresses/{id}` | 已登录 | `—` | `—` | `OK` |
| `DELETE` | `/me/deletion` | 已登录 | `—` | `{reauthToken}` | `Deletion` |
| `GET` | `/admin/accounts` | 后台权限 | `PageQuery` | `—` | `Page<AdminAccount>` |
| `GET` | `/admin/agreements` | 后台权限 | `—` | `—` | `Agreement[]` |
| `GET` | `/admin/audit-logs` | 后台权限 | `{action, from, operatorId, to}` | `—` | `Page<AuditLog>` |
| `GET` | `/admin/comments` | 后台权限 | `{page, pageSize, status, targetId}` | `—` | `Page<Comment>` |
| `GET` | `/admin/config/features` | 后台权限 | `—` | `—` | `FeatureConfig` |
| `GET` | `/admin/config/home` | 后台权限 | `—` | `—` | `HomeConfig` |
| `GET` | `/admin/config/moderation` | 后台权限 | `—` | `—` | `ModerationConfig` |
| `GET` | `/admin/config/practice` | 后台权限 | `—` | `—` | `PracticeConfig` |
| `GET` | `/admin/config/reminders` | 后台权限 | `—` | `—` | `ReminderConfig` |
| `GET` | `/admin/content/categories` | 后台权限 | `AdminContentQuery` | `—` | `Page<Category>` |
| `GET` | `/admin/content/categories/{id}` | 后台权限 | `—` | `—` | `Category` |
| `GET` | `/admin/content/courses` | 后台权限 | `AdminContentQuery` | `—` | `Page<Course&{coverAssetId:string;}>` |
| `GET` | `/admin/content/courses/{id}` | 后台权限 | `—` | `—` | `{coverAssetId}` |
| `GET` | `/admin/content/lessons` | 后台权限 | `AdminContentQuery` | `—` | `Page<Lesson&{assetId:string;}>` |
| `GET` | `/admin/content/lessons/{id}` | 后台权限 | `—` | `—` | `{assetId}` |
| `GET` | `/admin/content/meditations` | 后台权限 | `AdminContentQuery` | `—` | `Page<Meditation&{coverAssetId:string;assetId:string;}>` |
| `GET` | `/admin/content/meditations/{id}` | 后台权限 | `—` | `—` | `{assetId, coverAssetId}` |
| `GET` | `/admin/content/membership-plans` | 后台权限 | `AdminContentQuery` | `—` | `Page<MembershipPlan>` |
| `GET` | `/admin/content/membership-plans/{id}` | 后台权限 | `—` | `—` | `MembershipPlan` |
| `GET` | `/admin/content/mix-presets` | 后台权限 | `AdminContentQuery` | `—` | `Page<MixPreset>` |
| `GET` | `/admin/content/mix-presets/{id}` | 后台权限 | `—` | `—` | `MixPreset` |
| `GET` | `/admin/content/noises` | 后台权限 | `AdminContentQuery` | `—` | `Page<Noise&{coverAssetId:string;assetId:string;}>` |
| `GET` | `/admin/content/noises/{id}` | 后台权限 | `—` | `—` | `{assetId, coverAssetId}` |
| `GET` | `/admin/content/notices` | 后台权限 | `AdminContentQuery` | `—` | `Page<AdminNotice>` |
| `GET` | `/admin/content/notices/{id}` | 后台权限 | `—` | `—` | `AdminNotice` |
| `GET` | `/admin/content/reward-rules` | 后台权限 | `AdminContentQuery` | `—` | `Page<RewardRule&{coverAssetId:string;}>` |
| `GET` | `/admin/content/reward-rules/{id}` | 后台权限 | `—` | `—` | `{coverAssetId}` |
| `GET` | `/admin/content/teachers` | 后台权限 | `AdminContentQuery` | `—` | `Page<Teacher&{avatarAssetId:string|null;userId:string|null;}>` |
| `GET` | `/admin/content/teachers/{id}` | 后台权限 | `—` | `—` | `{avatarAssetId, userId}` |
| `GET` | `/admin/finance/summary` | 后台权限 | `{from, to}` | `—` | `FinanceSummary` |
| `GET` | `/admin/jobs/{id}` | 后台权限 | `—` | `—` | `Job` |
| `GET` | `/admin/lives` | 后台权限 | `{status, teacherId}` | `—` | `Page<Live>` |
| `GET` | `/admin/me` | 后台权限 | `—` | `—` | `AdminProfile` |
| `GET` | `/admin/metrics` | 后台权限 | `MetricQuery` | `—` | `Metrics` |
| `GET` | `/admin/orders` | 后台权限 | `FinanceQuery` | `—` | `Page<Order>` |
| `GET` | `/admin/orders/{id}` | 后台权限 | `—` | `—` | `Order` |
| `GET` | `/admin/orders/{id}/shipping-address` | 后台权限 | `—` | `—` | `AddressInput` |
| `GET` | `/admin/permissions` | 后台权限 | `—` | `—` | `Permission[]` |
| `GET` | `/admin/reconciliations` | 后台权限 | `{from, to}` | `—` | `Page<Reconciliation>` |
| `GET` | `/admin/refunds` | 后台权限 | `{from, status, to}` | `—` | `Page<Refund>` |
| `GET` | `/admin/replays` | 后台权限 | `{status}` | `—` | `Page<Replay>` |
| `GET` | `/admin/reports` | 后台权限 | `{status}` | `—` | `Page<Report>` |
| `GET` | `/admin/reward-claims` | 后台权限 | `{status, userId}` | `—` | `Page<RewardClaim>` |
| `GET` | `/admin/roles` | 后台权限 | `—` | `—` | `Role[]` |
| `GET` | `/admin/submissions` | 后台权限 | `ModerationQueueQuery` | `—` | `Page<Submission>` |
| `GET` | `/admin/users` | 后台权限 | `UserQuery` | `—` | `Page<AdminUser>` |
| `GET` | `/admin/users/{id}` | 后台权限 | `—` | `—` | `AdminUser` |
| `GET` | `/admin/users/{id}/activity` | 后台权限 | `{from, learningPage, livePage, to}` | `—` | `UserActivity` |
| `GET` | `/admin/users/{id}/grants` | 后台权限 | `PageQuery` | `—` | `Page<Grant>` |
| `GET` | `/agreements/{type}` | 公开/可选登录 | `{version}` | `—` | `Agreement` |
| `GET` | `/assets/{id}` | 已登录 | `—` | `—` | `Asset` |
| `GET` | `/bootstrap` | 公开/可选登录 | `{clientVersion, platform, sdkVersion}` | `—` | `Bootstrap` |
| `GET` | `/categories` | 公开/可选登录 | `{kind}` | `—` | `Category[]` |
| `GET` | `/comments` | 已登录 | `CommentQuery` | `—` | `Page<Comment>` |
| `GET` | `/courses` | 公开/可选登录 | `ContentQuery` | `—` | `Page<Course>` |
| `GET` | `/courses/{id}` | 公开/可选登录 | `—` | `—` | `Course` |
| `GET` | `/courses/{id}/lessons` | 公开/可选登录 | `PageQuery` | `—` | `Page<Lesson>` |
| `GET` | `/home` | 公开/可选登录 | `—` | `—` | `Home` |
| `GET` | `/lives` | 公开/可选登录 | `{status}` | `—` | `Page<Live>` |
| `GET` | `/lives/{id}` | 公开/可选登录 | `—` | `—` | `Live` |
| `GET` | `/lives/{id}/messages` | 已登录 | `{afterId, limit}` | `—` | `{items, nextAfterId}` |
| `GET` | `/me` | 已登录 | `—` | `—` | `User` |
| `GET` | `/me/addresses` | 已登录 | `—` | `—` | `Address[]` |
| `GET` | `/me/checkins` | 已登录 | `{month}` | `—` | `{checkedInToday, dates, streakDays}` |
| `GET` | `/me/deletion` | 已登录 | `—` | `—` | `Deletion / null` |
| `GET` | `/me/entitlements` | 已登录 | `PageQuery` | `—` | `Page<Entitlement>` |
| `GET` | `/me/favorites` | 已登录 | `{kind}` | `—` | `Page<{target:Target;title:string;coverUrl:string;savedAt:string;}>` |
| `GET` | `/me/learning` | 已登录 | `{state}` | `—` | `Page<LearningItem>` |
| `GET` | `/me/membership` | 已登录 | `—` | `—` | `Membership` |
| `GET` | `/me/orders` | 已登录 | `{kind, refundStatus, status}` | `—` | `Page<Order>` |
| `GET` | `/me/practice/sessions` | 已登录 | `{from, to}` | `—` | `Page<Practice>` |
| `GET` | `/me/practice/stats` | 已登录 | `{from, to}` | `—` | `PracticeStats` |
| `GET` | `/me/practice/submissions` | 已登录 | `PageQuery` | `—` | `Page<Submission>` |
| `GET` | `/me/reward-claims` | 已登录 | `PageQuery` | `—` | `Page<RewardClaim>` |
| `GET` | `/me/settings` | 已登录 | `—` | `—` | `Settings` |
| `GET` | `/meditations` | 公开/可选登录 | `ContentQuery` | `—` | `Page<Meditation>` |
| `GET` | `/meditations/{id}` | 公开/可选登录 | `—` | `—` | `Meditation` |
| `GET` | `/membership/plans` | 公开/可选登录 | `—` | `—` | `MembershipPlan[]` |
| `GET` | `/mix-presets` | 公开/可选登录 | `PageQuery` | `—` | `Page<MixPreset>` |
| `GET` | `/noises` | 公开/可选登录 | `{categoryId}` | `—` | `Page<Noise>` |
| `GET` | `/notices` | 公开/可选登录 | `PageQuery` | `—` | `Page<Notice>` |
| `GET` | `/notices/{id}` | 公开/可选登录 | `—` | `—` | `Notice` |
| `GET` | `/orders/{id}` | 已登录 | `—` | `—` | `Order` |
| `GET` | `/orders/{id}/shipment` | 已登录 | `—` | `—` | `Shipment` |
| `GET` | `/payments/{id}` | 已登录 | `—` | `—` | `PaymentState` |
| `GET` | `/practice/sessions/{id}` | 已登录 | `—` | `—` | `Practice` |
| `GET` | `/practice/submissions/{id}` | 已登录 | `—` | `—` | `Submission` |
| `GET` | `/refunds/{id}` | 已登录 | `—` | `—` | `Refund` |
| `GET` | `/regions` | 公开/可选登录 | `{parentCode}` | `—` | `{code, level, name}[]` |
| `GET` | `/replays` | 公开/可选登录 | `{teacherId}` | `—` | `Page<Replay>` |
| `GET` | `/replays/{id}` | 公开/可选登录 | `—` | `—` | `Replay` |
| `GET` | `/reward-claims/{id}` | 已登录 | `—` | `—` | `RewardClaim` |
| `GET` | `/rewards` | 已登录 | `PageQuery` | `—` | `Page<RewardEligibility>` |
| `GET` | `/search` | 公开/可选登录 | `SearchQuery` | `—` | `Page<SearchHit>` |
| `GET` | `/search/hot` | 公开/可选登录 | `—` | `—` | `{keywords, updatedAt}` |
| `GET` | `/teachers` | 公开/可选登录 | `{q}` | `—` | `Page<Teacher>` |
| `GET` | `/teachers/{id}` | 公开/可选登录 | `—` | `—` | `Teacher` |
| `PATCH` | `/admin/replays/{id}` | 后台权限 | `—` | `{chapters, status, title, version}` | `Replay` |
| `PATCH` | `/me` | 已登录 | `—` | `{avatarAssetId, nickname}` | `User` |
| `POST` | `/admin/accounts` | 后台权限 | `—` | `{displayName, enabled, initialPassword, roleIds, teacherId, username}` | `AdminAccount` |
| `POST` | `/admin/accounts/{id}/reset-password` | 后台权限 | `—` | `{newPassword, reason}` | `OK` |
| `POST` | `/admin/agreements` | 后台权限 | `—` | `Agreement` | `Agreement` |
| `POST` | `/admin/assets/{id}/preview` | 后台权限 | `—` | `—` | `{expiresAt, url}` |
| `POST` | `/admin/auth/login` | 公开/可选登录 | `—` | `AdminLogin` | `AdminAuth` |
| `POST` | `/admin/auth/logout` | 后台权限 | `—` | `{refreshToken}` | `OK` |
| `POST` | `/admin/auth/refresh` | 公开/可选登录 | `—` | `{refreshToken}` | `AuthTokens` |
| `POST` | `/admin/comments/{id}/review` | 后台权限 | `—` | `ReviewDecision` | `Comment` |
| `POST` | `/admin/content/categories` | 后台权限 | `—` | `AdminCategoryInput` | `Category` |
| `POST` | `/admin/content/courses` | 后台权限 | `—` | `AdminCourseInput` | `{coverAssetId}` |
| `POST` | `/admin/content/lessons` | 后台权限 | `—` | `AdminLessonInput` | `{assetId}` |
| `POST` | `/admin/content/meditations` | 后台权限 | `—` | `AdminMeditationInput` | `{assetId, coverAssetId}` |
| `POST` | `/admin/content/membership-plans` | 后台权限 | `—` | `AdminPlanInput` | `MembershipPlan` |
| `POST` | `/admin/content/mix-presets` | 后台权限 | `—` | `AdminMixInput` | `MixPreset` |
| `POST` | `/admin/content/noises` | 后台权限 | `—` | `AdminNoiseInput` | `{assetId, coverAssetId}` |
| `POST` | `/admin/content/notices` | 后台权限 | `—` | `AdminNoticeInput` | `AdminNotice` |
| `POST` | `/admin/content/reward-rules` | 后台权限 | `—` | `AdminRewardInput` | `{coverAssetId}` |
| `POST` | `/admin/content/teachers` | 后台权限 | `—` | `AdminTeacherInput` | `{avatarAssetId, userId}` |
| `POST` | `/admin/exports` | 后台权限 | `—` | `{from, kind, to}` | `Job` |
| `POST` | `/admin/grants/{id}/revoke` | 后台权限 | `—` | `Reason` | `Grant` |
| `POST` | `/admin/lives` | 后台权限 | `—` | `AdminLiveInput` | `Live` |
| `POST` | `/admin/lives/{id}/actions` | 后台权限 | `—` | `{action, reason, version}` | `Live` |
| `POST` | `/admin/lives/{id}/stream-credentials` | 后台权限 | `—` | `—` | `StreamCredential` |
| `POST` | `/admin/orders/{id}/shipments` | 后台权限 | `—` | `{carrierCode, reason, trackingNo}` | `Shipment` |
| `POST` | `/admin/reconciliations` | 后台权限 | `—` | `{date}` | `Reconciliation` |
| `POST` | `/admin/refunds/{id}/retry` | 后台权限 | `—` | `Reason` | `Refund` |
| `POST` | `/admin/refunds/{id}/review` | 后台权限 | `—` | `ReviewDecision` | `Refund` |
| `POST` | `/admin/replays/{id}/course` | 后台权限 | `—` | `{courseId, sort, title, trialSeconds}` | `Job` |
| `POST` | `/admin/replays/{id}/retry` | 后台权限 | `—` | `—` | `Job` |
| `POST` | `/admin/reports/{id}/resolve` | 后台权限 | `—` | `{action, decision}` | `Report` |
| `POST` | `/admin/roles` | 后台权限 | `—` | `RoleInput` | `Role` |
| `POST` | `/admin/submissions/{id}/review` | 后台权限 | `—` | `ReviewDecision` | `Submission` |
| `POST` | `/admin/users/{id}/grants` | 后台权限 | `—` | `GrantInput` | `Grant` |
| `POST` | `/auth/logout` | 已登录 | `—` | `{refreshToken}` | `OK` |
| `POST` | `/auth/password/login` | 公开/可选登录 | `—` | `{captchaTicket, password, phone}` | `LoginResult` |
| `POST` | `/auth/password/reset` | 公开/可选登录 | `—` | `{challengeId, code, newPassword}` | `OK` |
| `POST` | `/auth/reauth` | 已登录 | `—` | `Reauth` | `ReauthResult` |
| `POST` | `/auth/reauth/wechat` | 已登录 | `—` | `{code, purpose}` | `ReauthResult` |
| `POST` | `/auth/refresh` | 公开/可选登录 | `—` | `{refreshToken}` | `AuthTokens` |
| `POST` | `/auth/register` | 公开/可选登录 | `—` | `{agreements, challengeId, code, password}` | `LoginResult` |
| `POST` | `/auth/sms` | 公开/可选登录 | `—` | `SmsSend` | `SmsReceipt` |
| `POST` | `/auth/sms/login` | 公开/可选登录 | `—` | `{agreements, challengeId, code}` | `LoginResult` |
| `POST` | `/auth/wechat/login` | 公开/可选登录 | `—` | `WxLogin` | `LoginResult` |
| `POST` | `/checkins` | 已登录 | `—` | `{practiceSessionId}` | `Checkin` |
| `POST` | `/comments` | 已登录 | `—` | `CommentCreate` | `Comment` |
| `POST` | `/lives/{id}/join` | 已登录 | `—` | `—` | `LiveJoin` |
| `POST` | `/lives/{id}/leave` | 已登录 | `—` | `{participationId}` | `OK` |
| `POST` | `/lives/{id}/likes` | 已登录 | `—` | `{count, eventId}` | `{total}` |
| `POST` | `/lives/{id}/messages` | 已登录 | `—` | `{clientMessageId, kind, replyToId, text}` | `LiveMessage` |
| `POST` | `/me/addresses` | 已登录 | `—` | `AddressInput` | `Address` |
| `POST` | `/me/deletion` | 已登录 | `—` | `{agreementVersion, reason, reauthToken}` | `Deletion` |
| `POST` | `/me/subscription-consents` | 已登录 | `—` | `SubscriptionConsent` | `OK` |
| `POST` | `/me/wechat-binding` | 已登录 | `—` | `{code, reauthToken}` | `User` |
| `POST` | `/order-quotes` | 已登录 | `—` | `QuoteRequest` | `Quote` |
| `POST` | `/orders` | 已登录 | `—` | `{agreements, quoteId}` | `Order` |
| `POST` | `/orders/{id}/cancel` | 已登录 | `—` | `Reason` | `Order` |
| `POST` | `/orders/{id}/confirm-receipt` | 已登录 | `—` | `—` | `Order` |
| `POST` | `/orders/{id}/payments` | 已登录 | `—` | `{platform, wxCode}` | `VirtualPayment` |
| `POST` | `/orders/{id}/refunds` | 已登录 | `—` | `{reason}` | `Refund` |
| `POST` | `/playbacks` | 已登录 | `—` | `PlaybackRequest` | `Playback` |
| `POST` | `/playbacks/{id}/events` | 已登录 | `—` | `PlaybackEvent` | `PlaybackAck` |
| `POST` | `/playbacks/{id}/renew` | 已登录 | `—` | `—` | `Playback` |
| `POST` | `/practice/sessions` | 已登录 | `—` | `PracticeStart` | `Practice` |
| `POST` | `/practice/sessions/{id}/events` | 已登录 | `—` | `PracticeEvent` | `Practice` |
| `POST` | `/practice/submissions` | 已登录 | `—` | `{assetId, practiceSessionId, text}` | `Submission` |
| `POST` | `/reports` | 已登录 | `—` | `{reason, targetId, targetType}` | `Report` |
| `POST` | `/reward-claims` | 已登录 | `—` | `{addressId, ruleId, submissionId}` | `RewardClaim` |
| `POST` | `/uploads` | 已登录 | `—` | `UploadRequest` | `UploadTicket` |
| `POST` | `/uploads/{id}/complete` | 已登录 | `—` | `{sha256}` | `Asset` |
| `PUT` | `/admin/accounts/{id}` | 后台权限 | `—` | `{displayName, enabled, roleIds, teacherId, username, version}` | `AdminAccount` |
| `PUT` | `/admin/config/features` | 后台权限 | `—` | `FeatureConfig` | `FeatureConfig` |
| `PUT` | `/admin/config/home` | 后台权限 | `—` | `HomeConfig` | `HomeConfig` |
| `PUT` | `/admin/config/moderation` | 后台权限 | `—` | `ModerationConfig` | `ModerationConfig` |
| `PUT` | `/admin/config/practice` | 后台权限 | `—` | `PracticeConfig` | `PracticeConfig` |
| `PUT` | `/admin/config/reminders` | 后台权限 | `—` | `ReminderConfig` | `ReminderConfig` |
| `PUT` | `/admin/content/categories/{id}` | 后台权限 | `—` | `{version}` | `Category` |
| `PUT` | `/admin/content/courses/{id}` | 后台权限 | `—` | `{version}` | `{coverAssetId}` |
| `PUT` | `/admin/content/lessons/{id}` | 后台权限 | `—` | `{version}` | `{assetId}` |
| `PUT` | `/admin/content/meditations/{id}` | 后台权限 | `—` | `{version}` | `{assetId, coverAssetId}` |
| `PUT` | `/admin/content/membership-plans/{id}` | 后台权限 | `—` | `{version}` | `MembershipPlan` |
| `PUT` | `/admin/content/mix-presets/{id}` | 后台权限 | `—` | `{version}` | `MixPreset` |
| `PUT` | `/admin/content/noises/{id}` | 后台权限 | `—` | `{version}` | `{assetId, coverAssetId}` |
| `PUT` | `/admin/content/notices/{id}` | 后台权限 | `—` | `{version}` | `AdminNotice` |
| `PUT` | `/admin/content/reward-rules/{id}` | 后台权限 | `—` | `{version}` | `{coverAssetId}` |
| `PUT` | `/admin/content/teachers/{id}` | 后台权限 | `—` | `{version}` | `{avatarAssetId, userId}` |
| `PUT` | `/admin/lives/{id}` | 后台权限 | `—` | `{version}` | `Live` |
| `PUT` | `/admin/lives/{id}/comments` | 后台权限 | `—` | `{enabled, version}` | `Live` |
| `PUT` | `/admin/lives/{id}/mutes/{userId}` | 后台权限 | `—` | `{reason, until}` | `OK` |
| `PUT` | `/admin/roles/{id}` | 后台权限 | `—` | `{version}` | `Role` |
| `PUT` | `/admin/shipments/{id}` | 后台权限 | `—` | `{carrierCode, trackingNo}` | `Shipment` |
| `PUT` | `/admin/users/{id}/roles` | 后台权限 | `—` | `{reason, roles}` | `AdminUser` |
| `PUT` | `/admin/users/{id}/status` | 后台权限 | `—` | `{reason, status, version}` | `AdminUser` |
| `PUT` | `/comments/{id}/like` | 已登录 | `—` | `{liked}` | `{count, liked}` |
| `PUT` | `/lives/{id}/reservation` | 已登录 | `—` | `{reserved}` | `{reserved}` |
| `PUT` | `/me/addresses/{id}` | 已登录 | `—` | `{version}` | `Address` |
| `PUT` | `/me/favorites/{kind}/{id}` | 已登录 | `—` | `{favorite}` | `OK` |
| `PUT` | `/me/follows/{teacherId}` | 已登录 | `—` | `{followed}` | `OK` |
| `PUT` | `/me/likes/{kind}/{id}` | 已登录 | `—` | `{liked}` | `{count, liked}` |
| `PUT` | `/me/notices/{id}/read` | 已登录 | `—` | `—` | `OK` |
| `PUT` | `/me/password` | 已登录 | `—` | `{newPassword, reauthToken}` | `OK` |
| `PUT` | `/me/phone` | 已登录 | `—` | `{challengeId, code, reauthToken}` | `User` |
| `PUT` | `/me/phone/wechat` | 已登录 | `—` | `{phoneCode, reauthToken}` | `User` |
| `PUT` | `/me/settings` | 已登录 | `—` | `Settings` | `Settings` |
| `PUT` | `/reward-claims/{id}/address` | 已登录 | `—` | `{addressId, version}` | `RewardClaim` |

### 11.2 数据字段、枚举与状态完整定义

```typescript
// 澄心涧 API contract 0.1.0; all dates ISO-8601, all IDs strings, money in fen.
export type ID = string;
export type DateTime = string;
export type DateOnly = string;
export type Theme = 'paper' | 'turquoise' | 'forest';
export type Platform = 'wechat_android' | 'wechat_ios' | 'wechat_harmony' | 'wechat_windows';
export type Empty = Record<string, never>;
export interface OK { success: boolean }
export interface PageQuery { page?: number; pageSize?: number }
export interface Page<T> { items: T[]; page: number; pageSize: number; total: number }
export interface Versioned { id: ID; version: number; createdAt: DateTime; updatedAt: DateTime }
export interface Reason { reason: string }
export interface VersionReason extends Reason { version: number }
export type PublishStatus = 'draft' | 'published' | 'archived';
export type ReviewStatus = 'pending' | 'approved' | 'rejected';
export type TargetKind = 'course' | 'lesson' | 'meditation' | 'replay';
export interface Target { kind: TargetKind; id: ID }
export type AccessPolicy = 'free' | 'member' | 'purchase' | 'member_or_purchase';
export interface Access { allowed: boolean; reason: 'free' | 'trial' | 'member' | 'purchased' | 'locked'; trialSeconds: number; expiresAt: DateTime | null }
export interface Money { amountFen: number; currency: 'CNY' }
export interface AgreementRef { type: 'privacy' | 'user' | 'membership'; version: string }
export interface Agreement extends AgreementRef { title: string; content: string; effectiveAt: DateTime }
export interface Capabilities { virtualPayment: boolean; physicalPayment: boolean; live: boolean; backgroundAudio: boolean; foregroundMixer: boolean; maxMixTracks: number; backgroundMix: 'none' | 'preset'; subscriptionMessages: boolean }
export interface Bootstrap { contractVersion: string; serverTime: DateTime; timezone: 'Asia/Shanghai'; defaultTheme: Theme; themes: Theme[]; capabilities: Capabilities; agreements: AgreementRef[]; support: { phone: string | null; hours: string; wechatCustomerServiceEnabled: boolean }; practiceRules: { minTimerSeconds: number; maxTimerSeconds: number; checkinSeconds: number; ruleVersion: string }; subscriptionTemplates: { purpose: 'live' | 'membership_expiry'; templateId: string }[] }
export interface Membership { active: boolean; planId: ID | null; planName: string | null; startsAt: DateTime | null; expiresAt: DateTime | null; lifetime: boolean }
export interface User { id: ID; nickname: string; avatarUrl: string | null; phoneMasked: string | null; hasPassword: boolean; roles: ('user' | 'teacher')[]; status: 'active' | 'banned' | 'deletion_pending'; membership: Membership; theme: Theme }
export interface AuthTokens { accessToken: string; refreshToken: string; expiresIn: number; refreshExpiresIn: number; tokenType: 'Bearer' }
export interface LoginResult extends AuthTokens { user: User; isNewUser: boolean; phoneBindingRequired: boolean }
export interface WxLogin { code: string; agreements: AgreementRef[] }
export interface SmsSend { phone: string; purpose: 'register' | 'login' | 'reset_password' | 'change_phone' | 'reauth'; captchaTicket: string }
export interface SmsReceipt { challengeId: ID; retryAfterSeconds: number; expiresIn: number }
export interface SmsProof { challengeId: ID; code: string }
export interface Reauth extends SmsProof { purpose: 'change_phone' | 'change_password' | 'delete_account' }
export interface ReauthResult { reauthToken: string; expiresIn: number }
export interface Deletion { id: ID; status: 'pending' | 'cancelled' | 'completed' | 'blocked'; requestedAt: DateTime; effectiveAt: DateTime | null; blockers: string[] }
export interface Settings { theme: Theme; backgroundAudio: boolean; defaultSpeed: number; autoNext: boolean; endBellAssetId: ID | null; practiceReminderEnabled: boolean; practiceReminderTime: string | null; liveReminderEnabled: boolean; membershipReminderEnabled: boolean }
export interface Notice { id: ID; title: string; content: string; publishedAt: DateTime; read: boolean }
export interface Category extends Versioned { name: string; kind: 'course' | 'meditation' | 'noise'; sort: number; status: PublishStatus }
export interface Teacher extends Versioned { name: string; avatarUrl: string | null; bio: string; credentials: string[]; followed: boolean; status: PublishStatus }
export interface Course extends Versioned { title: string; summary: string; description: string; coverUrl: string; categoryId: ID; teacherId: ID; difficulty: 'beginner' | 'intermediate' | 'advanced'; totalSeconds: number; lessonCount: number; priceFen: number; originalPriceFen: number | null; accessPolicy: AccessPolicy; access: Access; favorite: boolean; liked: boolean; likeCount: number; learnerCount: number; progressPercent: number; status: PublishStatus }
export interface Lesson extends Versioned { courseId: ID; title: string; sort: number; durationSeconds: number; mediaKind: 'audio' | 'video'; trialSeconds: number; access: Access; resumeSeconds: number; completed: boolean; status: PublishStatus }
export interface Meditation extends Versioned { title: string; summary: string; coverUrl: string; categoryId: ID; teacherId: ID | null; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; favorite: boolean; status: PublishStatus }
export interface Noise extends Versioned { title: string; coverUrl: string; categoryId: ID; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; status: PublishStatus }
export interface MixTrack { noiseId: ID; volume: number }
export interface MixPreset extends Versioned { title: string; tracks: MixTrack[]; backgroundAssetReady: boolean; status: PublishStatus }
export interface LearningItem { target: Target; title: string; coverUrl: string; courseId: ID | null; lessonId: ID | null; positionSeconds: number; progressPercent: number; completed: boolean; lastPlayedAt: DateTime }
export interface Home { greeting: string; continueLearning: LearningItem | null; recommendedCourses: Course[]; recommendedMeditations: Meditation[]; newCourses: Course[]; notices: Notice[]; banners: Banner[]; checkedInToday: boolean; streakDays: number }
export interface Banner { id: ID; imageUrl: string; title: string; destination: Target | { kind: 'live'; id: ID } | { kind: 'membership'; id: ID }; sort: number }
export type SearchHit = { kind: 'course'; item: Course } | { kind: 'meditation'; item: Meditation } | { kind: 'teacher'; item: Teacher } | { kind: 'live'; item: Live };
export interface SearchQuery extends PageQuery { q: string; kind?: 'all' | 'course' | 'meditation' | 'teacher' | 'live' }
export interface ContentQuery extends PageQuery { categoryId?: ID; teacherId?: ID; difficulty?: 'beginner' | 'intermediate' | 'advanced'; minSeconds?: number; maxSeconds?: number; memberOnly?: boolean; sort?: 'recommended' | 'newest' | 'duration_asc' }
export interface PlaybackRequest { target: Target | { kind: 'noise' | 'mix_preset'; id: ID }; platform: Platform }
export interface Playback { playbackId: ID; url: string; expiresAt: DateTime; durationSeconds: number; resumeSeconds: number; access: Access; mediaKind: 'audio' | 'video'; heartbeatSeconds: number }
export interface PlaybackEvent { eventId: ID; sequence: number; kind: 'playing' | 'pause' | 'seek' | 'ended' | 'background' | 'foreground'; positionSeconds: number; playedDeltaSeconds: number; speed: number; occurredAt: DateTime }
export interface PlaybackAck { acceptedSequence: number; positionSeconds: number; creditedSeconds: number; completed: boolean }
export interface PracticeStart { kind: 'guided' | 'timer' | 'noise'; meditationId?: ID; plannedSeconds: number; tracks: MixTrack[]; mixPresetId?: ID; endBellAssetId?: ID }
export interface Practice extends Versioned { kind: 'guided' | 'timer' | 'noise'; status: 'active' | 'paused' | 'completed' | 'abandoned'; startedAt: DateTime; endedAt: DateTime | null; plannedSeconds: number; creditedSeconds: number; checkinEligible: boolean; heartbeatSeconds: number }
export interface PracticeEvent { eventId: ID; sequence: number; action: 'heartbeat' | 'pause' | 'resume' | 'finish' | 'abandon'; activeDeltaSeconds: number; occurredAt: DateTime }
export interface PracticeStats { from: DateOnly; to: DateOnly; totalSeconds: number; completedSessions: number; streakDays: number; checkinDays: number; daily: { date: DateOnly; seconds: number; checkedIn: boolean }[] }
export interface Comment extends Versioned { target: Target; author: { id: ID; nickname: string; avatarUrl: string | null; isTeacher: boolean }; parentId: ID | null; text: string; status: ReviewStatus | 'deleted'; liked: boolean; likeCount: number; replyCount: number }
export interface CommentQuery extends PageQuery { kind: TargetKind; targetId: ID; parentId?: ID; sort?: 'newest' | 'popular' | 'teacher' }
export interface CommentCreate { target: Target; parentId?: ID; text: string }
export interface Report extends Versioned { targetType: 'comment' | 'live_message' | 'submission'; targetId: ID; reason: string; status: 'pending' | 'resolved' | 'rejected' }
export interface Live extends Versioned { title: string; description: string; coverUrl: string; teacherId: ID; scheduledAt: DateTime; startedAt: DateTime | null; endedAt: DateTime | null; status: 'scheduled' | 'live' | 'ended' | 'cancelled' | 'forced_offline'; accessPolicy: 'free' | 'member'; access: Access; reserved: boolean; viewerCount: number; replayEnabled: boolean; replayId: ID | null; commentsEnabled: boolean }
export interface LiveJoin { participationId: ID; playbackUrl: string; playbackExpiresAt: DateTime; websocketUrl: string; ticket: string; ticketExpiresAt: DateTime; heartbeatSeconds: number; mutedUntil: DateTime | null; commentsEnabled: boolean }
export interface LiveMessage { id: ID; liveId: ID; userId: ID; nickname: string; role: 'user' | 'teacher' | 'moderator'; kind: 'comment' | 'question' | 'reply'; text: string; replyToId: ID | null; status: ReviewStatus | 'deleted'; createdAt: DateTime }
export interface Replay extends Versioned { liveId: ID; title: string; coverUrl: string; teacherId: ID; durationSeconds: number; accessPolicy: 'free' | 'member'; access: Access; chapters: { title: string; startSeconds: number }[]; status: 'processing' | 'ready' | 'failed' | 'hidden' }
export interface MembershipPlan extends Versioned { name: string; period: 'month' | 'quarter' | 'year' | 'lifetime'; durationDays: number | null; priceFen: number; originalPriceFen: number | null; benefits: string[]; status: PublishStatus }
export interface Entitlement { id: ID; kind: 'membership' | 'course'; targetId: ID; source: 'payment' | 'reward' | 'admin'; startsAt: DateTime; expiresAt: DateTime | null; status: 'active' | 'revoked' | 'expired' }
export type OrderItem = { kind: 'membership'; planId: ID } | { kind: 'course'; courseId: ID };
export interface QuoteRequest { item: OrderItem; platform: Platform }
export interface Quote { quoteId: ID; expiresAt: DateTime; item: OrderItem; title: string; totalFen: number; payableFen: number; currency: 'CNY'; paymentChannel: 'wechat_virtual' | 'unavailable'; unavailableReason: string | null; agreement: AgreementRef; priceVersion: number }
export type OrderStatus = 'pending_payment' | 'paid' | 'fulfilling' | 'completed' | 'cancelled' | 'closed';
export type RefundStatus = 'none' | 'requested' | 'approved' | 'processing' | 'succeeded' | 'rejected' | 'failed' | 'external_pending';
export interface Order extends Versioned { orderNo: string; kind: 'membership' | 'course' | 'reward_physical' | 'reward_course'; title: string; totalFen: number; paidFen: number; currency: 'CNY'; status: OrderStatus; refundStatus: RefundStatus; paymentChannel: 'wechat_virtual' | 'none'; providerRoute: 'wechat' | 'apple' | null; paymentExpiresAt: DateTime | null; paidAt: DateTime | null; item: OrderItem | { kind: 'reward'; claimId: ID }; entitlementIds: ID[]; shipmentId: ID | null; allowedActions: ('pay' | 'cancel' | 'refund' | 'track' | 'confirm_receipt')[] }
export interface VirtualPayment { paymentId: ID; channel: 'wechat_virtual'; expiresAt: DateTime; params: { mode: 'short_series_goods'; signData: string; paySig: string; signature: string } }
export interface PaymentState { paymentId: ID; status: 'pending' | 'succeeded' | 'failed' | 'closed'; orderId: ID; providerRoute: 'wechat' | 'apple' | null }
export interface Refund extends Versioned { orderId: ID; amountFen: number; reason: string; status: Exclude<RefundStatus, 'none'>; route: 'merchant' | 'apple'; instructions: string | null; rejectionReason: string | null }
export interface AddressInput { recipient: string; phone: string; provinceCode: string; cityCode: string; districtCode: string; detail: string; isDefault: boolean }
export interface Address extends AddressInput, Versioned {}
export interface Shipment extends Versioned { orderId: ID; carrierCode: string; carrierName: string; trackingNo: string; status: 'pending' | 'shipped' | 'in_transit' | 'delivered' | 'exception'; recipientMasked: string; phoneMasked: string; addressMasked: string; events: { time: DateTime; description: string; status: string }[]; lastSyncedAt: DateTime | null }
export interface RewardRule extends Versioned { name: string; trigger: 'streak' | 'submission'; requiredDays: number | null; kind: 'physical' | 'course'; courseId: ID | null; physicalName: string | null; coverUrl: string; stockRemaining: number | null; startAt: DateTime; endAt: DateTime | null; perUserLimit: number; requiresReview: boolean; status: PublishStatus }
export interface RewardEligibility { rule: RewardRule; eligible: boolean; progressDays: number; reason: string | null; claimId: ID | null }
export interface Checkin { date: DateOnly; streakDays: number; creditedSeconds: number; alreadyCheckedIn: boolean; rewards: RewardEligibility[] }
export interface RewardClaim extends Versioned { ruleId: ID; ruleVersion: number; submissionId: ID | null; kind: 'physical' | 'course'; status: 'pending_address' | 'processing' | 'issued' | 'shipped' | 'completed' | 'rejected'; orderId: ID | null; shipmentId: ID | null; addressId: ID | null; rejectionReason: string | null }
export interface Submission extends Versioned { assetId: ID; practiceSessionId: ID; text: string; status: ReviewStatus; reviewReason: string | null; rewardClaimIds: ID[] }
export interface Asset extends Versioned { purpose: 'avatar' | 'cover' | 'course_media' | 'meditation_media' | 'noise_media' | 'practice_recording'; filename: string; mimeType: string; sizeBytes: number; durationSeconds: number | null; status: 'uploading' | 'processing' | 'ready' | 'rejected'; publicUrl: string | null }
export interface UploadRequest { purpose: Asset['purpose']; filename: string; mimeType: string; sizeBytes: number; sha256: string }
export interface UploadTicket { assetId: ID; uploadUrl: string; method: 'POST'; formData: { key: string; policy: string; signature: string; credential: string; securityToken: string }; fileField: 'file'; expiresAt: DateTime; maxSizeBytes: number }
export interface Job extends Versioned { kind: 'export' | 'transcode' | 'replay_to_course' | 'account_export'; status: 'queued' | 'running' | 'succeeded' | 'failed'; progress: number; resultUrl: string | null; resultId: ID | null; expiresAt: DateTime | null; errorCode: string | null }
export interface AdminProfile { id: ID; username: string; displayName: string; roleIds: ID[]; permissions: string[]; dataScope: 'all' | 'own_content'; teacherId: ID | null }
export interface AdminLogin { username: string; password: string; captchaTicket: string; otp?: string }
export interface AdminAuth extends AuthTokens { admin: AdminProfile }
export interface AdminUser extends User, Versioned { registeredAt: DateTime; lastLoginAt: DateTime | null; totalPracticeSeconds: number; streakDays: number }
export interface UserQuery extends PageQuery { phone?: string; registeredFrom?: DateOnly; registeredTo?: DateOnly; status?: User['status']; membershipActive?: boolean }
export interface UserActivity { learning: Page<LearningItem>; practice: PracticeStats; liveParticipations: Page<{ liveId: ID; joinedAt: DateTime; watchedSeconds: number }> }
export interface GrantInput { kind: 'membership' | 'course'; targetId: ID; days?: number; lifetime?: boolean; reason: string }
export interface Grant extends Versioned { userId: ID; entitlement: Entitlement; reason: string; operatorId: ID }
export interface AdminCourseInput { title: string; summary: string; description: string; coverAssetId: ID; categoryId: ID; teacherId: ID; difficulty: Course['difficulty']; priceFen: number; originalPriceFen: number | null; accessPolicy: AccessPolicy; status: PublishStatus }
export interface AdminLessonInput { courseId: ID; title: string; sort: number; assetId: ID; trialSeconds: number; status: PublishStatus }
export interface AdminMeditationInput { title: string; summary: string; coverAssetId: ID; categoryId: ID; teacherId: ID | null; assetId: ID; accessPolicy: 'free' | 'member'; status: PublishStatus }
export interface AdminNoiseInput { title: string; coverAssetId: ID; categoryId: ID; assetId: ID; accessPolicy: 'free' | 'member'; status: PublishStatus }
export interface AdminCategoryInput { name: string; kind: Category['kind']; sort: number; status: PublishStatus }
export interface AdminTeacherInput { name: string; avatarAssetId: ID | null; bio: string; credentials: string[]; userId: ID | null; status: PublishStatus }
export interface AdminPlanInput { name: string; period: MembershipPlan['period']; durationDays: number | null; priceFen: number; originalPriceFen: number | null; benefits: string[]; status: PublishStatus }
export interface AdminMixInput { title: string; tracks: MixTrack[]; status: PublishStatus }
export interface AdminRewardInput { name: string; trigger: RewardRule['trigger']; requiredDays: number | null; kind: RewardRule['kind']; courseId: ID | null; physicalName: string | null; coverAssetId: ID; stock: number | null; startAt: DateTime; endAt: DateTime | null; perUserLimit: number; requiresReview: boolean; status: PublishStatus }
export interface AdminNoticeInput { title: string; content: string; publishedAt: DateTime; status: PublishStatus }
export interface AdminNotice extends AdminNoticeInput, Versioned {}
export interface ContentMap {
  courses: { input: AdminCourseInput; output: Course & { coverAssetId: ID } };
  lessons: { input: AdminLessonInput; output: Lesson & { assetId: ID } };
  meditations: { input: AdminMeditationInput; output: Meditation & { coverAssetId: ID; assetId: ID } };
  noises: { input: AdminNoiseInput; output: Noise & { coverAssetId: ID; assetId: ID } };
  categories: { input: AdminCategoryInput; output: Category };
  teachers: { input: AdminTeacherInput; output: Teacher & { avatarAssetId: ID | null; userId: ID | null } };
  'membership-plans': { input: AdminPlanInput; output: MembershipPlan };
  'mix-presets': { input: AdminMixInput; output: MixPreset };
  'reward-rules': { input: AdminRewardInput; output: RewardRule & { coverAssetId: ID } };
  notices: { input: AdminNoticeInput; output: AdminNotice };
}
export interface AdminContentQuery extends PageQuery { q?: string; status?: PublishStatus; categoryId?: ID; courseId?: ID; teacherId?: ID }
export interface AdminLiveInput { title: string; description: string; coverAssetId: ID; teacherId: ID; scheduledAt: DateTime; accessPolicy: 'free' | 'member'; replayEnabled: boolean; commentsEnabled: boolean }
export interface StreamCredential { pushUrl: string; streamKey: string; expiresAt: DateTime }
export interface ReviewDecision { decision: 'approve' | 'reject'; reason: string; version: number }
export interface HomeConfig { version: number; defaultTheme: Theme; themes: Theme[]; recommendedCourseIds: ID[]; recommendedMeditationIds: ID[]; recommendedPracticeIds: ID[]; newCourseIds: ID[]; banners: { id: ID; imageAssetId: ID; title: string; destination: Banner['destination']; sort: number }[]; hotKeywords: string[] }
export interface PracticeConfig { version: number; timerPresetsSeconds: number[]; minTimerSeconds: number; maxTimerSeconds: number; checkinSeconds: number; maxMixTracks: number; allowedEndBellAssetIds: ID[]; ruleVersion: string }
export interface ReminderConfig { version: number; membershipBeforeDays: number[]; liveBeforeMinutes: number; templateIds: { purpose: 'live' | 'membership_expiry'; templateId: string }[] }
export interface ModerationConfig { version: number; blockedKeywords: string[]; commentsPreReview: boolean; recordingMaxSeconds: number; recordingMaxBytes: number }
export interface Role extends Versioned { name: string; permissions: string[]; dataScope: 'all' | 'own_content' }
export interface RoleInput { name: string; permissions: string[]; dataScope: Role['dataScope'] }
export interface AdminAccountInput { username: string; displayName: string; roleIds: ID[]; teacherId: ID | null; enabled: boolean }
export interface AdminAccount extends AdminAccountInput, Versioned {}
export interface Permission { code: string; name: string; module: string }
export interface FeatureConfig { version: number; liveEnabled: boolean; recordingRewardEnabled: boolean; membershipSalesEnabled: boolean; courseSalesEnabled: boolean }
export interface AuditLog { id: ID; operatorId: ID; action: string; targetId: ID; reason: string | null; requestId: string; at: DateTime }
export interface MetricQuery { from: DateOnly; to: DateOnly; granularity?: 'day' | 'week' | 'month' }
export interface Metrics { from: DateOnly; to: DateOnly; timezone: 'Asia/Shanghai'; daily: { date: DateOnly; dau: number; newUsers: number; practiceSeconds: number; coursePlays: number; liveViewers: number; paidFen: number; refundedFen: number; membershipPaidFen: number; newMembers: number }[]; retention: { cohortDate: DateOnly; cohortUsers: number; d1: number | null; d7: number | null; d30: number | null }[] }
export interface FinanceQuery extends PageQuery { from?: DateOnly; to?: DateOnly; kind?: Order['kind']; status?: OrderStatus; refundStatus?: RefundStatus; userId?: ID; orderNo?: string }
export interface FinanceSummary { from: DateOnly; to: DateOnly; grossPaidFen: number; refundsFen: number; providerFeesFen: number; netReceiptsFen: number; unsettledFen: number; paidOrderCount: number; membershipSales: { planId: ID; count: number; grossFen: number }[]; reconciliationStatus: 'pending' | 'matched' | 'exceptions' }
export interface Reconciliation extends Versioned { date: DateOnly; channel: 'wechat_virtual'; status: 'pending' | 'matched' | 'exceptions' | 'failed'; mismatches: { orderNo: string; reason: string }[] }
export interface ModerationQueueQuery extends PageQuery { status?: ReviewStatus }
export interface SubscriptionConsent { purpose: 'live' | 'membership_expiry'; templateId: string; result: 'accept' | 'reject' | 'ban'; liveId?: ID }
export type SocketEvent =
  | { type: 'auth'; ticket: string; lastEventId?: ID }
  | { type: 'heartbeat'; clientTime: DateTime }
  | { type: 'ready'; eventId: ID; serverTime: DateTime }
  | { type: 'message.published'; eventId: ID; message: LiveMessage }
  | { type: 'message.deleted'; eventId: ID; messageId: ID }
  | { type: 'live.stats'; eventId: ID; viewers: number; likes: number }
  | { type: 'live.state'; eventId: ID; status: Live['status']; commentsEnabled: boolean }
  | { type: 'user.muted'; eventId: ID; until: DateTime | null }
  | { type: 'access.revoked'; eventId: ID; reason: string }
  | { type: 'resync_required'; eventId: ID }
  | { type: 'error'; code: string; message: string };
```

### 11.3 每个接口的请求与返回对应关系

```typescript
import * as T from './models';
// q = query string; b = JSON body; r = success envelope data.
// Absent q/b means no query/body. Path placeholders are required string IDs.
type R<O> = { r: O };
type Q<I, O> = { q: I; r: O };
type B<I, O> = { b: I; r: O };

export interface CoreApi {
  // Public / optional login: bootstrap, agreements, catalog, search, home, plans.
  'GET /bootstrap': Q<{ platform: T.Platform; clientVersion: string; sdkVersion: string }, T.Bootstrap>;
  'GET /agreements/{type}': Q<{ version?: string }, T.Agreement>;
  'POST /auth/wechat/login': B<T.WxLogin, T.LoginResult>;
  'POST /auth/sms': B<T.SmsSend, T.SmsReceipt>;
  'POST /auth/register': B<T.SmsProof & { password: string; agreements: T.AgreementRef[] }, T.LoginResult>;
  'POST /auth/sms/login': B<T.SmsProof & { agreements: T.AgreementRef[] }, T.LoginResult>;
  'POST /auth/password/login': B<{ phone: string; password: string; captchaTicket: string }, T.LoginResult>;
  'POST /auth/password/reset': B<T.SmsProof & { newPassword: string }, T.OK>;
  'POST /auth/refresh': B<{ refreshToken: string }, T.AuthTokens>;
  'POST /auth/logout': B<{ refreshToken: string }, T.OK>;
  'POST /auth/reauth': B<T.Reauth, T.ReauthResult>;
  'POST /auth/reauth/wechat': B<{ code: string; purpose: 'change_phone' | 'change_password' | 'delete_account' }, T.ReauthResult>;
  'PUT /me/phone/wechat': B<{ phoneCode: string; reauthToken?: string }, T.User>;
  'PUT /me/phone': B<T.SmsProof & { reauthToken: string }, T.User>;
  'PUT /me/password': B<{ newPassword: string; reauthToken: string }, T.OK>;
  'POST /me/wechat-binding': B<{ code: string; reauthToken: string }, T.User>;
  'GET /me': R<T.User>;
  'PATCH /me': B<{ nickname?: string; avatarAssetId?: T.ID }, T.User>;
  'GET /me/settings': R<T.Settings>;
  'PUT /me/settings': B<T.Settings, T.Settings>;
  'GET /me/deletion': R<T.Deletion | null>;
  'POST /me/deletion': B<{ reauthToken: string; reason: string; agreementVersion: string }, T.Deletion>;
  'DELETE /me/deletion': B<{ reauthToken: string }, T.Deletion>;
  'GET /home': R<T.Home>;
  'GET /notices': Q<T.PageQuery, T.Page<T.Notice>>;
  'GET /notices/{id}': R<T.Notice>;
  'PUT /me/notices/{id}/read': R<T.OK>;
  'GET /search': Q<T.SearchQuery, T.Page<T.SearchHit>>;
  'GET /search/hot': R<{ keywords: string[]; updatedAt: T.DateTime }>;
  'GET /categories': Q<{ kind: T.Category['kind'] }, T.Category[]>;
  'GET /teachers': Q<T.PageQuery & { q?: string }, T.Page<T.Teacher>>;
  'GET /teachers/{id}': R<T.Teacher>;
  'PUT /me/follows/{teacherId}': B<{ followed: boolean }, T.OK>;
  'GET /courses': Q<T.ContentQuery, T.Page<T.Course>>;
  'GET /courses/{id}': R<T.Course>;
  'GET /courses/{id}/lessons': Q<T.PageQuery, T.Page<T.Lesson>>;
  'GET /meditations': Q<T.ContentQuery, T.Page<T.Meditation>>;
  'GET /meditations/{id}': R<T.Meditation>;
  'GET /noises': Q<T.PageQuery & { categoryId?: T.ID }, T.Page<T.Noise>>;
  'GET /mix-presets': Q<T.PageQuery, T.Page<T.MixPreset>>;
  'POST /playbacks': B<T.PlaybackRequest, T.Playback>;
  'POST /playbacks/{id}/events': B<T.PlaybackEvent, T.PlaybackAck>;
  'POST /playbacks/{id}/renew': R<T.Playback>;
  'GET /me/learning': Q<T.PageQuery & { state?: 'in_progress' | 'completed' | 'purchased' }, T.Page<T.LearningItem>>;
  'GET /me/favorites': Q<T.PageQuery & { kind?: T.TargetKind }, T.Page<{ target: T.Target; title: string; coverUrl: string; savedAt: T.DateTime }>>;
  'PUT /me/favorites/{kind}/{id}': B<{ favorite: boolean }, T.OK>;
  'PUT /me/likes/{kind}/{id}': B<{ liked: boolean }, { liked: boolean; count: number }>;
  'GET /comments': Q<T.CommentQuery, T.Page<T.Comment>>;
  'POST /comments': B<T.CommentCreate, T.Comment>;
  'DELETE /comments/{id}': R<T.OK>;
  'PUT /comments/{id}/like': B<{ liked: boolean }, { liked: boolean; count: number }>;
  'POST /reports': B<{ targetType: T.Report['targetType']; targetId: T.ID; reason: string }, T.Report>;
  'POST /practice/sessions': B<T.PracticeStart, T.Practice>;
  'GET /practice/sessions/{id}': R<T.Practice>;
  'POST /practice/sessions/{id}/events': B<T.PracticeEvent, T.Practice>;
  'GET /me/practice/sessions': Q<T.PageQuery & { from?: T.DateOnly; to?: T.DateOnly }, T.Page<T.Practice>>;
  'GET /me/practice/stats': Q<{ from: T.DateOnly; to: T.DateOnly }, T.PracticeStats>;
  'POST /checkins': B<{ practiceSessionId: T.ID }, T.Checkin>;
  'GET /me/checkins': Q<{ month: string }, { dates: T.DateOnly[]; streakDays: number; checkedInToday: boolean }>;
  'GET /rewards': Q<T.PageQuery, T.Page<T.RewardEligibility>>;
  'POST /reward-claims': B<{ ruleId: T.ID; submissionId?: T.ID; addressId?: T.ID }, T.RewardClaim>;
  'GET /me/reward-claims': Q<T.PageQuery, T.Page<T.RewardClaim>>;
  'GET /reward-claims/{id}': R<T.RewardClaim>;
  'PUT /reward-claims/{id}/address': B<{ addressId: T.ID; version: number }, T.RewardClaim>;
  'POST /practice/submissions': B<{ practiceSessionId: T.ID; assetId: T.ID; text: string }, T.Submission>;
  'GET /me/practice/submissions': Q<T.PageQuery, T.Page<T.Submission>>;
  'GET /practice/submissions/{id}': R<T.Submission>;
  'POST /uploads': B<T.UploadRequest, T.UploadTicket>;
  'POST /uploads/{id}/complete': B<{ sha256: string }, T.Asset>;
  'GET /assets/{id}': R<T.Asset>;
  'GET /lives': Q<T.PageQuery & { status?: 'scheduled' | 'live' | 'ended' }, T.Page<T.Live>>;
  'GET /lives/{id}': R<T.Live>;
  'PUT /lives/{id}/reservation': B<{ reserved: boolean }, { reserved: boolean }>;
  'POST /me/subscription-consents': B<T.SubscriptionConsent, T.OK>;
  'POST /lives/{id}/join': R<T.LiveJoin>;
  'POST /lives/{id}/leave': B<{ participationId: T.ID }, T.OK>;
  'GET /lives/{id}/messages': Q<{ afterId?: T.ID; limit?: number }, { items: T.LiveMessage[]; nextAfterId: T.ID | null }>;
  'POST /lives/{id}/messages': B<{ clientMessageId: T.ID; kind: 'comment' | 'question' | 'reply'; text: string; replyToId?: T.ID }, T.LiveMessage>;
  'POST /lives/{id}/likes': B<{ eventId: T.ID; count: number }, { total: number }>;
  'GET /replays': Q<T.PageQuery & { teacherId?: T.ID }, T.Page<T.Replay>>;
  'GET /replays/{id}': R<T.Replay>;
  'GET /membership/plans': R<T.MembershipPlan[]>;
  'GET /me/membership': R<T.Membership>;
  'GET /me/entitlements': Q<T.PageQuery, T.Page<T.Entitlement>>;
  'POST /order-quotes': B<T.QuoteRequest, T.Quote>;
  'POST /orders': B<{ quoteId: T.ID; agreements: T.AgreementRef[] }, T.Order>;
  'GET /me/orders': Q<T.PageQuery & { kind?: T.Order['kind']; status?: T.OrderStatus; refundStatus?: T.RefundStatus }, T.Page<T.Order>>;
  'GET /orders/{id}': R<T.Order>;
  'POST /orders/{id}/cancel': B<T.Reason, T.Order>;
  'POST /orders/{id}/payments': B<{ platform: T.Platform; wxCode: string }, T.VirtualPayment>;
  'GET /payments/{id}': R<T.PaymentState>;
  'POST /orders/{id}/refunds': B<{ reason: string }, T.Refund>;
  'GET /refunds/{id}': R<T.Refund>;
  'GET /orders/{id}/shipment': R<T.Shipment>;
  'POST /orders/{id}/confirm-receipt': R<T.Order>;
  'GET /me/addresses': R<T.Address[]>;
  'POST /me/addresses': B<T.AddressInput, T.Address>;
  'PUT /me/addresses/{id}': B<T.AddressInput & { version: number }, T.Address>;
  'DELETE /me/addresses/{id}': R<T.OK>;
  'GET /regions': Q<{ parentCode?: string }, { code: string; name: string; level: 'province' | 'city' | 'district' }[]>;
  // Admin auth has independent token audience; every other /admin route is RBAC-protected.
  'POST /admin/auth/login': B<T.AdminLogin, T.AdminAuth>;
  'POST /admin/auth/refresh': B<{ refreshToken: string }, T.AuthTokens>;
  'POST /admin/auth/logout': B<{ refreshToken: string }, T.OK>;
  'GET /admin/me': R<T.AdminProfile>;
  'GET /admin/users': Q<T.UserQuery, T.Page<T.AdminUser>>;
  'GET /admin/users/{id}': R<T.AdminUser>;
  'PUT /admin/users/{id}/status': B<T.VersionReason & { status: 'active' | 'banned' }, T.AdminUser>;
  'PUT /admin/users/{id}/roles': B<{ roles: ('user' | 'teacher')[]; reason: string }, T.AdminUser>;
  'GET /admin/users/{id}/activity': Q<{ from: T.DateOnly; to: T.DateOnly; learningPage?: number; livePage?: number }, T.UserActivity>;
  'GET /admin/users/{id}/grants': Q<T.PageQuery, T.Page<T.Grant>>;
  'POST /admin/users/{id}/grants': B<T.GrantInput, T.Grant>;
  'POST /admin/grants/{id}/revoke': B<T.Reason, T.Grant>;
  'GET /admin/lives': Q<T.PageQuery & { status?: T.Live['status']; teacherId?: T.ID }, T.Page<T.Live>>;
  'POST /admin/lives': B<T.AdminLiveInput, T.Live>;
  'PUT /admin/lives/{id}': B<T.AdminLiveInput & { version: number }, T.Live>;
  'POST /admin/lives/{id}/actions': B<T.VersionReason & { action: 'start' | 'end' | 'force_offline' | 'cancel' }, T.Live>;
  'POST /admin/lives/{id}/stream-credentials': R<T.StreamCredential>;
  'PUT /admin/lives/{id}/comments': B<{ enabled: boolean; version: number }, T.Live>;
  'PUT /admin/lives/{id}/mutes/{userId}': B<{ until: T.DateTime | null; reason: string }, T.OK>;
  'DELETE /admin/lives/{id}/messages/{messageId}': B<T.Reason, T.OK>;
  'GET /admin/replays': Q<T.PageQuery & { status?: T.Replay['status'] }, T.Page<T.Replay>>;
  'PATCH /admin/replays/{id}': B<{ title?: string; chapters?: T.Replay['chapters']; status?: 'ready' | 'hidden'; version: number }, T.Replay>;
  'DELETE /admin/replays/{id}': B<T.VersionReason, T.OK>;
  'POST /admin/replays/{id}/retry': R<T.Job>;
  'POST /admin/replays/{id}/course': B<{ courseId: T.ID; title: string; sort: number; trialSeconds: number }, T.Job>;
  'GET /admin/config/home': R<T.HomeConfig>;
  'PUT /admin/config/home': B<T.HomeConfig, T.HomeConfig>;
  'GET /admin/config/practice': R<T.PracticeConfig>;
  'PUT /admin/config/practice': B<T.PracticeConfig, T.PracticeConfig>;
  'GET /admin/config/reminders': R<T.ReminderConfig>;
  'PUT /admin/config/reminders': B<T.ReminderConfig, T.ReminderConfig>;
  'GET /admin/config/moderation': R<T.ModerationConfig>;
  'PUT /admin/config/moderation': B<T.ModerationConfig, T.ModerationConfig>;
  'GET /admin/config/features': R<T.FeatureConfig>;
  'PUT /admin/config/features': B<T.FeatureConfig, T.FeatureConfig>;
  'GET /admin/agreements': R<T.Agreement[]>;
  'POST /admin/agreements': B<T.Agreement, T.Agreement>;
  'GET /admin/comments': Q<T.ModerationQueueQuery & { targetId?: T.ID }, T.Page<T.Comment>>;
  'POST /admin/comments/{id}/review': B<T.ReviewDecision, T.Comment>;
  'DELETE /admin/comments/{id}': B<T.Reason, T.OK>;
  'GET /admin/submissions': Q<T.ModerationQueueQuery, T.Page<T.Submission>>;
  'POST /admin/submissions/{id}/review': B<T.ReviewDecision, T.Submission>;
  'POST /admin/assets/{id}/preview': R<{ url: string; expiresAt: T.DateTime }>;
  'GET /admin/reports': Q<T.PageQuery & { status?: T.Report['status'] }, T.Page<T.Report>>;
  'POST /admin/reports/{id}/resolve': B<T.VersionReason & { decision: 'resolved' | 'rejected'; action: 'none' | 'delete_content' | 'ban_user' }, T.Report>;
  'GET /admin/reward-claims': Q<T.PageQuery & { status?: T.RewardClaim['status']; userId?: T.ID }, T.Page<T.RewardClaim>>;
  'GET /admin/orders': Q<T.FinanceQuery, T.Page<T.Order>>;
  'GET /admin/orders/{id}': R<T.Order>;
  'GET /admin/orders/{id}/shipping-address': R<T.AddressInput>;
  'POST /admin/orders/{id}/shipments': B<{ carrierCode: string; trackingNo: string; reason: string }, T.Shipment>;
  'PUT /admin/shipments/{id}': B<T.VersionReason & { carrierCode: string; trackingNo: string }, T.Shipment>;
  'GET /admin/refunds': Q<T.PageQuery & { status?: T.Refund['status']; from?: T.DateOnly; to?: T.DateOnly }, T.Page<T.Refund>>;
  'POST /admin/refunds/{id}/review': B<T.ReviewDecision, T.Refund>;
  'POST /admin/refunds/{id}/retry': B<T.Reason, T.Refund>;
  'GET /admin/finance/summary': Q<{ from: T.DateOnly; to: T.DateOnly }, T.FinanceSummary>;
  'POST /admin/exports': B<{ kind: 'orders' | 'finance' | 'members' | 'practice'; from: T.DateOnly; to: T.DateOnly }, T.Job>;
  'GET /admin/jobs/{id}': R<T.Job>;
  'GET /admin/reconciliations': Q<T.PageQuery & { from: T.DateOnly; to: T.DateOnly }, T.Page<T.Reconciliation>>;
  'POST /admin/reconciliations': B<{ date: T.DateOnly }, T.Reconciliation>;
  'GET /admin/metrics': Q<T.MetricQuery, T.Metrics>;
  'GET /admin/permissions': R<T.Permission[]>;
  'GET /admin/roles': R<T.Role[]>;
  'POST /admin/roles': B<T.RoleInput, T.Role>;
  'PUT /admin/roles/{id}': B<T.RoleInput & { version: number }, T.Role>;
  'DELETE /admin/roles/{id}': R<T.OK>;
  'GET /admin/accounts': Q<T.PageQuery, T.Page<T.AdminAccount>>;
  'POST /admin/accounts': B<T.AdminAccountInput & { initialPassword: string }, T.AdminAccount>;
  'PUT /admin/accounts/{id}': B<T.AdminAccountInput & { version: number }, T.AdminAccount>;
  'POST /admin/accounts/{id}/reset-password': B<{ newPassword: string; reason: string }, T.OK>;
  'GET /admin/audit-logs': Q<T.PageQuery & { from: T.DateOnly; to: T.DateOnly; operatorId?: T.ID; action?: string }, T.Page<T.AuditLog>>;
}

// Exact expansion for the 10 approved content resource types. PUT is full replacement.
type ListContent = { [K in keyof T.ContentMap as `GET /admin/content/${K}`]: Q<T.AdminContentQuery, T.Page<T.ContentMap[K]['output']>> };
type GetContent = { [K in keyof T.ContentMap as `GET /admin/content/${K}/{id}`]: R<T.ContentMap[K]['output']> };
type CreateContent = { [K in keyof T.ContentMap as `POST /admin/content/${K}`]: B<T.ContentMap[K]['input'], T.ContentMap[K]['output']> };
type UpdateContent = { [K in keyof T.ContentMap as `PUT /admin/content/${K}/{id}`]: B<T.ContentMap[K]['input'] & { version: number }, T.ContentMap[K]['output']> };
type DeleteContent = { [K in keyof T.ContentMap as `DELETE /admin/content/${K}/{id}`]: B<T.VersionReason, T.OK> };
export type Api = CoreApi & ListContent & GetContent & CreateContent & UpdateContent & DeleteContent;

export interface Envelope<D> { code: 'OK'; message: string; data: D; requestId: string; serverTime: T.DateTime }
export interface ErrorEnvelope { code: string; message: string; data: null; requestId: string; serverTime: T.DateTime; details?: { field: string; reason: string }[] }
```

## 12. 修改契约与复查

修改 `contracts/` 后，在本目录执行 `npm ci`、`npm run check`；它会检查类型、生成 JSON Schema、更新 OpenAPI 与本 README 附录。需要 Node.js 和 Python。不要只改生成的 README 附录。破坏性变更必须双方共同确认、升契约版本并同步前后端；新增可选字段一般可保持 `/v1`。

本交付只做契约结构检查，不代表支付、直播、备案或真机测试通过；这些需甲方真实账号与开发实现后验证。
