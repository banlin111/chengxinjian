# 讲师基础阶段实施与验收

> 本页是讲师阶段历史快照，当前范围和测试见 [媒资阶段验收](backend-stage4-2026-11-10.md)。

日期：2026-11-10（Asia/Shanghai）。在原分支 codex/backend-foundation、HEAD 0120d62 的本地修改上继续实施；保留所有原有改动，没有重新搭建、reset、clean、提交或推送。

## 已新增的真实后端能力

8 个方法/路径组合（均加 /api/v1）：

- GET /teachers、GET /teachers/{id}：已发布讲师分页、名称搜索、详情；登录用户返回本人关注状态，匿名为 false。公开字段不包含 userId、avatarAssetId。
- PUT /me/follows/{teacherId}：仅本人关注或取消关注，重复提交不生成重复关系；不可关注草稿或归档资料。
- GET/POST /admin/content/teachers、GET/PUT/DELETE /admin/content/teachers/{id}：管理列表、创建、详情、修改、归档。创建使用 UUID 幂等键和原始响应快照，更新使用 version，归档保留行、用户关联和关注历史，重复归档不重复审计。

当前累计实现 25/220 个业务接口；还有 195 个待逐步实现，不能把契约操作数当已完成数。

新增 V3（3 张表）：content_teacher、teacher_follow、media_asset；admin_account.teacher_id 新增外键。业务表总数 20，另有 Flyway 历史表。V1/V2 未改写。关联用户必须存在且 active，一名用户只能关联一个讲师资料；不自动合并账号或赋予管理员身份。用户 roles 根据未归档讲师关系实时读取，归档后 teacher 角色移除，历史关系保留。

content:read/write 与实时权限校验继续生效。own_content 的列表和读写只限自身 teacherId，禁止创建全局资料、读写他人、重新绑定普通用户；没有有效 teacherId 时拒绝全局访问。管理员令牌不用于用户端关注，普通用户/teacher 用户角色也不会取得后台权限。

头像引用必须是已处理完成的 avatar 图片，校验类型、大小、HTTPS 与归属，拒绝不存在、处理中、错误用途或越权资产。全局管理员可先分配头像，讲师可继续修改自身资料且保留已分配头像。media_asset 仅为真实上传管线准备元数据与外键，尚无上传票据、内容验证/审核/转码、COS 或播放授权实现；测试地址为隔离夹具，没有伪装云端资源。允许头像为空沿用现有 DTO 的 nullable 约定，完整头像功能未从范围删除。

## 契约生成缺陷及修复

发现原生成器在交叉类型中丢字段，例如 PUT /admin/content/teachers/{id} 的 body 只保留 version，GET /teachers 的查询漏掉 page/pageSize，组合后台返回漏掉 Teacher 基础字段。

scripts/generate_schema.cjs 用同一份 contracts 的无引用展开结果修复引用树，保留 158 个命名模型；再使用生成器实际使用的 TypeScript 编译器独立核对所有 220 操作内 770 个对象字段和必填性。接口 DTO 和路由语义未变，OpenAPI/README 由工具重新生成。不是直接手修生成文件，也不是因为原错误 OpenAPI 漏字段就删除业务字段。

COS POST Object 需要的真实表单字段与原 UploadTicket 五个通用字段不一致，已形成待双方确认的具体修改提议；UploadTicket 尚未改变，不输出无效的假上传票据。

## 当日验证结果

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| H2 全量回归 | 55 项通过，0 失败/错误 | server/target/stage3-h2.log |
| MySQL 8.4.11 全量回归 | 55 项通过，0 失败/错误；V3 升级成功 | server/target/stage3-mysql.log |
| 契约检查 | 220 操作、158 schema；770 对象与编译器一致 | server/target/stage3-contracts.log |
| 打包 jar + 原生 MySQL + HTTP | PASS，管理员及分类原流程 + 讲师创建/重放/发布/公开读取/归档 | server/target/stage3-http-smoke.log、server/target/http-smoke.log |

12 项新增集成测试覆盖公开/内部字段隔离、发布/草稿/归档、分页与字面量搜索、幂等与并发创建、并发版本冲突、own_content 越权及禁止重新绑定、用户唯一关联与角色实时变化、关注幂等和用户隔离、头像引用状态/用途/归属、权限变更、严格 JSON 与必填可空字段、修复后 OpenAPI 请求响应字段。

HTTP 冒烟使用 cxj_dev，等待 readiness=UP 后测试，结束时将测试讲师归档，保留审计。没有用 Mock 替代打包后的 HTTP 验证。未将管理员 local 登录当成生产验证码已接入。

## 下一步与当前需要配合

下一步顺序：确认 COS 上传字段契约 → 接入受限私有媒资上传和处理 → 课程/课节 → 会员/购买权益与短效播放授权。服务端领域开发可继续推进；第三方真实联调要待项目云授权落实。微信阶段的真实验收结论见前一阶段报告，本轮不将历史微信结果冒充新的真机验收。

用户当前对腾讯云子账号/协作者状态不清楚，按 docs/cloud-access-check-2026-11-10.md 核实即可；尚未要求购买服务器、域名或其他套餐，未开通或购买任何云资源。正式协议仍为待甲方确认草案。云端部署、真机、媒资及付费播放均未完成，不因已有账号或元数据表宣称完成。

最新资源反馈：用户进一步确认甲方当前只有腾讯云账号。已生成仅限开发桶的授权 JSON（本地忽略文件）和可转发步骤，见 docs/cloud-media-request-2026-11-10.md；尚未取得子用户密钥，也未开通或测试 COS。
