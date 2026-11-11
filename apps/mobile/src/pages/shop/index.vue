<script setup lang="ts">
import { computed, ref } from 'vue';
import { onReady } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { usePreviewNavigation } from '../../composables/usePreviewNavigation';
import { useScreenPreview } from '../../composables/useScreenPreview';
import { shopProducts, productUrl, matchesShopQuery } from '../../services/shop-preview';
const { navigating, navigate } = usePreviewNavigation();
const { notice } = useScreenPreview(7);
const query = ref('');
const submitted = ref('');
const searchInput = ref<{ $el?: HTMLElement } | null>(null);
const flash = computed(() => matchesShopQuery(shopProducts.bowl,submitted.value) ? shopProducts.bowl : null);
const recommended = computed(() => matchesShopQuery(shopProducts.mat,submitted.value) ? shopProducts.mat : null);
onReady(() => {
  // #ifdef H5
  searchInput.value?.$el?.querySelector('input')?.setAttribute('aria-label','搜索商城商品');
  // #endif
});
function search(value?: string) {
  if (navigating.value) return;
  let current = value ?? query.value;
  // #ifdef H5
  if (value === undefined) current = searchInput.value?.$el?.querySelector('input')?.value ?? current;
  // #endif
  query.value = current.trim().slice(0,60); submitted.value = query.value; uni.hideKeyboard();
}
function clearSearch() { query.value = ''; submitted.value = ''; }
function practice() {
  if (navigating.value) return;
  navigating.value = true;
  uni.switchTab({url:'/pages/practice/index',fail:()=>{navigating.value=false;uni.showToast({title:'暂时无法打开，请重试',icon:'none'});}});
}
</script>
<template>
  <ScreenFrame :screen="7" gradient>
    <view class="shop-content screen-content">
      <view class="shop-toolbar">
        <view class="shop-search">
          <view class="shop-search-icon"><AppIcon asset="fas-magnifying-glass-a8c5bd" /></view>
          <input ref="searchInput" v-model="query" class="shop-input" placeholder="颂钵 / 香薰 / 抱枕" placeholder-style="color:#A8C5BD" :maxlength="60" :disabled="navigating" confirm-type="search" aria-label="搜索商城商品" @confirm="search($event.detail.value)" />
          <MotionButton v-if="query" class="shop-clear" feedback="icon" aria-label="清除商品搜索" :busy="navigating" @click="clearSearch">×</MotionButton>
          <MotionButton class="shop-submit" feedback="icon" aria-label="搜索商品" :busy="navigating" @click="search()">搜索</MotionButton>
        </view>
        <MotionButton class="shop-cart" feedback="icon" aria-label="购物车" data-action="购物车" :busy="navigating" @click="notice('购物车演示','角标为原型示例数量，当前未连接真实购物车与库存服务。')"><AppIcon asset="fas-cart-shopping-ffffff" /><text class="cart-count">3</text></MotionButton>
      </view>
      <view class="shop-shortcuts">
        <MotionButton class="shop-shortcut" feedback="icon" aria-label="积分兑换" :busy="navigating" @click="navigate('/pages/points/index')"><view class="shortcut-icon coins"><AppIcon asset="fas-coins-0f1d1f" /></view><text>积分兑换</text></MotionButton>
        <MotionButton class="shop-shortcut" feedback="icon" aria-label="卡券中心" :busy="navigating" @click="notice('卡券中心','卡券入口已预留，暂无可领取或使用的真实卡券。')"><view class="shortcut-icon tickets"><AppIcon asset="fas-ticket-ffffff" /></view><text>卡券中心</text></MotionButton>
        <MotionButton class="shop-shortcut" feedback="icon" aria-label="打卡奖品" :busy="navigating" @click="practice"><view class="shortcut-icon gifts"><AppIcon asset="fas-gift-ffffff" /></view><text>打卡奖品</text></MotionButton>
        <MotionButton class="shop-shortcut" feedback="icon" aria-label="限时秒杀" :busy="navigating" @click="navigate(productUrl(shopProducts.bowl))"><view class="shortcut-icon sale"><AppIcon asset="fas-fire-ffffff" /></view><text>限时秒杀</text></MotionButton>
      </view>
      <view class="shop-membership">
        <view class="membership-icon"><AppIcon asset="fas-crown-e9c08a" /></view><view class="membership-copy"><text class="membership-title">会员全场 9 折</text><text class="membership-note">叠加积分抵现，更多省</text></view>
        <MotionButton class="membership-open" feedback="icon" aria-label="去开通" :busy="navigating" @click="navigate('/pages/membership/index')"><text>去开通</text></MotionButton>
      </view>
      <view v-if="submitted" class="shop-search-summary"><text>“{{ submitted }}”的示例商品</text><text class="shop-search-note">仅匹配当前两款示例</text></view>
      <view v-if="flash" class="shop-section">
        <view class="shop-section-heading"><view class="shop-heading-copy"><text class="shop-section-title">限时秒杀</text><view class="sale-note"><AppIcon asset="far-clock-a8c5bd" /><text>活动预览</text></view></view><MotionButton class="shop-more" feedback="icon" aria-label="更多秒杀商品" :busy="navigating" @click="notice('限时秒杀','当前保留一款示例，活动时间和更多商品待配置。')"><text>更多</text><AppIcon asset="fas-chevron-right-a8c5bd" /></MotionButton></view>
        <view class="flash-list"><MotionButton class="flash-card" feedback="icon" :aria-label="flash.title" :data-action="flash.title" :busy="navigating" @click="navigate(productUrl(flash))"><image class="flash-image" :src="flash.coverUrl" mode="aspectFill" /><text class="flash-title">{{ flash.title }}</text><view class="flash-prices"><text class="flash-price">¥{{ flash.price }}</text><text class="original-price">¥{{ flash.originalPrice }}</text></view></MotionButton></view>
      </view>
      <view v-if="recommended" class="shop-section">
        <view class="shop-section-heading"><text class="shop-section-title">为你推荐</text></view>
        <view class="shop-products"><MotionButton class="shop-product" feedback="icon" :aria-label="recommended.title" :data-action="recommended.title" :busy="navigating" @click="navigate(productUrl(recommended))"><image class="product-image" :src="recommended.coverUrl" mode="aspectFill" /><view class="product-copy"><text class="product-title">{{ recommended.title }}</text><view class="product-prices"><text class="product-price">¥{{ recommended.price }}</text><view class="member-price"><AppIcon asset="fas-crown-e9c08a" /><text>会员 ¥{{ recommended.memberPrice }}</text></view></view></view></MotionButton></view>
      </view>
      <view v-if="!flash && !recommended" class="shop-empty"><text class="shop-empty-title">暂无匹配的示例商品</text><text class="shop-empty-note">试试“颂钵”或“冥想垫”</text><MotionButton class="shop-reset" feedback="icon" @click="clearSearch">查看全部示例</MotionButton></view>
    </view>
  </ScreenFrame>
