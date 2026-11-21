// Independent WeChat Developer Tools client. Only fixture IDs and checked outcomes leave this page.
async function exerciseRecording(mode, fixture) {
  const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const n = Math.floor(Math.random() * 16)
    return (c === 'x' ? n : (n & 3) | 8).toString(16)
  })
  let auth
  const request = opts => new Promise((resolve, reject) =>
    wx.request({...opts, success:resolve, fail:()=>reject(Error('WX_REQUEST'))}))
  const call = async (path, method='GET', data, k, expected=200) => {
    const r = await request({url:'http://127.0.0.1:18082/api/v1'+path, method, data,
      header:{...(auth?{Authorization:'Bearer '+auth.accessToken}:{}), ...(k?{'Idempotency-Key':k}:{})}})
    if (r.statusCode !== expected) throw Error('HTTP_'+r.statusCode+'_'+r.data.code)
    return r.data.data
  }
  const post = (path, body, expected) => call(path,'POST',body,key(),expected)
  const delay = ms => new Promise(resolve=>setTimeout(resolve,ms))
  const insist = (value, code) => {if (!value) throw Error(code)}
  try {
    const agreements = []
    for (const type of ['privacy','user']) agreements.push({type,version:(await call('/agreements/'+type)).version})
    const code = await new Promise((resolve,reject)=>wx.login({success:r=>resolve(r.code),fail:()=>reject(Error('WX_LOGIN'))}))
    auth = await call('/auth/wechat/login','POST',{code,agreements})
    const play = expected=>post('/playbacks',{target:{kind:'lesson',id:fixture.lesson},platform:'wechat_windows'},expected)
    const rule = async sub=>(await call('/rewards?submissionId='+sub)).items.find(item=>item.rule.id===fixture.rule)
    if (mode==='claim') {
      const body={ruleId:fixture.rule,submissionId:fixture.sub}
      insist((await rule(fixture.sub)).eligible,'ELIGIBILITY')
      await play(403)
      const [a,b]=await Promise.all([post('/reward-claims',body),post('/reward-claims',body)])
      const after=await rule(fixture.sub)
      insist(a.id===b.id && a.status==='issued' && !after.eligible && after.claimId===a.id,'CLAIM')
      const history=await call('/reward-claims/'+a.id)
      const grants=(await call('/me/entitlements?pageSize=100')).items.filter(g=>g.targetId===fixture.course && g.source==='reward' && g.status==='active')
      const sub=await call('/practice/submissions/'+fixture.sub)
      insist(history.id===a.id && sub.rewardClaimIds.length===1 && sub.rewardClaimIds[0]===a.id && grants.length===1,'LEDGER')
      const ticket=await play(200)
      insist(ticket.access.allowed && /^http:\/\/127\.0\.0\.1:\d+\//.test(ticket.url),'TICKET')
      const download=await request({url:ticket.url,method:'GET',responseType:'arraybuffer'})
      insist(download.statusCode===200 && download.data.byteLength===fixture.courseBytes,'LESSON_BYTES')
      return {claim:a.id,grants:grants.length,downloadBytes:download.data.byteLength}
    }
    let session=fixture.session
    if (mode==='first') {
      const started=await post('/practice/sessions',{kind:'timer',plannedSeconds:60,tracks:[]})
      await delay(2200)
      const done=await post('/practice/sessions/'+started.id+'/events',{eventId:key(),sequence:1,action:'finish',activeDeltaSeconds:2,occurredAt:new Date().toISOString()})
      insist(done.status==='completed' && done.creditedSeconds===2,'PRACTICE')
      session=started.id
    } else {
      const prior=await call('/practice/submissions/'+fixture.sub)
      insist(prior.status==='rejected' && prior.reviewReason===fixture.reason,'REJECTION')
    }
    const data=wx.base64ToArrayBuffer(fixture.bytes)
    const ticket=await post('/uploads',{purpose:'practice_recording',filename:'recording.mp3',mimeType:'audio/mpeg',sizeBytes:data.byteLength,sha256:fixture.sha})
    insist(ticket.method==='PUT' && /^http:\/\/127\.0\.0\.1:\d+\//.test(ticket.uploadUrl),'LOCAL_STORAGE')
    const put=await request({url:ticket.uploadUrl,method:'PUT',data})
    insist(put.statusCode===204,'UPLOAD')
    await post('/uploads/'+ticket.assetId+'/complete',{sha256:fixture.sha})
    let asset
    for (let attempt=0;attempt<60;attempt++) {
      asset=await call('/assets/'+ticket.assetId)
      if (asset.status!=='processing') break
      await delay(450)
    }
    insist(asset.status==='ready' && asset.publicUrl===null,'PRIVATE_ASSET')
    const body={practiceSessionId:session,assetId:asset.id,text:'开发演示练习'}
    const k=key()
    const a=await call('/practice/submissions','POST',body,k)
    const b=await call('/practice/submissions','POST',body,k)
    const c=await post('/practice/submissions',body)
    insist(a.id===b.id && a.id===c.id && a.status==='pending' && !(await rule(a.id)).eligible,'SUBMISSION')
    await post('/reward-claims',{ruleId:fixture.rule,submissionId:a.id},409)
    return {session,sub:a.id,asset:asset.id}
  } catch(e) {
    return {failed:/^[A-Z0-9_]+$/.test(e.message)?e.message:'CLIENT_ERROR'}
  } finally {
    if (auth) await call('/auth/logout','POST',{refreshToken:auth.refreshToken})
  }
}
