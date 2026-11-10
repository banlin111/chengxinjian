"""Build README contract appendix and OpenAPI from the checked TypeScript schema."""
from pathlib import Path
import json, re, copy, hashlib

ROOT = Path(__file__).resolve().parents[1]
raw = json.loads((ROOT / 'research/api-schema.json').read_text(encoding='utf-8'))
defs = raw.get('definitions', {})
# OpenAPI component keys cannot contain TypeScript generic punctuation. Keep
# simple model names stable and use deterministic collision-safe generic names.
schema_names = {k: k if re.fullmatch(r'[a-zA-Z0-9._-]+', k) else
    re.sub(r'[^a-zA-Z0-9._-]+', '_', k).strip('_') + '_' + hashlib.sha256(k.encode()).hexdigest()[:8]
    for k in defs}
assert len(set(schema_names.values())) == len(schema_names), 'Schema name collision'

def resolve(s):
    if '$ref' in s:
        return resolve(defs[s['$ref'].split('/')[-1]])
    return s

def convert(x):
    if isinstance(x, list): return [convert(v) for v in x]
    if not isinstance(x, dict): return x
    x = copy.deepcopy(x)
    if '$ref' in x and x['$ref'].startswith('#/definitions/'):
        x['$ref'] = '#/components/schemas/' + schema_names[x['$ref'].split('/')[-1]]
    if 'const' in x: x['enum'] = [x.pop('const')]
    if isinstance(x.get('type'), list):
        types = x['type']
        if 'null' in types:
            x['nullable'] = True
            types = [t for t in types if t != 'null']
        if len(types) == 1: x['type'] = types[0]
        else: x.pop('type'); x['anyOf'] = [{'type': t} for t in types]
    if x.get('type') == 'null': x['type'] = 'string'; x['nullable'] = True
    if 'anyOf' in x:
        variants = x['anyOf']
        if any(v.get('type') == 'null' for v in variants):
            x['nullable'] = True
            variants = [v for v in variants if v.get('type') != 'null']
            if len(variants) == 1 and '$ref' in variants[0]:
                x.pop('anyOf'); x['allOf'] = variants
            elif len(variants) == 1:
                x.pop('anyOf'); x.update(variants[0])
            else: x['anyOf'] = variants
    x.pop('$schema', None)
    return {k: convert(v) for k, v in x.items()}

public = {
 '/bootstrap','/agreements/{type}','/home','/notices','/notices/{id}',
 '/search','/search/hot','/categories','/teachers','/teachers/{id}',
 '/courses','/courses/{id}','/courses/{id}/lessons','/meditations','/meditations/{id}',
 '/noises','/mix-presets','/lives','/lives/{id}','/replays','/replays/{id}',
 '/membership/plans','/regions',
}
public_auth = {'/auth/wechat/login','/auth/sms','/auth/register','/auth/sms/login','/auth/password/login','/auth/password/reset','/auth/refresh','/admin/auth/login','/admin/auth/refresh'}

def security(path):
    if path in public or path in public_auth: return []
    if path.startswith('/admin/'): return [{'adminBearer': []}]
    if path.startswith('/uploads') or path.startswith('/assets/'): return [{'userBearer': []}, {'adminBearer': []}]
    return [{'userBearer': []}]

def label(s):
    if '$ref' in s: return s['$ref'].split('/')[-1]
    if s.get('type') == 'array': return label(s['items'])+'[]'
    if 'anyOf' in s: return ' / '.join(label(v) for v in s['anyOf'])
    if s.get('type') == 'object':
        return '{'+', '.join(s.get('properties', {}).keys())+'}'
    return str(s.get('type','object'))

error = {'type':'object','required':['code','message','data','requestId','serverTime'], 'properties':{
 'code':{'type':'string'}, 'message':{'type':'string'}, 'data':{'nullable':True,'type':'object'},
 'requestId':{'type':'string'}, 'serverTime':{'type':'string','format':'date-time'},
 'details':{'type':'array','items':{'type':'object','required':['field','reason'],'properties':{'field':{'type':'string'},'reason':{'type':'string'}}}}
}}
spec = {'openapi':'3.0.3','info':{'title':'澄心涧 API 契约草案','version':'0.3.0',
 'description':'思维导图全范围；微信小程序与管理后台。Java/Spring Boot 实现。业务规则见 README。此文件不是已实现的服务。'},
 'servers':[{'url':'https://api.chengxinjian.example/api/v1','description':'示例域名，替换为测试或生产地址'}],
 'paths':{},'components':{'securitySchemes':{'userBearer':{'type':'http','scheme':'bearer'},'adminBearer':{'type':'http','scheme':'bearer'}},'schemas':{schema_names[k]:convert(v) for k,v in defs.items()}}}
