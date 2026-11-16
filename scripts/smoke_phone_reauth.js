// Independent IDE probe for an isolated local cxj_test API. No phone bind or SMS send.
async function exerciseRecentIdentity() {
  const base = 'http://127.0.0.1:18083/api/v1'
  let session
  const call = (path, method, data) => new Promise((resolve,reject) => wx.request({
    url:base + path, method, data,
    header:{'Content-Type':'application/json', ...(session ? {Authorization:'Bearer '+session.accessToken} : {})},
    success:r=>r.statusCode===200 && r.data.code==='OK' ? resolve(r.data.data) : reject(Error('API_'+r.statusCode)),
    fail:()=>reject(Error('REQUEST_FAILED'))
  }))
  const loginCode = () => new Promise((resolve,reject) => wx.login({
    success:r=>r.code ? resolve(r.code) : reject(Error('EMPTY_CODE')), fail:()=>reject(Error('WX_LOGIN_FAILED'))
  }))
  try {
    const agreements = []
    for (const type of ['privacy','user']) {
      const {version} = await call('/agreements/'+type,'GET')
      agreements.push({type,version})
    }
    session = await call('/auth/wechat/login','POST',{code:await loginCode(), agreements})
    const before = await call('/me','GET')
    const proof = await call('/auth/reauth/wechat','POST',{code:await loginCode(), purpose:'change_phone'})
    const after = await call('/me','GET')
    return {sameAccount:before.id===after.id, phoneUnchanged:before.phoneMasked===after.phoneMasked,
      shortProof:typeof proof.reauthToken==='string' && proof.reauthToken.length>=40 && proof.expiresIn===300}
  } finally {
    if (session) await call('/auth/logout','POST',{refreshToken:session.refreshToken})
  }
}
