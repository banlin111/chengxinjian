# 后端微信登录联调工程

仅供本地联调，正式小程序和管理后台仍由前端同事开发。无上传、提审、发布授权。

1. 仓库根目录运行 `python3 scripts/prepare_wechat_probe.py`，从受保护的 `.env.wechat.local` 生成被 Git 忽略的 `project.config.json`，不打印 AppID 或密钥。工程内只使用 AppID，AppSecret 只在服务端进程。
2. `SERVER_PORT=18080 python3 scripts/local_backend.py run --database-env ../.tools/backend-local.env --wechat --cloud`，等 `/actuator/health/readiness` 返回 UP。上传测试另外要求受保护云配置和已配置的 FFmpeg。
3. 微信开发者工具打开本目录；开启服务端口。project.config.json 为本地联调关闭域名校验，不能把此设置当公网 HTTPS 验收。
4. 读取测试协议，阅读后主动勾选，然后点击真实登录；依次测试读取信息、刷新、退出。失败页面仅显示业务码，不显示 code/token/session_key。
5. 测试协议不等于甲方已批准协议；草案在 docs/agreements，非 local 禁用测试版本。

媒资验证按钮只生成一张 2×2 合成 PNG，通过真实 wx.uploadFile 将后端 formData 字典原样提交至 COS，再 complete 并轮询 ready/rejected。不访问相册或摄像头，不将令牌或上传票据写入 page data/storage/log，不扩展正式前端页面。文件仍保持私有；ready 是文件技术校验，不代表内容审核通过。一次性 code、临时签名和真实云服务均不使用 Mock。

自动化隔离测试可在登录前调用 `setBackendPort(18083)`，仅允许本机 18080/18083 两个端口；重新编译恢复默认 18080。隔离服务必须连接 cxj_test，不能将该测试模式指向生产。

真实登录 code 一次性使用；网络或数据库提交失败后应重新 wx.login，不重放旧 code。工具 mock 仅用于异常测试，不能替代真实微信验收。
