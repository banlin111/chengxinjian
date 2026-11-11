import { computed, reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { screens, backScreen } from '../config/screens';

// UI-only state. No requests, entitlement changes, payments or message publishing.
export function useScreenPreview(screen: number) {
  const navigating = ref(false);
  const dialogOpen = ref(false);
  const busy = computed(() => navigating.value || dialogOpen.value);
  onShow(() => { navigating.value = false; });
  function go(id: number, query = '') {
    if (busy.value) return;
    const target = screens.find(item => item.id === id);
    if (!target) return;
    navigating.value = true;
    const url = `/pages/${target.path}/index${query ? `?${query}` : ''}`;
    const fail = () => { navigating.value = false; uni.showToast({title:'暂时无法打开，请重试',icon:'none'}); };
    if ('tab' in target && target.tab) uni.switchTab({url,fail});
    else uni.navigateTo({url,fail});
  }
  const selections = reactive<Record<string, number>>({ plans: 2, orders: 0 });
  const switches = reactive<Record<string, boolean>>({ reminder: true, live: true, logistics: false, bell: true, background: true });
  const playing = ref(false);
  const liked = ref(screen === 5);
  const followed = ref(false);
  const speed = ref(1);
  const timer = ref(screen === 6 ? 45 : 30);
  const progress = ref(38);
  const volumes = reactive([60, 35, 0, 0]);
  const query = ref('');
  const searched = ref(false);
  const history = ref<string[]>(['林涧 身体扫描 12 节', '亚麻冥想垫']);
  const message = ref('');
  const localMessages = ref<string[]>([]);
  const booked = reactive([true, false]);
  function choose(group: string, index: number) { if (!busy.value) selections[group] = index; }
  function selected(group: string, index: number) { return (selections[group] || 0) === index; }
  function notice(title: string, content = '当前为前端界面演示，尚未连接真实服务，不会提交订单、扣款或改变账户权益。') {
    if (busy.value) return;
    dialogOpen.value = true;
    uni.showModal({ title, content, showCancel: false, complete: () => { dialogOpen.value = false; } });
  }
  function runSearch(term = query.value) {
    query.value = term.trim();
    if (!query.value) { searched.value = false; return; }
    searched.value = true;
    history.value = [query.value, ...history.value.filter(item => item !== query.value)].slice(0, 8);
  }
  function setSwitch(key: string, event: Event) { switches[key] = (event as unknown as { detail: { value: boolean } }).detail.value; }
  function setVolume(index: number, event: any) { volumes[index] = event.detail.value; }
  function sendLocalMessage(event?: { detail?: { value?: string } }) {
    if (busy.value) return;
    const value = (event?.detail?.value ?? message.value).trim().slice(0,100);
    if (!value) return;
    localMessages.value = [...localMessages.value.slice(-19), value];
    message.value = '';
    notice('评论预览', '已添加到本机演示区，没有发送给其他人。');
  }
  function action(label: string) {
    if (busy.value) return;
    if (/提现/.test(label)) return notice('提现演示', '这里仅展示提现入口，不会发起提现或改变佣金余额。');
    if (/买断|购买|续费|加入购物车|领取|确认收货|兑换/.test(label)) return notice(label);
    if (/关注/.test(label)) { followed.value = !followed.value; return; }
    if (/喜欢|收藏/.test(label)) { liked.value = !liked.value; return; }
    if (/倍速/.test(label)) { speed.value = speed.value === 1.5 ? 1 : speed.value + .25; return; }
    if (/定时|自动关闭/.test(label)) { timer.value = timer.value === 60 ? 15 : timer.value + 15; return; }
    if (/后台播放/.test(label)) return notice('后台播放', '播放界面已预留后台播放设置；真实音频和微信能力将在联调时接入。');
    if (/播放|助眠/.test(label)) { playing.value = !playing.value; return; }
    if (/清除缓存/.test(label)) return notice('清除缓存', '当前页面使用本地示例资源，没有可清除的真实音频下载。');
    if (/退出登录/.test(label)) return notice('退出登录', '当前是未登录的演示环境，没有真实账号会话。');
    if (/分享|复制|保存海报|长图/.test(label)) return notice('分享预览', '海报和邀请码为示例，当前不会向微信好友发送消息或建立推广关系。');
    if (/查物流|查看物流/.test(label)) return notice('物流预览', '顺丰快递 · 示例运单\n10.30 08:12 到达杭州转运中心\n10.29 21:40 已从上海发出');
    if (/规则说明/.test(label)) return notice('推广规则（示例）', '页面中的比例与金额仅用于展示，实际规则以后端配置及甲方确认为准。');
    if (/生成推广海报/.test(label)) return go(20);
    if (/推广人员/.test(label)) return go(21);
    if (/会员|开通/.test(label)) return go(9);
    if (/打卡|练习心得/.test(label)) return go(5);
    if (/进入/.test(label) && screen === 11) return go(17);
    notice(label || '功能预览', '这个入口已保留，当前展示本地示例。相关业务将在后端联调时接入。');
  }
  return { busy, selections, switches, playing, liked, followed, speed, timer, progress, volumes, query, searched, history, message, localMessages, booked,
    choose, selected, notice, runSearch, setSwitch, setVolume, sendLocalMessage, action, go, back: backScreen };
}
