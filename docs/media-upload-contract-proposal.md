# 腾讯云上传契约适配（已共同确认并落地）

2026-11-10。用户已明确回复“已与前端确认，按字符串字典实施”。已修改 contracts 源文件，并生成 OpenAPI/README，开发契约升级为 0.2.0；API 路由仍为 /v1。实际实现及验收见 [媒资阶段记录](backend-stage4-2026-11-10.md)。

## 发现的问题

已确认供应商是腾讯云。现有 UploadTicket.formData 固定为 key/policy/signature/credential/securityToken 五个字段；腾讯云 COS POST Object 使用 q-sign-algorithm、q-ak、q-key-time、q-signature 等实际表单字段，使用临时凭证时还需要 x-cos-security-token。不能把五个通用别名原样发给 COS 并声称已适配。

## 建议的最终契约

保留路由、POST 上传方式和外层字段；将 formData 改为服务器返回的字符串字典，前端原样提交：

```typescript
export interface UploadTicket {
  assetId: ID;
  uploadUrl: string;
  method: 'POST';
  formData: Record<string, string>;
  fileField: 'file';
  expiresAt: DateTime;
  maxSizeBytes: number;
}
```

腾讯云返回示意（均为占位值）：

```json
{
  "key": "quarantine/asset-id/source",
  "policy": "BASE64_POLICY",
  "q-sign-algorithm": "sha1",
  "q-ak": "TEMPORARY_SECRET_ID",
  "q-key-time": "START;END",
  "q-signature": "SCOPED_SIGNATURE",
  "x-cos-security-token": "TEMPORARY_TOKEN",
  "Content-Type": "audio/mpeg",
  "success_action_status": "204"
}
```

这取消了原五字段必须存在的保证，已按共同确认结果实施。小程序与后台需升级上传调用，将后端返回的字典原样提交；其他 DTO 和路由保持原约定。

## 对前端的影响

小程序通过 wx.uploadFile 的 url/name/formData 使用后端票据，不解析签名、不猜供应商字段、不接收长期 SecretKey；后台同样按票据构造 FormData，文件字段最后添加。上传成功后调用 complete，随后轮询资产状态；HTTP 上传成功不能当成文件审核与转码完成。

## 服务端接入要求

桶保持私有，先上传到隔离对象键；票据绑定单一对象、大小、类型及最短必要有效期，采用项目受限的临时权限。服务端复核真实大小、哈希、类型、时长及处理结果后才能 ready；课程音视频不产生永久 publicUrl，只由通过权益检查的播放接口签发短效访问。外部回调需验证真实性并持久化幂等事件，不能凭客户端 complete 报告直接放行。

V3 原有媒资元数据现由 V4 上传记录和处理任务扩展，已接入 COS、真实文件校验、私有正式对象及后台短效预览。内容审核、转码、回调和用户播放权益仍是后续模块；技术校验 ready 不等于内容审核通过或允许公开。

官方依据：[COS POST Object](https://cloud.tencent.com/document/product/436/14690)（2026-11-10 核对）。
