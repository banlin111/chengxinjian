"""A repeatable two-role recording demo using a real wx.login and private loopback objects."""
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import html
import json
import subprocess
import time
from urllib.parse import urlsplit
import uuid

import requests
from local_backend import ROOT
from smoke_phone_reauth import ide


def exercise(api, upload, check, png, ffmpeg, report, cleanup, assets, evidence):
    def key():
        return str(uuid.uuid4())

    def create(resource, table, **fields):
        row = api('POST', '/admin/content/'+resource, fields, key())
        cleanup.append((table, str(uuid.UUID(row['id']))))
        return row['id']

    def tone(frequency):
        return subprocess.run([ffmpeg, '-v','error','-f','lavfi','-i',f'sine=frequency={frequency}:duration=2',
                               '-metadata','comment='+key(),'-ar','16000','-ac','1','-codec:a','libmp3lame','-b:a','24k','-f','mp3','pipe:1'],
                              check=True,capture_output=True,timeout=10).stdout

    def reviewed(name, purpose, mime, payload):
        row,_ = upload(name,purpose,mime,payload)
        api('PUT','/admin/assets/'+row['id']+'/review',dict(decision='approve',reason='隔离演示素材',version=row['version']))
        return row['id']

    steps = []
    def observed(message):
        steps.append(message)
        # Show only public demonstration text in the independent IDE page.
        ide('wechat_automator','evaluate',fn_source='function(rows){getCurrentPages().slice(-1)[0].setData({rewardDemo:rows});return true}',
            args_json=json.dumps([steps]))

    def student(mode, fixture):
        source=(ROOT/'scripts/smoke_recording_rewards.js').read_text()
        start="""function(mode,fixture){const p=getCurrentPages().slice(-1)[0];p.rewardResult=null;
            (EXERCISE)(mode,fixture).then(v=>p.rewardResult=v,()=>p.rewardResult={failed:'CLEANUP'});return true}"""
        ide('wechat_automator','evaluate',fn_source=start.replace('EXERCISE',source),args_json=json.dumps([mode,fixture]))
        deadline=time.monotonic()+65
        while time.monotonic()<deadline:
            result=ide('wechat_automator','evaluate',fn_source='function(){return getCurrentPages().slice(-1)[0].rewardResult}').get('result')
            if result:
                if result.get('failed'):
                    raise RuntimeError('Recording client check failed: '+result['failed'])
                if 'asset' in result:
                    assets.append(str(uuid.UUID(result['asset'])))
                return result
            time.sleep(.5)
        raise RuntimeError('Recording client timed out')

    ide('wechat_ide','status')
    check('independent WeChat project',ide('wechat_automator','start').get('verified') and
          ide('wechat_automator','page_data',expected_path='pages/login/index').get('path')=='pages/login/index')
    cover=reviewed('cover.png','cover','image/png',png)
    course_bytes=tone(330)
    media=reviewed('lesson.mp3','course_media','audio/mpeg',course_bytes)
    teacher=create('teachers','content_teacher',name='演示讲师',avatarAssetId=None,bio='',credentials=[],userId=None,status='published')
    category=create('categories','content_category',name='演示课程',kind='course',sort=0,status='published')
    course=create('courses','content_course',title='练习奖励演示课',summary='',description='',coverAssetId=cover,categoryId=category,
                  teacherId=teacher,difficulty='beginner',priceFen=1,originalPriceFen=None,accessPolicy='purchase',status='published')
    lesson=create('lessons','content_lesson',courseId=course,title='声音练习',sort=1,assetId=media,trialSeconds=0,status='published')
    policy=dict(name='仅本机演示',trigger='submission',kind='course',courseId=course,coverAssetId=cover,stock=1,
                startAt=(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat(),perUserLimit=1,requiresReview=True,status='draft')
    rule=create('reward-rules','recording_reward_rule',**policy)
    api('PUT','/admin/content/reward-rules/'+rule,dict(policy,status='published',version=1))
    common=dict(course=course,lesson=lesson,rule=rule)
    last={}
    reason='请换一份清晰的练习录制'
    for mode,decision,hz in [('first','reject',480),('replacement','approve',710)]:
        # Unique digests between runs for the same real developer identity.
        payload=tone(hz)
        fixture=dict(common,**last,reason=reason,sha=hashlib.sha256(payload).hexdigest(),bytes=base64.b64encode(payload).decode())
        last=student(mode,fixture)
        path='/admin/submissions/'+last['sub']
        check('pending in review queue: '+mode,any(r['id']==last['sub'] for r in api('GET','/admin/submissions?status=pending')['items']))
        preview=api('POST',path+'/preview')
        if urlsplit(preview['url']).hostname!='127.0.0.1':
            raise RuntimeError('Expected local preview')
        with requests.Session() as local:
            local.trust_env=False
            response=local.get(preview['url'],timeout=8,allow_redirects=False)
        check('private reviewer audio matches submitted bytes: '+mode,response.status_code==200 and response.content==payload)
        observed('学员完成计时，提交私有录制。重复提交后，待审队列中只有一条记录。' if mode=='first' else '学员重新登录，看到驳回原因，在同一次练习下换一份录制提交。')
        verdict=dict(decision=decision,reason=reason if decision=='reject' else '试听通过',version=1)
        k=key()
        result=api('POST',path+'/review',verdict,k)
        check('replayed audit decision: '+mode,result==api('POST',path+'/review',verdict,k)==api('POST',path+'/review',verdict,key()))
        observed('审核员已读取私有音频，驳回申请并给出原因。' if mode=='first' else '审核员试听通过。学员现在有领取资格，领取前课程仍然锁定。')
    outcome=student('claim',dict(common,sub=last['sub'],courseBytes=len(course_bytes)))
    check('one issued reward and real authorized lesson download',outcome['grants']==1 and outcome['downloadBytes']==len(course_bytes))
    claim=str(uuid.UUID(outcome['claim']))
    check('issued claim in admin ledger',any(r['id']==claim for r in api('GET','/admin/reward-claims?status=issued')['items']))
    check('one stock used',api('GET','/admin/content/reward-rules/'+rule)['stockRemaining']==0)
    observed('学员同时领取两次，后台返回同一领取单，只新增一份课程权益。开发者工具已下载授权课节。')
    report['demo']={'steps':steps,'rewardGrants':outcome['grants'],'lessonBytes':outcome['downloadBytes'],'syntheticAudio':True}
    rows=''.join('<section><h2>'+html.escape(title)+'</h2><p>'+html.escape(step)+'</p></section>'
                 for title,step in zip(('提交练习录制','审核员驳回','学员重新提交','审核通过','领取课程'),steps))
    template=(ROOT/'scripts/probes/recording-demo.html').read_text()
    (evidence/'demo.html').write_text(template.replace('<!--steps-->',rows)
        .replace('<!--grants-->',str(outcome['grants'])).replace('<!--bytes-->',str(outcome['downloadBytes'])))
