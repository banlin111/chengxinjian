# 媒资上传、校验与真实联调

日期：2026-11-10。本阶段在已有工程上新增 4 个业务接口，累计 29/220；保留此前所有本地修改。没有开发正式前端页面，没有上传小程序代码、提审、发布或购买服务器。

## 接口与契约

| 方法 | 路径（加 /api/v1） | 实际行为 |
| --- | --- | --- |
| POST | /uploads | 申请私有隔离区的限定上传票据；UUID 幂等键、请求指纹、并发唯一预留、原始票据快照 |
| POST | /uploads/{id}/complete | 核对归属和声明摘要，事务写入持久化校验任务及审计；幂等重放保留最初响应 |
| GET | /assets/{id} | 授权范围内的资产状态；不返回对象键、所有者、云密钥或永久媒体地址 |
| POST | /admin/assets/{id}/preview | 管理权限和数据范围校验后，为 ready 的后台资产签发 120 秒私有预览地址 |

用户已确认与前端共同采用 formData 字符串字典，契约升至 0.2.0（路由仍为 /v1），源文件、OpenAPI 和 README 已同步。前端将字典原样提交，file 最后添加，不能继续依赖旧五个字段。生成器另外修复了映射字典被错误关闭的问题，并由 TypeScript 编译器核对字典值类型；本轮仍核对 220 操作、770 对象，生成 159 个 schema。另修复了泛型组件名含非法标点的问题，使用确定且防冲突的 OpenAPI 名称；普通 DTO 名称与业务 JSON 不变。只读后台预览不再被错误要求创建业务的幂等头。

普通用户仅可为本人上传 avatar/practice_recording；后台内容写入需要 content:write，读取需要 content:read。own_content 需有效讲师绑定且只能操作本人资产；全局内容管理员也不能借此读取普通用户私有录制。管理员凭据仅额外允许以上公共媒资路由，不能因此访问 /me 等用户接口。

## 实际文件处理

- 新增 V4，不改写 V1/V2/V3：上传幂等预留、短租约与处理任务，以及隔离对象、到期和错误元数据。外部 STS/COS/解码调用均不占用数据库事务。
- 使用真实 STS GetFederationToken，将临时权限缩至一个隔离对象的 PostObject；客户端只得到临时 ID、token、限定表单签名，不得到任何 SecretKey 或永久访问 ID。票据 10 分钟，签名绑定精确对象、MIME、字节数、private ACL、加密和过期时间。
- 服务端从 COS 有界下载，核对真实元数据、实际长度及 SHA-256。PNG/JPEG/WebP 检查格式与完整解码；音视频检查容器、真实音视频流、完整解码及秒数。FFmpeg 只允许本地 file/pipe，限制线程、分配、像素和运行时间，不继承微信、数据库或云密钥环境变量。
- 验证通过后将已校验本地字节写入 `validated/资产ID/source`，再以事务标记 ready。客户端临时票据只能操作原隔离对象，后续覆盖隔离文件不会改变正式对象。技术 ready 的 publicUrl 仍为 null；内容审核、公开图片投放和用户播放权益另行完成。
- 任务使用数据库租约抢占，恢复过期任务，避免多实例重复提交结果；暂时性供应商故障最多自动重试 5 次。耗尽后 job.state=failed、资产仍为 processing，保留 last_error；恢复供应商后可用新的幂等键再次 complete 重试，已测试。不能把 processing 当成功；任务管理界面尚未实现。
- 文件本身不合法时标记 rejected；临时本地文件随处理清理。票据过期 15 分钟后清理隔离对象，保留历史资产及正式对象。尚无生产部署、内容审核服务、转码队列、通用 outbox 或付费播放授权实现。

当前支持 PNG/JPEG/WebP 图片、MP3/M4A/WAV 音频及 MP4/WebM 视频（按用途限制）。图片上限 10 MiB、16MP；媒体默认 512 MiB、4 小时，解码默认 120 秒。媒体字节数、时长及解码超时可用环境变量调整，这些是开发期技术上限，不能替代甲方未确认的录制奖励规则。任务按实例串行处理，生产容量须按实际媒体和压测配置。

