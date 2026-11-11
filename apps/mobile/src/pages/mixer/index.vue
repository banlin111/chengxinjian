<script setup lang="ts">
import { ref, watch } from 'vue';
import { onHide, onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
import { useMixerPreviewStore, mixerTracks, mixerTimers } from '../../stores/mixer-preview';
const mix = useMixerPreviewStore();
const { notice } = useScreenPreview(6);
const playing = ref(false);
const choosing = ref(false);
onLoad(() => mix.restoreSaved());
onHide(() => { playing.value = false; });
watch(() => mix.audible, value => { if (!value) playing.value = false; });
function changeVolume(index: number, event: { detail: { value: number } }) { mix.setVolume(index,event.detail.value); }
function chooseTimer() {
  if (choosing.value) return;
  choosing.value = true;
  uni.showActionSheet({
    itemList: mixerTimers.map(value => `${value ? `${value} 分钟后关闭` : '关闭定时'}${value === mix.timer ? ' · 当前' : ''}`),
    success: result => { if (mixerTimers[result.tapIndex] !== undefined) mix.timer = mixerTimers[result.tapIndex]; },
    complete: () => { choosing.value = false; },
  });
}
function chooseRecipe() {
  if (choosing.value) return;
  choosing.value = true;
  const options = ['应用示例：雨夜篝火','保存当前配方到本机',...(mix.saved ? ['恢复本机配方'] : []),'分享配方（预留）'];
  uni.showActionSheet({
    itemList: options,
    success: result => {
      const selected = options[result.tapIndex];
      if (selected === options[0]) { mix.applyExample(); playing.value = false; }
      else if (selected === options[1]) uni.showToast({title:mix.save() ? '配方已保存到本机' : '保存失败，请重试',icon:'none'});
      else if (selected === '恢复本机配方') { mix.applySaved(); playing.value = false; }
      else if (selected === '分享配方（预留）') notice('分享配方', '分享入口已预留，尚未接入微信分享，不会向他人发送内容。');
    },
    complete: () => { choosing.value = false; },
  });
}
function toggle() {
  if (!mix.audible) { uni.showToast({title:'请添加声音并调高音量',icon:'none'}); return; }
  playing.value = !playing.value;
}
</script>
<template>
  <ScreenFrame :screen="6">
    <image class="mixer-background" src="/static/prototype/6f54511a47d6.jpg" mode="aspectFill" aria-hidden="true" />
    <view class="mixer-shade" />
    <view class="mixer-content screen-content">
      <text class="mixer-title">混合你的夜声</text>
      <text class="mixer-subtitle">叠加自然声音，调出专属入睡氛围</text>
      <view class="mixer-tracks">
        <view v-for="(track,index) in mixerTracks" :key="track.id" class="mixer-track" :class="{ 'track-enabled': mix.enabled[index] }" :data-track="track.id">
          <view class="track-header">
            <view class="track-icon"><AppIcon :asset="track.icon" /></view><text class="track-name">{{ track.name }}</text>
            <text v-if="mix.enabled[index]" class="track-tag">{{ mix.volumes[index] === 0 ? '已静音' : playing ? '演示中' : '已添加' }}</text>
            <text v-if="mix.enabled[index]" class="track-value">{{ mix.volumes[index] }}%</text>
            <MotionButton v-if="mix.enabled[index]" class="remove-track" feedback="icon" :busy="choosing" :aria-label="`移除${track.name}`" @click="mix.remove(index)">×</MotionButton>
            <MotionButton v-else class="add-track" feedback="icon" :busy="choosing" :aria-label="`添加${track.name}`" @click="mix.add(index)"><text>点击添加</text></MotionButton>
          </view>
          <view class="track-slider-reveal" :class="{ 'slider-open': mix.enabled[index] }">
            <slider v-if="mix.enabled[index]" class="track-slider" :aria-label="`${track.name}音量`" :value="mix.volumes[index]" :disabled="choosing" :active-color="track.color" background-color="rgba(255,255,255,.15)" block-color="#ffffff" :block-size="14" @changing="changeVolume(index,$event)" @change="changeVolume(index,$event)" />
          </view>
        </view>
      </view>
      <view class="mixer-options">
        <MotionButton class="mixer-option" feedback="icon" :busy="choosing" aria-label="调整助眠定时" @click="chooseTimer"><text class="option-label">自动关闭</text><text class="option-value">{{ mix.timer ? `${mix.timer} 分钟` : '已关闭' }}</text><text class="option-note">点击可调整</text></MotionButton>
        <MotionButton class="mixer-option recipe" feedback="icon" :busy="choosing" aria-label="我的配方" @click="chooseRecipe"><text class="option-label">我的配方</text><text class="option-value">{{ mix.saved ? '本机配方' : '雨夜篝火' }}</text><text class="option-note">{{ mix.saved ? '已保存 · 点击管理' : '示例配方 · 点击管理' }}</text></MotionButton>
      </view>
      <MotionButton class="start-sleep" feedback="icon" :busy="choosing" :aria-label="playing ? '暂停助眠演示' : '开始助眠演示'" :aria-pressed="playing ? 'true' : 'false'" @click="toggle"><text>{{ playing ? '暂停助眠' : '开始助眠' }}</text><AppIcon :asset="playing ? 'fas-pause-ffffff' : 'fas-play-ffffff'" /></MotionButton>
      <text class="mixer-demo-note">界面演示 · 暂无音源，定时不实际执行</text>
    </view>
  </ScreenFrame>
</template>
<style scoped>
.mixer-background { position: absolute; inset: 0; width: 100%; height: 100%; opacity: .5; }
.mixer-shade { position: absolute; inset: 0; background: linear-gradient(rgba(15,29,31,.55),rgba(22,48,47,.7),#0F1D1F); }
.mixer-content { position: relative; padding: var(--top) 20px max(30px,env(safe-area-inset-bottom)); color: #fff; }
.mixer-title { display: block; font-size: 22px; font-weight: 700; line-height: 31px; }
.mixer-subtitle { display: block; margin-top: 5px; font-size: 12px; color: #BCD5CF; line-height: 20px; }
.mixer-tracks { display: flex; flex-direction: column; gap: 12px; margin-top: 22px; }
.mixer-track { padding: 10px 14px; border: 1px solid rgba(255,255,255,.12); border-radius: 16px; background: rgba(255,255,255,.08); }
.track-header { display: flex; align-items: center; gap: 8px; min-height: 44px; }
.track-icon { display: flex; font-size: 17px; flex-shrink: 0; }.track-name { font-size: 14px; font-weight: 600; flex-shrink: 0; }
.track-tag { padding: 2px 7px; border-radius: 20px; color: #8ED4C7; background: rgba(63,179,163,.16); font-size: 10px; white-space: nowrap; }
.track-value { margin-left: auto; color: #BCD5CF; font-size: 12px; font-variant-numeric: tabular-nums; }
.remove-track { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; flex-shrink: 0; margin-right: -10px; color: #BCD5CF; font-size: 22px; }
.add-track { display: flex; align-items: center; justify-content: center; margin-left: auto; min-height: 44px; min-width: 76px; color: #BCD5CF; font-size: 11px; }
.add-track text { display: block; padding: 5px 10px; border-radius: 20px; background: rgba(255,255,255,.12); }
.track-slider-reveal { overflow: hidden; max-height: 0; transition: max-height 200ms cubic-bezier(.22,1,.36,1); }.track-slider-reveal.slider-open { max-height: 44px; }
.track-slider { margin: 0; padding: 0 7px; height: 36px; }
.mixer-options { display: flex; gap: 12px; margin-top: 20px; }
.mixer-option { flex: 1; min-width: 0; padding: 14px 8px; border: 1px solid rgba(255,255,255,.12); border-radius: 16px; background-image: linear-gradient(rgba(255,255,255,.08),rgba(255,255,255,.08)); text-align: center; }
.recipe { background-image: linear-gradient(135deg,rgba(63,179,163,.35),rgba(44,140,130,.3)); border-color: rgba(63,179,163,.4); }
.option-label,.option-note { display: block; color: #BCD5CF; font-size: 11px; line-height: 18px; }.option-value { display: block; margin-top: 4px; font-size: 16px; font-weight: 600; line-height: 24px; }.option-note { font-size: 10px; margin-top: 3px; }
.start-sleep { display: flex; justify-content: center; align-items: center; gap: 9px; width: 100%; min-height: 52px; margin-top: 20px; padding: 14px; border-radius: 30px; background-image: linear-gradient(100deg,#3FB3A3,#6FCABB); color: #fff; font-size: 14px; font-weight: 600; box-shadow: 0 8px 24px rgba(63,179,163,.15); }
.start-sleep .app-icon { font-size: 12px; }
.mixer-demo-note { display: block; margin-top: 16px; text-align: center; color: #A8C5BD; font-size: 10px; line-height: 18px; }
@media (max-width:350px) { .mixer-content { padding-left: 16px; padding-right: 16px; }.mixer-track { padding-left: 12px; padding-right: 12px; }.track-header { gap: 6px; }.track-tag { padding: 2px 5px; }.track-name { font-size: 13px; } }
@media (prefers-reduced-motion:reduce) { .track-slider-reveal { transition: none; } }
</style>
<style>
.pdf-screen[data-screen="6"] { min-height: calc(100vh - 28px); padding-bottom: 0; background: #0F1D1F; }
</style>