spec['components']['schemas']['ErrorEnvelope'] = error
rows = []
for key,s in raw['properties'].items():
    method,path = key.split(' ',1)
    parts = resolve(s)['properties']
    params=[]
    for name in re.findall(r'\{([^}]+)\}',path):
        schema={'type':'string'}
        if name=='kind': schema['enum']=['course','lesson','meditation','replay']
        if name=='type': schema['enum']=['privacy','user','membership']
        params.append({'name':name,'in':'path','required':True,'schema':schema})
    if 'q' in parts:
        q=resolve(parts['q'])
        for name,v in q.get('properties',{}).items():
            v=convert(v)
            if name in ('page','pageSize','limit','learningPage','livePage'):
                v['type']='integer';v['minimum']=1
                if name in ('pageSize','limit'): v['maximum']=100
                if name=='page': v['default']=1
                if name=='pageSize': v['default']=20
            if name in ('from','to','registeredFrom','registeredTo'): v['format']='date'
            if name=='month': v['pattern']=r'^\d{4}-\d{2}$'
            params.append({'name':name,'in':'query','required':name in q.get('required',[]),'schema':v})
    if method=='POST' and path not in public_auth and path not in ('/auth/logout','/admin/auth/logout','/admin/assets/{id}/preview'):
        params.append({'name':'Idempotency-Key','in':'header','required':True,'schema':{'type':'string','format':'uuid'},'description':'相同意图重试复用，同键不同体返回409'})
    result=convert(parts['r'])
    env={'type':'object','required':['code','message','data','requestId','serverTime'],'properties':{
     'code':{'type':'string','enum':['OK']},'message':{'type':'string'},'data':result,
     'requestId':{'type':'string'},'serverTime':{'type':'string','format':'date-time'}}}
    op={'operationId':re.sub(r'[^A-Za-z0-9]+','_',key).strip('_'), 'summary':key,
        'tags':['admin' if path.startswith('/admin/') else path.split('/')[1]], 'security':security(path),
        'responses':{'200':{'description':'成功；异步任务需查询 Job 状态','content':{'application/json':{'schema':env}}}}}
    if params: op['parameters']=params
    if 'b' in parts: op['requestBody']={'required':True,'content':{'application/json':{'schema':convert(parts['b'])}}}
    for status in [400,401,403,404,409,410,422,429,500,503]:
        op['responses'][str(status)]={'description':'业务错误，详见 README 错误码','content':{'application/json':{'schema':{'$ref':'#/components/schemas/ErrorEnvelope'}}}}
    spec['paths'].setdefault(path,{})[method.lower()]=op
    rows.append(f"| `{method}` | `{path}` | {'公开/可选登录' if not security(path) else '后台权限' if path.startswith('/admin/') else '已登录'} | `{label(parts['q']) if 'q' in parts else '—'}` | `{label(parts['b']) if 'b' in parts else '—'}` | `{label(parts['r'])}` |")

(ROOT/'openapi.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
readme=ROOT/'README.md'
base=readme.read_text(encoding='utf-8').split('<!-- GENERATED_CONTRACTS -->')[0]
append='<!-- GENERATED_CONTRACTS -->\n\n### 11.1 HTTP 接口索引\n\n'
append+=f'共 **{len(rows)} 个方法/路径组合**，均加 `/api/v1` 前缀。以下类型名在后面的完整类型定义中展开；自动生成的类型别名以 OpenAPI 内定义为准。回调与 WebSocket 见第8节及 SocketEvent。\n\n'
append+='| 方法 | 路径 | 身份 | 查询 | 请求体 | data 返回值 |\n| --- | --- | --- | --- | --- | --- |\n'+'\n'.join(rows)+'\n'
append+='\n### 11.2 数据字段、枚举与状态完整定义\n\n```typescript\n'+(ROOT/'contracts/models.ts').read_text(encoding='utf-8')+'```\n'
append+='\n### 11.3 每个接口的请求与返回对应关系\n\n```typescript\n'+(ROOT/'contracts/api.ts').read_text(encoding='utf-8')+'```\n'
append+='\n## 12. 修改契约与复查\n\n修改 `contracts/` 后，在本目录执行 `npm ci`、`npm run check`；它会检查类型、生成 JSON Schema、更新 OpenAPI 与本 README 附录。需要 Node.js 和 Python。不要只改生成的 README 附录。破坏性变更必须双方共同确认、升契约版本并同步前后端；新增可选字段一般可保持 `/v1`。\n\n契约检查只验证结构；已实现后端与测试范围见 `docs/backend-development.md`。支付、直播、备案或真机测试仍需甲方真实账号与对应开发实现后验证。\n'
readme.write_text(base+append,encoding='utf-8')
print(f'Generated {len(rows)} API operations, {len(defs)} schemas; README {readme.stat().st_size} bytes')
