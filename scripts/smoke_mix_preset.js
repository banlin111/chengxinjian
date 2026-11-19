// Injected only in the independent loopback IDE probe. No storage credentials are used.
async function exerciseMix(mode, info, api) {
  const {call, post, key, stats} = api
  const mine = '/me/mix-presets'
  const tracks = info.tracks
  const sameTracks = (actual, expected) => actual.length === expected.length &&
    actual.every((t,i) => t.noiseId === expected[i].noiseId && t.volume === expected[i].volume)
  if (mode === 'save') {
    const entry = (await call('/mix-presets')).items.find(p => p.id === info.editorial)
    if (!entry || !sameTracks(entry.tracks, tracks) || entry.backgroundAssetReady) throw Error('PUBLIC_MIX')
    const body = {title:'开发配方',tracks}, k = key()
    const saved = await call(mine,'POST',body,k)
    const replay = await call(mine,'POST',body,k)
    if (saved.id !== replay.id || !sameTracks(saved.tracks,tracks)) throw Error('SAVE_MIX')
    return {id:saved.id}
  }

  const detail = mine+'/'+info.saved
  const saved = await call(detail)
  if (!sameTracks(saved.tracks,tracks) || !(await call(mine)).items.some(p=>p.id===saved.id)) throw Error('RESTORE_MIX')
  const before = await stats()
  const session = await post('/practice/sessions',{kind:'noise',plannedSeconds:60,tracks:[],mixPresetId:saved.id})
  const changed = await call(detail,'PUT',{title:'修改后的开发配方',tracks:[tracks[1]],version:saved.version})
  const frozen = await call('/practice/sessions/'+session.id)
  if (!sameTracks(frozen.tracks,tracks) || frozen.mixPresetVersion !== saved.version || changed.version !== saved.version+1)
    throw Error('SESSION_SNAPSHOT')

  const players=[], paths=[]
  try {
    for (const track of frozen.tracks) {
      const ticket = await post('/playbacks',{target:{kind:'noise',id:track.noiseId},practiceSessionId:session.id,platform:'wechat_windows'})
      if (!/^http:\/\/127\.0\.0\.1:\d+\//.test(ticket.url)) throw Error('EXPECTED_LOOPBACK')
      const download = await new Promise((resolve,reject)=>wx.downloadFile({url:ticket.url,success:resolve,fail:()=>reject(Error('DOWNLOAD'))}))
      if (download.statusCode!==200) throw Error('DOWNLOAD_STATUS')
      paths.push(download.tempFilePath)
      const path=wx.env.USER_DATA_PATH+'/cxj-mix-'+ticket.playbackId+'.mp3'
      paths.push(path)
      await new Promise((resolve,reject)=>wx.getFileSystemManager().copyFile({srcPath:download.tempFilePath,destPath:path,
        success:resolve,fail:()=>reject(Error('COPY'))}))
      const audio=wx.createInnerAudioContext()
      players.push({audio,ticket,path,volume:track.volume})
    }
    const play = ({audio,ticket,path,volume}) => new Promise((resolve,reject)=>{
      let last=0, started=0
      const limit=setTimeout(()=>reject(Error('MIX_TIMEOUT')),15000)
      audio.onPlay(()=>{if(!started)started=Date.now()})
      audio.onTimeUpdate(()=>{last=Math.max(last,audio.currentTime)})
      audio.onError(()=>{clearTimeout(limit);reject(Error('MIX_NATIVE'))})
      audio.onEnded(()=>{
        clearTimeout(limit)
        if (!started || last<1 || Math.abs(audio.volume-volume)>0.01) return reject(Error('MIX_PROGRESS'))
        resolve({id:ticket.playbackId,started,end:Date.now(),seconds:Math.min(ticket.durationSeconds,Math.round(last))})
      })
      audio.src=path
      audio.volume=volume
      audio.play()
    })
    const results=await Promise.all(players.map(play))
    const overlap=Math.min(...results.map(r=>r.end))-Math.max(...results.map(r=>r.started))
    if (overlap<1000) throw Error('MIX_NO_OVERLAP')
    const media=await Promise.all(results.map(async r=>{
      const body={eventId:key(),sequence:1,kind:'ended',playedDeltaSeconds:r.seconds,positionSeconds:r.seconds,
        speed:1,occurredAt:new Date(r.end).toISOString()}, path='/playbacks/'+r.id+'/events', k=key()
      const ack=await call(path,'POST',body,k), retry=await call(path,'POST',body,k)
      if (ack.creditedSeconds!==retry.creditedSeconds) throw Error('RETRY_CHANGED')
      return ack.creditedSeconds
    }))
    const end=Math.max(...results.map(r=>r.end))
    const done=await post('/practice/sessions/'+session.id+'/events',{eventId:key(),sequence:1,action:'finish',
      activeDeltaSeconds:Math.ceil((end-Date.parse(session.startedAt))/1000),occurredAt:new Date(end).toISOString()})
    const after=await stats(), total=done.creditedSeconds
    const windows=results.map((r,i)=>({end:Math.floor(r.end/1000),duration:media[i]}))
    const shared=Math.max(0,Math.min(...windows.map(w=>w.end))-Math.max(...windows.map(w=>w.end-w.duration)))
    if (shared<1 || total!==media[0]+media[1]-shared || total!==after.totalSeconds-before.totalSeconds ||
        after.completedSessions-before.completedSessions!==1) throw Error('MIX_ACCOUNTING')
    await call(detail,'DELETE',{version:changed.version})
    return {overlapMs:overlap,trackSeconds:media,volumes:tracks.map(t=>t.volume),practiceSeconds:total,
      statsDelta:after.totalSeconds-before.totalSeconds,restored:true,snapshot:true,removed:true}
  } finally {
    players.forEach(p=>p.audio.destroy())
    for(const path of paths) try{wx.getFileSystemManager().unlinkSync(path)}catch(_){}
  }
}