</template>
<style scoped>
.shop-content { padding: var(--top) 20px 24px; color: var(--text); }
.shop-toolbar { display: flex; align-items: center; gap: 8px; }.shop-search { display: flex; align-items: center; flex: 1; min-width: 0; height: 46px; padding-left: 13px; border: 1px solid rgba(255,255,255,.14); border-radius: 24px; background: rgba(255,255,255,.08); }
.shop-search-icon { display: flex; font-size: 14px; margin-right: 7px; }.shop-input { flex: 1; min-width: 0; width: 0; height: 44px; color: var(--text); font-size: 12px; }
.shop-submit,.shop-clear { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; flex-shrink: 0; color: var(--muted); font-size: 12px; }.shop-clear { width: 32px; font-size: 20px; }
.shop-cart { position: relative; display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; font-size: 20px; flex-shrink: 0; }.cart-count { position: absolute; top: 2px; right: 0; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; color: #0F1D1F; background: #E9C08A; font-size: 10px; font-weight: 600; line-height: 16px; }
.shop-shortcuts { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 8px; margin-top: 18px; }.shop-shortcut { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 2px 0; min-height: 76px; font-size: 11px; }
.shortcut-icon { display: flex; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: 16px; font-size: 19px; }.coins { background: linear-gradient(135deg,#E9C08A,#8F6B3A); }.tickets { background: linear-gradient(135deg,#3FB3A3,#2C8C82); }.gifts { background: linear-gradient(135deg,#4FA97F,#16302F); }.sale { background: linear-gradient(135deg,#D16B8A,#16302F); }
.shop-membership { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding: 12px; border: 1px solid rgba(233,192,138,.25); border-radius: 16px; background: linear-gradient(110deg,#17332F,#1D403C); }.membership-icon { display: flex; font-size: 18px; }.membership-copy { flex: 1; min-width: 0; }.membership-title { display: block; color: #E9C08A; font-size: 13px; font-weight: 600; line-height: 21px; }.membership-note { display: block; margin-top: 3px; color: #BCD5CF; font-size: 10px; line-height: 17px; }.membership-open { display: flex; align-items: center; justify-content: center; min-width: 66px; min-height: 44px; flex-shrink: 0; }.membership-open text { padding: 7px 12px; border-radius: 20px; background: linear-gradient(110deg,#E9C08A,#D9A35C); color: #0F1D1F; font-size: 11px; font-weight: 600; }
.shop-section { margin-top: 20px; }.shop-section-heading { display: flex; align-items: center; justify-content: space-between; min-height: 44px; gap: 8px; margin-bottom: 6px; }.shop-heading-copy { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; }.shop-section-title { font-size: 18px; font-weight: 700; line-height: 27px; }.sale-note { display: flex; align-items: center; gap: 4px; color: var(--muted); font-size: 10px; }.shop-more { display: flex; align-items: center; justify-content: flex-end; gap: 4px; min-width: 44px; min-height: 44px; color: var(--muted); font-size: 11px; }
.flash-list { display: flex; gap: 12px; }.flash-card { width: 112px; padding: 8px; border: 1px solid var(--line); border-radius: 13px; background-image: linear-gradient(var(--surface),var(--surface)); text-align: left; }.flash-image { width: 94px; height: 82px; border-radius: 8px; }.flash-title { display: block; margin-top: 8px; font-size: 12px; font-weight: 600; line-height: 20px; }.flash-prices { display: flex; flex-wrap: wrap; align-items: baseline; gap: 7px; margin-top: 4px; }.flash-price { color: var(--gold); font-size: 14px; font-weight: 700; }.original-price { color: var(--muted); font-size: 10px; text-decoration: line-through; }
.shop-products { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }.shop-product { min-width: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 16px; background-image: linear-gradient(var(--surface),var(--surface)); text-align: left; }.product-image { width: 100%; height: 116px; }.product-copy { padding: 11px; }.product-title { display: block; font-size: 13px; font-weight: 600; line-height: 21px; }.product-prices { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; margin-top: 8px; }.product-price { color: var(--gold); font-size: 15px; font-weight: 700; }.member-price { display: flex; align-items: center; gap: 3px; color: var(--muted); font-size: 10px; }
.shop-search-summary { margin-top: 20px; overflow-wrap: anywhere; font-size: 15px; }.shop-search-note { display: block; margin-top: 5px; color: var(--muted); font-size: 11px; }.shop-empty { padding: 44px 8px; text-align: center; }.shop-empty-title { display: block; font-size: 17px; font-weight: 600; }.shop-empty-note { display: block; margin-top: 8px; font-size: 12px; color: var(--muted); }.shop-reset { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; margin-top: 20px; padding: 8px 16px; border: 1px solid var(--accent); border-radius: 24px; color: var(--accent); font-size: 12px; }
@media (max-width:350px) { .shop-content { padding-left: 16px; padding-right: 16px; }.shop-search { padding-left: 10px; }.shop-search-icon { margin-right: 5px; }.shop-membership { gap: 8px; padding: 10px; }.shop-shortcut { font-size: 10px; }.product-copy { padding: 9px; }.product-image { height: 108px; } }
</style>
<style>
.pdf-screen[data-screen="7"] { min-height: 0; padding-bottom: 0; }
</style>
