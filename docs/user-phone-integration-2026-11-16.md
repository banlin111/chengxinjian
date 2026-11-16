# 普通用户管理与手机号绑定、换绑交接

2026-11-16，协作分支 codex/backend-foundation。Flyway 新增 V9，保留既有账号和权益。手机号归属由 app_user.id 和唯一手机号约束确定。当前支持中国大陆 11 位手机号。

## 前端接入

以下路径均以 `/api/v1` 开头。用户以本人的 Bearer 会话调用，登录响应给出 `phoneBindingRequired`，绑定成功返回 User，展示 `phoneMasked`。同事分支 codex/frontend 的 223b2ea 中，`apps/mobile/src/pages/settings/index.vue` 第 64 行“账号资料与隐私”目前只显示占位提示。

| 用户操作 | 请求与字段 |
| --- | --- |
| 首次绑定微信授权号码 | 在用户点击授权后，从 getPhoneNumber 回调取 code，调用 `PUT /me/phone/wechat`，传 `phoneCode`。授权取消时留在原页面。 |
| 换绑前确认当前微信身份 | 保持原会话，调用 wx.login 取得新的 code，`POST /auth/reauth/wechat` 传 `code, purpose: "change_phone"`。返回 300 秒有效的一次性 reauthToken。已有绑定后调用微信手机号接口还须传该 token。 |
| 用原号码确认当前身份 | 小程序验证码插件成功后，`POST /auth/sms` 传 `phone, purpose: "reauth", captchaTicket`。手机号须等于原绑定。收到验证码后 `POST /auth/reauth` 传 `challengeId, code, purpose: "change_phone"`。 |
| 用短信验证新号码 | `POST /auth/sms` 传新 `phone, purpose: "change_phone", captchaTicket`。收到后 `PUT /me/phone` 传 `challengeId, code, reauthToken`。首次通过短信绑定也须先复核当前身份。 |

微信身份复核使用新的登录 code。手机号授权使用单独的 phoneCode。短信人机验证使用小程序插件 ticket，由后端调用 DescribeCaptchaMiniResult 校验。三者用途独立。前端只需公开验证码应用 ID。验证码、登录 code、授权码、token 只在本次请求流程中使用，不放日志、页面数据或本地存储。

`409 PHONE_ALREADY_BOUND` 时保留原登录和资料，提示“此手机号已绑定其他账号”，允许用户重新选择号码。再次尝试须获取新凭证。`403 REAUTH_REQUIRED` 回到身份复核，`400 PHONE_PROOF_INVALID` 重新获取验证码，`429 RATE_LIMITED` 等待冷却，`503 PROVIDER_UNAVAILABLE` 显示服务暂不可用。发送回执包含 challengeId、60 秒重发间隔和 300 秒有效期。每张短信凭证最多尝试五次，绑定当前用户、会话、用途及手机版本。短信发送失败或结果未知的凭证不可用。

用户管理使用管理员 Bearer 会话、全局数据范围及专门的权限。以前仅具备人工权益发放权限的角色，需由有 RBAC 权限的管理员按职责分配以下新增权限。

| 接口 | 条件和行为 |
| --- | --- |
| `GET /admin/users` | user_directory:read。按完整手机号精确筛选、注册日期（上海自然日）、状态、当前会员有效性及分页查询。返回脱敏号码。 |
| `GET /admin/users/{id}` | user_directory:read。包含账户状态、普通角色、当前会员、注册和最近登录时间、版本。 |
| `PUT /admin/users/{id}/status` | user_directory:write。传 status（active / banned）、version 和 reason。禁用撤销所有用户会话，启用后须重新登录。 |
| `PUT /admin/users/{id}/roles` | user_directory:write。传 roles、reason，建议带 version。保留 user，可附加 teacher。已有未归档讲师资料时，移除 teacher 返回 409。 |

状态和角色变更记录原因。手机号修改只更新当前普通用户的号码及版本，并记录操作。接口响应和审计不包含原始号码。数据库含可查询的完整号码，备份须按个人信息保护，代理日志避免记录手机号筛选参数。练习累计和连续打卡字段暂为 0；管理端活动明细、短信注册/登录、密码、找回及注销尚待后续实现。

