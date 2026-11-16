// Executed inside the independent WeChat IDE probe by the opt-in course smoke.
async function exerciseCourse(fixtureCategory, expectedCourse, expectedLesson, expectedReason, seconds, previousFull) {
  const base = 'http://127.0.0.1:18082/api/v1'
  let session
  let downloaded
  let localVideo
  const call = (path, method, data, key) => new Promise((resolve, reject) => wx.request({
    url: base + path, method, data,
    header: { 'Content-Type': 'application/json', ...(session ? {Authorization: 'Bearer ' + session.accessToken} : {}),
      ...(key ? {'Idempotency-Key': key} : {}) },
    success(r) { if (r.statusCode === 200 && r.data.code === 'OK') resolve(r.data.data); else reject(Error('API_' + r.statusCode + '_' + r.data.code)) },
    fail() { reject(Error('LOCAL_REQUEST_FAILED')) }
  }))
  const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const n = Math.floor(Math.random()*16)
    return (c === 'x' ? n : ((n & 3) | 8)).toString(16)
  })
  let courseId = expectedCourse
  try {
    const agreements = []
    for (const type of ['user','privacy']) {
      const a = await call('/agreements/' + type, 'GET')
      agreements.push({type, version: a.version})
    }
    const code = await new Promise((resolve, reject) => wx.login({success: r => resolve(r.code), fail: () => reject(Error('WX_LOGIN_FAILED'))}))
    session = await call('/auth/wechat/login', 'POST', {code, agreements})
    const list = await call('/courses?categoryId=' + fixtureCategory + '&sort=newest&page=1&pageSize=20', 'GET')
    const selected = list.items.find(c => c.id === expectedCourse)
    if (!selected || selected.accessPolicy !== 'purchase' || selected.access.reason !== expectedReason) throw Error('COURSE_LIST_FAILED')
    courseId = selected.id
    const detail = await call('/courses/' + courseId, 'GET')
    if (detail.id !== courseId || detail.accessPolicy !== selected.accessPolicy || detail.lessonCount !== 1) throw Error('DETAIL_FAILED')
    const directory = await call('/courses/' + detail.id + '/lessons', 'GET')
    const lesson = directory.items[0]
    if (!lesson || lesson.id !== expectedLesson || lesson.courseId !== detail.id || lesson.access.reason !== expectedReason || 'assetId' in lesson) throw Error('CATALOG_FAILED')
    const lessonId = lesson.id
    if (previousFull) {
      try {
        await call('/playbacks/' + previousFull + '/renew', 'POST', undefined, key())
        throw Error('REVOKED_SESSION_RENEWED')
      } catch (e) { if (e.message !== 'API_410_PLAYBACK_EXPIRED') throw e }
    }
    const ticket = await call('/playbacks', 'POST', {target:{kind:'lesson',id:lessonId}, platform:'wechat_windows'}, key())
    if (ticket.durationSeconds !== seconds || ticket.resumeSeconds !== lesson.resumeSeconds || ticket.access.reason !== expectedReason ||
        ticket.mediaKind !== 'video' || !ticket.url.includes(expectedReason === 'trial' ? '/trials/' : '/validated/')) throw Error('TICKET_FAILED')
    const renewKey = key()
    const renewal = await call('/playbacks/' + ticket.playbackId + '/renew', 'POST', undefined, renewKey)
    const retried = await call('/playbacks/' + ticket.playbackId + '/renew', 'POST', undefined, renewKey)
    if (renewal.playbackId !== ticket.playbackId || renewal.url !== retried.url || renewal.access.reason !== expectedReason) throw Error('RENEWAL_FAILED')
    const result = await new Promise((resolve, reject) => wx.downloadFile({url:renewal.url,success:resolve,fail:()=>reject(Error('COS_DOWNLOAD_FAILED'))}))
    downloaded = result.tempFilePath
    if (result.statusCode !== 200) throw Error('COS_FETCH_FAILED')
    localVideo = wx.env.USER_DATA_PATH + '/cxj-probe-video.mp4'
    await new Promise((resolve,reject)=>wx.getFileSystemManager().copyFile({srcPath:downloaded,destPath:localVideo,success:resolve,fail:()=>reject(Error('FIXTURE_COPY_FAILED'))}))
    const events = '/playbacks/' + ticket.playbackId + '/events'
    const start = await call(events,'POST',{eventId:key(),sequence:1,kind:'seek',positionSeconds:0,
      playedDeltaSeconds:0,speed:1,occurredAt:new Date().toISOString()},key())
    const pages = getCurrentPages()
    const rendered = await pages[pages.length-1].playVideoFixture(localVideo)
    if (!rendered.played || rendered.lastTime <= 0 || Math.abs(rendered.duration-seconds) > 0.25) throw Error('NATIVE_PLAYBACK_FAILED')
    const position = Math.round(rendered.duration)
    const ended = {eventId:key(),sequence:2,kind:'ended',positionSeconds:position,playedDeltaSeconds:position,speed:1,occurredAt:new Date().toISOString()}
    const eventKey = key()
    const ack = await call(events,'POST',ended,eventKey)
    const replayAck = await call(events,'POST',ended,eventKey)
    if (ack.acceptedSequence !== 2 || ack.positionSeconds !== position || ack.creditedSeconds !== replayAck.creditedSeconds ||
        (expectedReason === 'purchased' && ack.creditedSeconds <= start.creditedSeconds) ||
        (expectedReason === 'trial' && !previousFull && ack.completed)) throw Error('ENDED_ACK_FAILED')
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
    if (!item || item.positionSeconds!==position || item.completed!==ack.completed) throw Error('LEARNING_HISTORY_FAILED')
    const favorites = await call('/me/favorites?kind=course','GET')
    if (!favorites.items.some(i=>i.target.id===courseId)) throw Error('FAVORITE_PERSISTENCE_FAILED')
    const course = await call('/courses/'+courseId,'GET')
    if (!course.favorite || !course.liked || course.likeCount!==1) throw Error('PERSONAL_CATALOG_FAILED')
    const resumed = await call('/playbacks','POST',{target:{kind:'lesson',id:lessonId},platform:'wechat_windows'},key())
    if (resumed.resumeSeconds!==position) throw Error('CROSS_SESSION_RESUME_FAILED')
    const profile = await call('/me', 'GET')
    return {userId:profile.id, reason:expectedReason, seconds:ticket.durationSeconds, downloadStatus:result.statusCode, creditedSeconds:ack.creditedSeconds, resumeSeconds:ticket.resumeSeconds, playbackId:ticket.playbackId, nativeDuration:rendered.duration, lastTime:rendered.lastTime, completed:ack.completed, libraryVerified:true, crossSessionVerified:true}
  } catch (error) {
    // Only fixed local check codes may cross the IDE bridge; never serialize network errors or URLs.
    return { failedCheck: /^[A-Z][A-Z_0-9]{2,80}$/.test(error.message) ? error.message : 'PROBE_RUNTIME_ERROR' }
  } finally {
    try {
      if (localVideo) await new Promise(resolve => wx.getFileSystemManager().unlink({filePath:localVideo,complete:resolve}))
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
