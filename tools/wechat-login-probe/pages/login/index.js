let BASE = 'http://127.0.0.1:18080/api/v1'
// Tokens and wx.login codes stay in closure memory, never page data, storage or logs.
let tokens = null
let agreements = null
function request(path, method = 'GET', data, authenticated = false, extraHeaders = {}) {
  return new Promise((resolve, reject) => wx.request({
    url: BASE + path, method, data,
    header: { 'Content-Type': 'application/json', ...extraHeaders, ...(authenticated && tokens ? { Authorization: 'Bearer ' + tokens.accessToken } : {}) },
    success(r) { if (r.statusCode === 200 && r.data.code === 'OK') resolve(r.data.data)
      else reject(new Error((r.data && r.data.code) || 'HTTP_' + r.statusCode)) },
    fail() { reject(new Error('NETWORK_UNAVAILABLE')) }
  }))
}
Page({
  data: { busy: false, loggedIn: false, consented: false, backendPort: 18080, mediaStatus: '', mediaAssetId: '', status: '请先确认后端 readiness=UP，并在开发者工具本地设置中关闭域名校验。' },
  setBackendPort(port) {
    if (tokens || ![18080, 18083].includes(port)) throw new Error('LOCAL_TEST_PORT_REQUIRED')
    BASE = 'http://127.0.0.1:' + port + '/api/v1'
    agreements = null
    this.setData({ backendPort: port, consented: false })
  },
  consentChanged(e) { this.setData({ consented: !!agreements && e.detail.value.includes('agree') }) },
  async run(fn) {
    if (this.data.busy) return
    this.setData({ busy: true })
    try { await fn() } catch (e) { this.setData({ status: '失败：' + e.message }) }
    finally { this.setData({ busy: false }) }
  },
  readAgreements() { return this.run(async () => {
    const privacy = await request('/agreements/privacy')
    const user = await request('/agreements/user')
    agreements = [privacy, user].map(a => ({ type: a.type, version: a.version }))
    this.setData({ consented: false, status: [privacy, user].map(a => a.title + '\n版本：' + a.version + '\n' + a.content).join('\n\n') })
  }) },
  login() { return this.run(async () => {
    if (!this.data.consented || !agreements) throw new Error('AGREEMENT_REQUIRED')
    const code = await new Promise((resolve, reject) => wx.login({ success(r) { r.code ? resolve(r.code) : reject(new Error('WX_LOGIN_NO_CODE')) }, fail() { reject(new Error('WX_LOGIN_FAILED')) } }))
    const result = await request('/auth/wechat/login', 'POST', { code, agreements })
    tokens = { accessToken: result.accessToken, refreshToken: result.refreshToken }
    this.setData({ loggedIn: true, status: '真实登录成功；新用户：' + result.isNewUser + '；手机号待绑定：' + result.phoneBindingRequired })
  }) },
  readMe() { return this.run(async () => {
    const me = await request('/me', 'GET', undefined, true)
    const settings = await request('/me/settings', 'GET', undefined, true)
    this.setData({ status: '用户读取成功；状态：' + me.status + '；主题：' + settings.theme + '；设置版本：' + settings.version })
  }) },
  refresh() { return this.run(async () => {
    if (!tokens) throw new Error('UNAUTHENTICATED')
    tokens = await request('/auth/refresh', 'POST', { refreshToken: tokens.refreshToken })
    this.setData({ status: '刷新成功，访问与刷新令牌已同时轮换' })
  }) },
  uploadFixture() { return this.run(async () => {
    if (!tokens) throw new Error('UNAUTHENTICATED')
    const uuid = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const n = Math.floor(Math.random() * 16)
      return (c === 'x' ? n : (n & 3) | 8).toString(16)
    })
    // Synthetic fixture only; no camera, photo library, user files or permanent cloud credentials.
    const fixture = {"base64": "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAD0lEQVR4nGNgaGgAIQgFABoOBAH77Fo+AAAAAElFTkSuQmCC", "size": 72, "sha256": "77a53bb81fcebd3d0c37d9eb523ad227e465598259c4a230b589a89bbcc89b3d"}
    const path = wx.env.USER_DATA_PATH + '/cxj-synthetic-upload.png'
    const fs = wx.getFileSystemManager()
    try {
      await new Promise((resolve, reject) => fs.writeFile({ filePath: path, data: fixture.base64, encoding: 'base64', success: resolve, fail: () => reject(new Error('FIXTURE_WRITE_FAILED')) }))
      const ticket = await request('/uploads', 'POST', { purpose: 'avatar', filename: 'synthetic.png', mimeType: 'image/png', sizeBytes: fixture.size, sha256: fixture.sha256 }, true, { 'Idempotency-Key': uuid() })
      const result = await new Promise((resolve, reject) => wx.uploadFile({ url: ticket.uploadUrl, filePath: path, name: ticket.fileField, formData: ticket.formData, success: resolve, fail: () => reject(new Error('WX_UPLOAD_FAILED')) }))
      if (result.statusCode !== 204) throw new Error('COS_UPLOAD_HTTP_' + result.statusCode)
      await request('/uploads/' + ticket.assetId + '/complete', 'POST', { sha256: fixture.sha256 }, true, { 'Idempotency-Key': uuid() })
      this.setData({ mediaAssetId: ticket.assetId, mediaStatus: 'processing', status: '真实 wx.uploadFile 已完成，等待服务端校验' })
      for (let i = 0; i < 60; i++) {
        const asset = await request('/assets/' + ticket.assetId, 'GET', undefined, true)
        if (asset.status === 'ready' || asset.status === 'rejected') {
          this.setData({ mediaStatus: asset.status, status: '真实 wx.uploadFile → 服务端校验：' + asset.status + '；保持私有：' + (asset.publicUrl === null) })
          return
        }
        await new Promise(resolve => setTimeout(resolve, 500))
      }
      throw new Error('VALIDATION_STILL_PROCESSING')
    } finally { fs.unlink({ filePath: path, fail() {} }) }
  }) },
  logout() { return this.run(async () => {
    if (!tokens) throw new Error('UNAUTHENTICATED')
    await request('/auth/logout', 'POST', { refreshToken: tokens.refreshToken }, true)
    tokens = null
    this.setData({ loggedIn: false, status: '退出成功' })
  }) }
})
