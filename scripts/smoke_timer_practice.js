// Independent IDE probe. Codes and bearer credentials stay inside this invocation.
async function timerPractice() {
  const base = 'http://127.0.0.1:18083/api/v1'
  const key = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const value = Math.floor(Math.random() * 16)
    return (c === 'x' ? value : ((value & 3) | 8)).toString(16)
  })
  let session
  const call = (path, method, data, requestKey) => new Promise((resolve, reject) => wx.request({
    url: base + path, method, data,
    header: {'Content-Type': 'application/json', ...(session ? {Authorization: 'Bearer ' + session.accessToken} : {}),
      ...(requestKey ? {'Idempotency-Key': requestKey} : {})},
    success: r => r.statusCode === 200 && r.data.code === 'OK' ? resolve(r.data.data) : reject(Error(r.data && r.data.code || 'API_' + r.statusCode)),
    fail: () => reject(Error('REQUEST_FAILED'))
  }))
  const dateInShanghai = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)
  const firstDay = dateInShanghai()
  const login = async () => {
    const agreements = []
    for (const type of ['privacy','user']) {
      const {version} = await call('/agreements/' + type, 'GET')
      agreements.push({type,version})
    }
    const code = await new Promise((resolve, reject) => wx.login({success: r => resolve(r.code), fail: reject}))
    return call('/auth/wechat/login', 'POST', {code, agreements})
  }
  const stats = () => call('/me/practice/stats?from=' + firstDay + '&to=' + dateInShanghai(), 'GET')
  const logout = async () => {
    await call('/auth/logout', 'POST', {refreshToken: session.refreshToken})
    session = null
  }
  try {
    session = await login()
    const before = await stats()
    const started = await call('/practice/sessions', 'POST', {kind:'timer',plannedSeconds:60,tracks:[]}, key())
    const route = '/practice/sessions/' + started.id
    const event = (sequence, action, delta) => ({
      eventId:key(), sequence, action, activeDeltaSeconds:delta, occurredAt:new Date().toISOString()
    })
    const send = (body, requestKey) => call(route + '/events', 'POST', body, requestKey)
    await new Promise(resolve => setTimeout(resolve, 2200))
    const pause = event(1, 'pause', 2), requestKey = key()
    const paused = await send(pause, requestKey)
    const retried = await send(pause, requestKey)
    await logout()
    session = await login()
    const current = await call(route, 'GET')
    await send(event(current.version, 'resume', 0), key())
    await new Promise(resolve => setTimeout(resolve, 1200))
    const done = await send(event(3, 'finish', 1), key())
    const history = await call('/me/practice/sessions?pageSize=10', 'GET')
    let disabled = false
    try {
      await call('/checkins', 'POST', {practiceSessionId: done.id}, key())
    } catch (error) {
      disabled = error.message === 'PRACTICE_RULE_UNCONFIGURED'
    }
    const after = await stats()
    return {
      credited: paused.creditedSeconds === 2 && done.creditedSeconds === 3,
      retried: retried.version === paused.version && retried.creditedSeconds === paused.creditedSeconds,
      resumed: current.status === 'paused' && current.version === 2,
      completed: done.status === 'completed' && done.endedAt !== null,
      history: history.items.some(item => item.id === done.id && item.version === 4),
      stats: after.totalSeconds === before.totalSeconds + 3 && after.completedSessions === before.completedSessions + 1,
      noCheckin: disabled && !done.checkinEligible && after.checkinDays === before.checkinDays
    }
  } finally {
    if (session) await logout()
  }
}
