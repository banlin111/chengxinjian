# 开发存储与受限凭据实测记录

> 此页记录资源创建时的只读验证。随后已完成后端受控接入、真实上传及开发工具 wx.uploadFile 联调，最新结果见 [媒资阶段验收](backend-stage4-2026-11-10.md)。

2026-11-10。在用户登录腾讯云并明确确认创建、保存受限密钥后，由开发方完成以下操作。实际账号标识、完整桶名及密钥均留在本机忽略文件，不写入公开仓库。

## 已实际完成

- 创建澄心涧开发用 COS 桶，地域上海 `ap-shanghai`，标准存储、单 AZ、私有读写，启用 SSE-COS AES256 服务端加密。
- 创建自定义策略 `cxj-media-dev`，资源仅限定此开发桶及其对象：HeadBucket、GetBucket、GetBucketCORS、PutBucketCORS、PostObject、PutObject、HeadObject、GetObject、DeleteObject。策略资源使用当前控制台验证通过的 `qcs::cos:地域:uid/云APPID:桶名` 格式。
- 创建子用户 `cxj-media-dev`，访问方式仅为编程访问。创建后进入用户详情，核对仅关联上述一项自定义策略，没有财务、服务器、创建/删除桶、修改公开权限或全账号管理权限。
- 下载该子用户凭据并安全导入 `.env.cloud.local`，原始 CSV 移至 `server/.local/cloud-bootstrap/private-credentials.csv`；两者均为本人所有、0600、Git 忽略且未跟踪。没有使用主账号 API 密钥。
- 使用该子用户密钥、官方 COS Python SDK 1.9.44，通过 HTTPS 真实执行只读检查：HeadBucket 成功返回 HTTP 200；GetBucketCORS 返回 HTTP 404 / NoSuchCORSConfiguration，表示尚未配置跨域规则。

检查没有上传、修改或删除云端文件，没有充值、购买资源包、开通服务器或部署后端。权限范围依据实际策略与用户详情核对；尚未逐项执行写入或跨资源拒绝测试。云存储请求属于实际云端使用，最终费用以腾讯云账单为准，不能把未购买资源包等同于所有调用免费。

## 本地复查

`scripts/check_cos_access.py` 只执行 HeadBucket 和 GetBucketCORS。它复用私有配置加载器，拒绝不安全的文件权限，不打印原始响应、签名、SecretId、SecretKey 或异常报文。独立 SDK 环境在仓库外 `.tools/cos-runtime`，使用已有受管 Python 3.10 / OpenSSL，不依赖系统 Python 的 LibreSSL。

在实际仓库根目录执行：

```sh
../.tools/cos-runtime/bin/python scripts/check_cos_access.py
```

本轮安全截图位于忽略目录 `server/target/cloud-setup-evidence/`：bucket-created.png、policy-created.png、user-review.png、subuser-created.png。只读检查结果位于同目录 readonly-check.json。不保存密钥显示页截图。

## 与后端实现的边界

`.env.cloud.local` 不由 Spring Boot 隐式读取，现由 `--cloud` 显式加载，已接入上传与服务端校验，累计 29 个接口。V4 扩展了 V3 元数据，但内容审核、转码和用户播放鉴权尚未完成。上传契约已经双方确认，见 [上传契约适配](media-upload-contract-proposal.md)。在确认具体前端来源前不设置通配跨域。

此开发桶和身份不代表生产部署完成。后端目前仍运行在本机，正式服务器、域名、HTTPS、生产独立身份及上线验收后续推进。当前无需甲方再次发送密钥。

官方依据：[Python SDK 存储桶操作](https://cloud.tencent.com/document/product/436/35150)、[COS API 授权策略](https://cloud.tencent.com/document/product/436/31923)（2026-11-10 核对）。