## 腾讯云与微信侧办理

1. 以实际运营主体登录短信控制台，在概览先核对已有国内短信应用、可用签名、模板、套餐余额及条数。首次开通时审核服务协议后点“开始接入”。进入“概览 → 新手配置指引 → 国内短信”，依次点“创建资质”“创建签名”。使用可证明归属的主体或品牌，申请说明写“小程序用户绑定和更换手机号的身份验证”。记录签名审核及运营商实名报备状态。[腾讯云国内短信操作指南](https://cloud.tencent.com/document/product/382/37745)
2. 在同一指引点“创建模板”，选“验证码短信”，使用一个变量。可提交“您的验证码为{1}，5分钟内有效，请勿向他人泄露。” 交付短信应用的 SDKAppID、批准的签名原文、已通过的模板 ID 及可用额度。实际发送采用 SendSms，运营商回执和实际收件另行验证。[短信控制台](https://console.cloud.tencent.com/smsv2) · [SendSms 接口](https://cloud.tencent.com/document/api/382/55981)
3. 验证码控制台“验证管理 → 创建验证码”，为微信小程序插件配置应用，名称可填“澄心涧手机号验证”。记录 CaptchaAppId 和 AppSecretKey。微信小程序管理员登录公众平台，进入“设置 → 第三方设置 → 添加插件”，搜索“腾讯验证码”并添加。将公开的 CaptchaAppId 和插件状态给前端；AppSecretKey 仅交后端。[创建验证码](https://cloud.tencent.com/document/product/1110/36839) · [微信小程序与 uni-app 插件接入](https://cloud.tencent.com/document/product/1110/49319)
4. 在腾讯云 CAM 为后端设置受限的 API 身份，短信仅需 sms:SendSms，人机验证仅需 captcha:DescribeCaptchaMiniResult。核实已生效后在后端私密环境填写参数。开发配置使用仓库内忽略且本人持有、权限 0600 的 .env.phone.local，运行 local_backend.py run 时加 --phone。隔离部署使用既有受控环境文件。[CAM 控制台](https://console.cloud.tencent.com/cam) · [小程序票据验证](https://cloud.tencent.com/document/product/1110/48499)

后端短信字段为 SMS_SDK_APP_ID、SMS_SIGN_NAME、SMS_TEMPLATE_ID、SMS_REGION、SMS_SECRET_ID、SMS_SECRET_KEY。人机验证字段为 SMS_CAPTCHA_APP_ID、SMS_CAPTCHA_APP_SECRET、SMS_CAPTCHA_SECRET_ID、SMS_CAPTCHA_SECRET_KEY。私钥及密钥请通过受控环境配置。实际小程序微信手机号能力由甲方在对应 AppID 下确认，用户须操作授权按钮才能取得 phoneCode。[微信手机号授权](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html)

## 复验与待验收

完整 Java 套件分别使用 H2 和 MySQL cxj_test 运行。冲突用例比较两位用户独立的人工会员权益，并核对原号码、用户 ID、刷新与重新登录。并发用例让两人同时申请同一未占用号码，核对成功者唯一且失败者保留原号码。另以仅一人有人工单课权益的课程校验冲突前后双方申请播放的结果。自动化用例在供应商边界使用测试替身。

真实微信身份复核使用独立探针、微信开发者工具和隔离 cxj_test。先按交接配置 WECHAT_MCP_PYTHON、WECHAT_MCP_WRAPPER，在仓库根执行 `python3 scripts/smoke_phone_reauth.py`。需独占本机 18083、IDE 自动化端口及测试库，使用已打包的 JAR。探针通过两次 wx.login 验证同账号复核、凭证期限、原绑定信息和退出，只记录布尔结论与 JAR 摘要。

真实短信、人机验证插件和微信手机号授权仍需可用配置、正式前端、两组受控测试微信账号和手机号配合。端到端验收顺序为两人分别绑定并留存各自权益 → A 尝试换到 B 的号，核对 409 和双方原账号/权益 → A 换到可用的新号码（可用第三个受控号码）→ 复登录并验证原课节播放权限。还需在公网 HTTPS 下用真机执行，正式协议批准后再验收正式注册。