## 验证与证据

- H2 / 真实 MySQL 8.4.11：各 83 项通过、无跳过；私有配置加载器 5 项通过；OpenAPI 3.0 规范校验通过。
- 包含用户与管理员隔离、权限和数据范围、幂等并发、真实字节哈希/类型、处理任务重试、租约恢复、竞争 worker、审计失败整体回滚、过期清理、短效预览、严格 JSON 和响应契约。
- 打包 Java 服务 + 真实 MySQL 隔离库 cxj_test + 正式 COS：60 个检查通过。实际上传合成 PNG、JPEG、WebP、WAV、MP4；验证了路径/大小/公开 ACL 篡改拒绝、匿名读取拒绝、隔离区覆盖不改变正式对象、错误哈希/伪造图片/不可解码视频拒绝、幂等原始响应及退出。
- 微信开发者工具：技能/MCP 0.9.21，已登录、start verified、compile 无错误，page_data 核对 pages/login/index；真实 wx.login → 后端用户会话 → wx.uploadFile → COS → complete → ready，保持私有，不使用 Mock。临时后端 18083 连接 cxj_test，完成后退出并停止，合成本地文件和云对象均清理。
- HTTP 测试使用合成小文件，测试云对象已删除并逐项 HEAD 确认 404，隔离库相应元数据标为 TEST_FIXTURE_CLEANED；微信测试元数据随后由隔离库自动测试清理。不会清理开发或生产业务数据。

证据在忽略目录：stage4-h2.log、stage4-mysql.log、stage4-contract.log、stage4-openapi-validation.log、media-cos-smoke/result.json、stage4-wechat-{status,start,compile,upload,page,logout}.json。日志及报告不保存 code、令牌或签名。默认开发工具关闭了本地合法域名检查；这是真实开发工具联调，尚不是微信真机或正式公网验收。

## 启动与复查

```sh
./scripts/backend.sh test
python3 scripts/local_backend.py test --database-env ../.tools/mysql-test.env
python3 -m unittest discover -s scripts -p test_local_backend.py
../.tools/cos-runtime/bin/python -m openapi_spec_validator openapi.json
../.tools/cos-runtime/bin/python scripts/smoke_media_cos.py --run-real-cloud-upload
SERVER_PORT=18080 python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat --cloud
```

真实 COS 脚本只接受本机 cxj_test，并只删除它创建的合成对象；不要与隔离库集成测试同时运行。额外运行依赖见 scripts/requirements-media-smoke.txt。本机 FFmpeg 来自 imageio-ffmpeg 0.6.0；Java COS 5.6.283、腾讯云 STS 3.2.6、WebP 解码 3.15.3。Dockerfile 补充 FFmpeg 安装，当前未在本机 Docker 执行，不宣称容器验收通过。

`.env.cloud.local` 只由 --cloud 显式加载，0600、本人所有、忽略 Git，普通自动测试拒绝该选项。FFMPEG_PATH 指向实际可执行文件；缺解码器或云配置时拒绝相应能力，不伪造成功。后端仍运行本机，存储为甲方腾讯云开发桶。尚无正式后台网页来源，未设置通配 CORS；实际后台联调时按其确切来源添加。当前无需甲方重发密钥或购买其他云产品。

官方依据：[COS POST Object](https://cloud.tencent.com/document/product/436/14690)、[STS GetFederationToken](https://cloud.tencent.com/document/product/1312/48195)、[FFmpeg 命令参考](https://ffmpeg.org/ffmpeg.html)。

测试修复：真实联调在隔离库保留了多个测试管理员，暴露原密码哈希断言错误假设只有一条管理员记录；已将断言限定到 integration-admin，而不删除其他账号或放宽哈希要求。
