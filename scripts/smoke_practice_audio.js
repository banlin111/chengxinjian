// Runs in the independent WeChat IDE project. Only non-secret observations leave this function.
async function exerciseAudio(guide, noise, mode, oldTicket, exerciseMix) {
  const base = 'http://127.0.0.1:18082/api/v1'
  const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const n=Math.floor(Math.random()*16)
    return (c==='x'?n:(n&3)|8).toString(16)
  })
  let auth, player, file, downloaded
  const call = (path, method='GET', data, k) => new Promise((resolve,reject) => wx.request({
    url:base+path, method, data, header:{...(auth?{Authorization:'Bearer '+auth.accessToken}:{}),...(k?{'Idempotency-Key':k}:{})},
    success:r=>r.statusCode===200 && r.data.code==='OK'?resolve(r.data.data):reject(Error('API_'+r.statusCode+'_'+r.data.code)),
    fail:()=>reject(Error('API_NETWORK'))
  }))
  const post=(path,body)=>call(path,'POST',body,key())
  const denied=async (fn,expected)=>{
    try { await fn() } catch (e) { if (e.message===expected) return; throw e }
    throw Error('UNEXPECTED_SUCCESS')
  }
  const date = d=>new Date(d+8*3600000).toISOString().slice(0,10)
  const from=date(Date.now())
  const stats=()=>call('/me/practice/stats?from='+from+'&to='+date(Date.now()))
  try {
    const agreements=[]
    for(const type of ['privacy','user']) agreements.push({type,version:(await call('/agreements/'+type)).version})
    const code=await new Promise((resolve,reject)=>wx.login({success:r=>resolve(r.code),fail:()=>reject(Error('WX_LOGIN'))}))
    auth=await call('/auth/wechat/login','POST',{code,agreements})
    if (mode==='save' || mode==='mix') return await exerciseMix(mode,oldTicket,{call,post,key,stats})
    if (mode==='revoked') {
      await denied(()=>post('/playbacks/'+oldTicket+'/renew'),'API_403_CONTENT_LOCKED')
      await denied(()=>call('/meditations/'+guide),'API_404_NOT_FOUND')
      return {revoked:true}
    }
    const meditation=await call('/meditations/'+guide)
    const entry=(await call('/noises')).items.find(n=>n.id===noise)
    if (!entry || !(await call('/meditations?categoryId='+meditation.categoryId)).items.some(m=>m.id===guide) ||
        'assetId' in meditation || 'url' in entry) throw Error('CATALOG')
    const guided=mode==='guided'
    if (guided) await denied(()=>post('/playbacks',{target:{kind:'noise',id:noise},platform:'wechat_windows'}),'API_403_CONTENT_LOCKED')
    const before=await stats()
    const session=await post('/practice/sessions',guided?{kind:'guided',plannedSeconds:30,meditationId:guide,tracks:[]}:
      {kind:'noise',plannedSeconds:30,tracks:[{noiseId:noise,volume:0.4}]})
    const ticket=await post('/playbacks',{target:{kind:guided?'meditation':'noise',id:guided?guide:noise},
      platform:'wechat_windows',practiceSessionId:session.id})
    const renewal=await post('/playbacks/'+ticket.playbackId+'/renew')
    if (renewal.access.reason!==(guided?'free':'member') || renewal.mediaKind!=='audio') throw Error('TICKET')
    const result=await new Promise((resolve,reject)=>wx.downloadFile({url:renewal.url,success:resolve,fail:()=>reject(Error('DOWNLOAD'))}))
    if(result.statusCode!==200) throw Error('COS_STATUS')
    downloaded=result.tempFilePath
    file=wx.env.USER_DATA_PATH+'/cxj-audio-'+ticket.playbackId+'.mp3'
    await new Promise((resolve,reject)=>wx.getFileSystemManager().copyFile({srcPath:downloaded,destPath:file,success:resolve,fail:()=>reject(Error('COPY'))}))
    let last=0
    player=wx.createInnerAudioContext()
    await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('NATIVE_TIMEOUT')),12000)
      player.onError(()=>{clearTimeout(timer);reject(Error('NATIVE_ERROR'))})
      player.onTimeUpdate(()=>{last=Math.max(last,player.currentTime)})
      player.onEnded(()=>{clearTimeout(timer);resolve()})
      player.src=file;player.play()
    })
    if(last<1) throw Error('NATIVE_PROGRESS')
    const at=new Date().toISOString(), seconds=Math.min(ticket.durationSeconds,Math.round(last))
    const ev={eventId:key(),sequence:1,kind:'ended',positionSeconds:seconds,playedDeltaSeconds:seconds,speed:1,occurredAt:at}
    const path='/playbacks/'+ticket.playbackId+'/events', k=key()
    const a=await call(path,'POST',ev,k), b=await call(path,'POST',ev,k)
    const done=await post('/practice/sessions/'+session.id+'/events',{eventId:key(),sequence:1,action:'finish',activeDeltaSeconds:seconds,occurredAt:at})
    const after=await stats()
    if(a.creditedSeconds!==b.creditedSeconds || done.creditedSeconds<=0 || done.creditedSeconds>seconds ||
        after.totalSeconds-before.totalSeconds!==done.creditedSeconds || after.completedSessions-before.completedSessions!==1)
      throw Error('ACCOUNTING')
    return {userId:auth.user.id, playbackId:ticket.playbackId,seconds:done.creditedSeconds,lastTime:last,
      access:renewal.access.reason,downloadStatus:result.statusCode,nativeEnded:true,statsDelta:after.totalSeconds-before.totalSeconds}
  } catch(e) {
    return {failed:/^(?:API_[0-9]{3}_[A-Z_]+|[A-Z_]+)$/.test(e.message)?e.message:'PROBE_ERROR'}
  } finally {
    if(player) player.destroy()
    for(const path of [file,downloaded]) if(path) try{wx.getFileSystemManager().unlinkSync(path)}catch(_){}
    if(auth) await call('/auth/logout','POST',{refreshToken:auth.refreshToken})
  }
}
