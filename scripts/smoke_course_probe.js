// Executed inside the independent WeChat IDE probe by the opt-in course smoke.
async function exerciseCourse(courseId, lessonId, expectedReason, seconds) {
  const base = 'http://127.0.0.1:18082/api/v1'
  let session
  let downloaded
  const call = (path, method, data, key) => new Promise((resolve, reject) => wx.request({
    url: base + path, method, data,
    header: { 'Content-Type': 'application/json', ...(session ? {Authorization: 'Bearer ' + session.accessToken} : {}),
      ...(key ? {'Idempotency-Key': key} : {}) },
    success(r) { if (r.statusCode === 200) resolve(r.data.data); else reject(Error('API_' + r.statusCode)) },
    fail() { reject(Error('LOCAL_REQUEST_FAILED')) }
  }))
  const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const n = Math.floor(Math.random()*16)
    return (c === 'x' ? n : ((n & 3) | 8)).toString(16)
  })
  try {
    const agreements = []
    for (const type of ['user','privacy']) {
      const a = await call('/agreements/' + type, 'GET')
      agreements.push({type, version: a.version})
    }
    const code = await new Promise((resolve, reject) => wx.login({success: r => resolve(r.code), fail: () => reject(Error('WX_LOGIN_FAILED'))}))
    session = await call('/auth/wechat/login', 'POST', {code, agreements})
    const directory = await call('/courses/' + courseId + '/lessons', 'GET')
    const lesson = directory.items.find(l => l.id === lessonId)
    if (!lesson || lesson.access.reason !== expectedReason || 'assetId' in lesson) throw Error('TRIAL_CATALOG_FAILED')
    const ticket = await call('/playbacks', 'POST', {target:{kind:'lesson',id:lessonId}, platform:'wechat_ios'}, key())
    if (ticket.durationSeconds !== seconds || ticket.access.reason !== expectedReason || !ticket.url.includes(expectedReason === 'trial' ? '/trials/' : '/validated/')) throw Error('TRIAL_TICKET_FAILED')
    const result = await new Promise((resolve, reject) => wx.downloadFile({url:ticket.url,success:resolve,fail:()=>reject(Error('COS_DOWNLOAD_FAILED'))}))
    downloaded = result.tempFilePath
    if (result.statusCode !== 200) throw Error('TRIAL_FETCH_FAILED')
    const ack = await call('/playbacks/' + ticket.playbackId + '/events','POST',{eventId:key(),sequence:1,kind:'seek',positionSeconds:1,
      playedDeltaSeconds:0,speed:1,occurredAt:new Date().toISOString()},key())
    if (ack.creditedSeconds || ack.completed) throw Error('TRIAL_EVENT_FAILED')
    const renewal = await call('/playbacks/' + ticket.playbackId + '/renew', 'POST', undefined, key())
    if (renewal.access.reason !== expectedReason) throw Error('RENEWAL_FAILED')
    await call('/me/favorites/course/' + courseId, 'PUT', {favorite:true})
    await call('/me/favorites/course/' + courseId, 'PUT', {favorite:true})
    await call('/me/likes/course/' + courseId, 'PUT', {liked:true})
    const liked = await call('/me/likes/course/' + courseId, 'PUT', {liked:true})
    if (!liked.liked || liked.count !== 1) throw Error('LIKE_DEDUP_FAILED')
    await call('/auth/logout','POST',{refreshToken:session.refreshToken})
    session = undefined
    const nextCode = await new Promise((resolve,reject)=>wx.login({success:r=>resolve(r.code),fail:()=>reject(Error('WX_RELOGIN_FAILED'))}))
    session = await call('/auth/wechat/login','POST',{code:nextCode,agreements})
    const history = await call('/me/learning','GET')
    const item = history.items.find(i=>i.lessonId===lessonId)
    if (!item || item.positionSeconds!==1 || item.completed || item.progressPercent!==0) throw Error('LEARNING_HISTORY_FAILED')
    const favorites = await call('/me/favorites?kind=course','GET')
    if (!favorites.items.some(i=>i.target.id===courseId)) throw Error('FAVORITE_PERSISTENCE_FAILED')
    const course = await call('/courses/'+courseId,'GET')
    if (!course.favorite || !course.liked || course.likeCount!==1) throw Error('PERSONAL_CATALOG_FAILED')
    const resumed = await call('/playbacks','POST',{target:{kind:'lesson',id:lessonId},platform:'wechat_ios'},key())
    if (resumed.resumeSeconds!==1) throw Error('CROSS_SESSION_RESUME_FAILED')
    const profile = await call('/me', 'GET')
    return {userId:profile.id, reason:expectedReason, seconds:ticket.durationSeconds, downloadStatus:result.statusCode, creditedSeconds:ack.creditedSeconds, libraryVerified:true, crossSessionVerified:true}
  } finally {
    try {
      if (downloaded) await new Promise(resolve => wx.getFileSystemManager().unlink({filePath:downloaded,complete:resolve}))
    } finally {
      if (session) {
        try {
          await call('/me/favorites/course/'+courseId,'PUT',{favorite:false})
          await call('/me/likes/course/'+courseId,'PUT',{liked:false})
        } finally { await call('/auth/logout','POST',{refreshToken:session.refreshToken}) }
      }
    }
  }
}
