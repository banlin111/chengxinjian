<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { shopProducts } from '../../services/shop-preview';
const product = ref(shopProducts.bowl);
const valid = ref(true);
const selectedSpec = ref(0);
const dialogOpen = ref(false);
const isBowl = computed(() => product.value.id === shopProducts.bowl.id);
const specs = ['10cm 入门款','12cm 进阶款','14cm 专业款'];
// The prototype only supplies the starting price, not prices for larger variants.
const priceKnown = computed(() => !isBowl.value || selectedSpec.value === 0);
const specLabel = computed(() => isBowl.value ? specs[selectedSpec.value] : '规格待确认');
const reviewImage = '/static/prototype/8549d3ee0646.jpg';
onLoad(options => {
  if (!options?.productId) return;
  const found = Object.values(shopProducts).find(item => item.id === options.productId);
  valid.value = Boolean(found);
  if (found) product.value = found;
});
function notice(title: string, content: string) {
  if (dialogOpen.value) return;
  dialogOpen.value = true;
  uni.showModal({title,content,showCancel:false,complete:()=>{dialogOpen.value=false;}});
}
function purchase(kind: 'cart' | 'buy') {
  notice(kind === 'cart' ? '加入购物车 · 演示' : '立即购买 · 演示', `${product.value.title}\n${specLabel.value}${priceKnown.value ? `\n示例价格 ¥${product.value.price}` : '\n此规格价格待配置'}\n当前仅展示操作流程，不会提交购物车、生成订单或发起支付。`);
}
function previewImage(url: string) {
  if (dialogOpen.value) return;
  uni.previewImage({urls:[url],current:url,fail:()=>uni.showToast({title:'图片暂时无法打开',icon:'none'})});
}
</script>
<template>
  <ScreenFrame v-if="valid" :screen="8" footer>
    <view class="product-hero">
      <MotionButton class="product-image-open" feedback="icon" aria-label="查看商品大图" :busy="dialogOpen" @click="previewImage(product.detailCoverUrl)"><image class="product-detail-cover" mode="aspectFill" :src="product.detailCoverUrl" /></MotionButton>
      <view class="product-image-shade" />
      <view class="product-member-badge"><AppIcon asset="fas-crown-e9c08a" /><text>{{ isBowl ? '会员 9 折' : `会员 ¥${product.memberPrice}` }}</text></view>
      <text class="product-image-count">1/1</text>
    </view>
    <view class="product-detail-content screen-content">
      <view class="product-price-row">
        <text class="product-detail-price" :class="{ 'price-pending': !priceKnown }">{{ priceKnown ? `¥${product.price}` : '价格待配置' }}</text>
        <text v-if="priceKnown && product.originalPrice" class="product-original-price">¥{{ product.originalPrice }}</text>
        <text v-if="isBowl && priceKnown" class="product-points">积分可抵 ¥5</text>
        <text v-if="!isBowl" class="product-member-price">会员 ¥{{ product.memberPrice }}</text>
      </view>
      <text class="product-detail-title">{{ product.detailTitle }}</text>
      <text class="product-summary">{{ product.summary }}</text>
      <view class="product-specs-section">
        <view class="product-section-heading"><text class="product-section-title">规格</text><text class="selected-spec-note">{{ isBowl ? '单选' : '待配置' }}</text></view>
        <view v-if="isBowl" class="product-specs">
          <MotionButton v-for="(spec,index) in specs" :key="spec" class="product-spec" :class="{ 'spec-selected': selectedSpec === index }" feedback="icon" :aria-label="spec" :aria-pressed="selectedSpec === index ? 'true' : 'false'" :data-action="spec" :busy="dialogOpen" @click="selectedSpec = index"><text>{{ spec }}</text></MotionButton>
        </view>
        <text v-else class="product-pending-note">规格及库存待确认</text>
      </view>
      <MotionButton v-if="isBowl" class="product-services" feedback="icon" aria-label="查看商品服务说明" :busy="dialogOpen" @click="notice('商品服务 · 示例','顺丰包邮、7天无理由、碎裂补寄为原型示例，正式服务规则待商家确认。')"><view class="service-items"><view v-for="label in ['顺丰包邮','7 天无理由','碎裂补寄']" :key="label" class="service-item"><AppIcon asset="fas-check-3fb3a3" /><text>{{ label }}</text></view></view><AppIcon asset="fas-chevron-right-a8c5bd" /></MotionButton>
      <view v-if="isBowl" class="product-reviews">
        <view class="product-review-heading"><text class="product-section-title">用户评价（486）</text><MotionButton class="product-reviews-more" feedback="icon" aria-label="查看全部评价" data-action="全部" :busy="dialogOpen" @click="notice('评价预览','示例评价：音色纯正，包装完整。\n当前仅保留一条演示，评分与数量均为原型示例。')"><text>全部</text><AppIcon asset="fas-chevron-right-a8c5bd" /></MotionButton></view>
        <view class="review-summary"><text class="review-rating">4.9</text><text class="review-tags">好评率 98% · 音色纯正 32 · 静心 21</text></view>
        <MotionButton class="review-photo" feedback="icon" aria-label="查看评价示例图片" :busy="dialogOpen" @click="previewImage(reviewImage)"><image :src="reviewImage" mode="aspectFill" /></MotionButton>
      </view>
      <view v-else class="product-reviews product-reviews-empty"><text class="product-section-title">用户评价</text><text class="product-pending-note">暂无这款商品的评价示例</text></view>
    </view>
    <view class="product-footer screen-footer">
      <view class="product-actions">
        <MotionButton class="product-mini-action" feedback="icon" aria-label="客服" data-action="客服" :busy="dialogOpen" @click="notice('客服','客服入口已预留，正式联系方式待甲方提供。')"><AppIcon asset="fas-comment-dots-a8c5bd" /><text>客服</text></MotionButton>
        <MotionButton class="product-mini-action" feedback="icon" aria-label="购物车" data-action="购物车" :busy="dialogOpen" @click="notice('购物车演示','尚未连接真实购物车，当前操作不会改变购物车数量。')"><AppIcon asset="fas-cart-plus-a8c5bd" /><text>购物车</text></MotionButton>
        <MotionButton class="product-add" feedback="icon" aria-label="加入购物车" data-action="加入购物车" :busy="dialogOpen" @click="purchase('cart')">加入购物车</MotionButton>
        <MotionButton class="product-buy" feedback="icon" aria-label="立即购买" data-action="立即购买" :busy="dialogOpen" @click="purchase('buy')">立即购买</MotionButton>
      </view>
    </view>
  </ScreenFrame>
  <ScreenFrame v-else :screen="8"><view class="page-header"><text class="page-title">暂时找不到这件商品</text><text class="page-subtitle">请返回商城重新选择。</text></view></ScreenFrame>
</template>
<style scoped>
.product-hero { position: absolute; top: 0; left: 0; right: 0; height: calc(var(--top) + 256px); }
.product-image-open { display: block; width: 100%; height: 100%; }.product-detail-cover { width: 100%; height: 100%; }
.product-image-shade { position: absolute; inset: 0; pointer-events: none; background: linear-gradient(180deg,rgba(15,29,31,.12),transparent 48%,var(--bg)); }
.product-member-badge { position: absolute; top: var(--top); left: 20px; display: flex; align-items: center; gap: 5px; padding: 5px 10px; border-radius: 20px; background: rgba(0,0,0,.4); color: #fff; font-size: 10px; pointer-events: none; }
.product-image-count { position: absolute; top: var(--top); right: 20px; padding: 5px 10px; border-radius: 20px; background: rgba(0,0,0,.4); color: #fff; font-size: 10px; pointer-events: none; }
.product-detail-content { padding: calc(var(--top) + 212px) 20px 0; color: var(--text); pointer-events: none; }.product-detail-content > view, .product-services { pointer-events: auto; }
.product-price-row { display: flex; align-items: baseline; flex-wrap: wrap; gap: 10px; }.product-detail-price { color: var(--gold); font-size: 30px; font-weight: 700; line-height: 40px; }.product-detail-price.price-pending { font-size: 22px; }.product-original-price { color: var(--muted); font-size: 12px; text-decoration: line-through; }.product-points { padding: 2px 7px; border-radius: 5px; background: rgba(63,179,163,.2); color: #78CABB; font-size: 10px; }.product-member-price { color: var(--muted); font-size: 12px; }
.product-detail-title { display: block; margin-top: 8px; font-size: 19px; font-weight: 700; line-height: 28px; }.product-summary { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; line-height: 21px; }
.product-specs-section { margin-top: 18px; }.product-section-heading { display: flex; align-items: baseline; gap: 8px; }.product-section-title { font-size: 14px; font-weight: 600; line-height: 22px; }.selected-spec-note { font-size: 10px; color: var(--muted); }
.product-specs { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }.product-spec { display: flex; align-items: center; justify-content: center; min-height: 44px; min-width: 44px; color: var(--muted); font-size: 11px; }.product-spec text { display: block; padding: 7px 11px; border-radius: 22px; border: 1px solid var(--line); background: var(--surface); transition: background-color 160ms ease,border-color 160ms ease,color 160ms ease; }.product-spec.spec-selected text { background: #3FB3A3; border-color: #3FB3A3; color: #0F1D1F; font-weight: 600; }
.product-pending-note { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; line-height: 21px; }
.product-services { display: flex; align-items: center; justify-content: space-between; gap: 8px; width: 100%; min-height: 50px; margin-top: 16px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 13px; background-image: linear-gradient(var(--surface),var(--surface)); color: var(--muted); font-size: 10px; text-align: left; }.service-items { display: flex; flex-wrap: wrap; gap: 8px 12px; }.service-item { display: flex; align-items: center; gap: 4px; }
.product-reviews { margin-top: 14px; padding: 10px 14px 14px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }.product-review-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.product-reviews-more { display: flex; align-items: center; justify-content: flex-end; gap: 5px; min-width: 44px; min-height: 44px; color: var(--muted); font-size: 11px; }.review-summary { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; margin-top: 2px; }.review-rating { color: var(--gold); font-size: 19px; font-weight: 700; }.review-tags { color: var(--muted); font-size: 10px; line-height: 18px; }.review-photo { display: block; width: 56px; height: 56px; margin-top: 12px; border-radius: 9px; overflow: hidden; }.review-photo image { width: 100%; height: 100%; }.product-reviews-empty { padding-top: 14px; }
.product-footer.screen-footer { padding-left: 20px; padding-right: 20px; color: var(--text); background: linear-gradient(transparent,var(--bg) 30%); }.product-actions { display: flex; align-items: center; gap: 6px; padding: 10px; border: 1px solid var(--line); border-radius: 16px; background: var(--bg); }
.product-mini-action { display: flex; flex-direction: column; align-items: center; justify-content: center; flex-shrink: 0; gap: 4px; width: 44px; min-height: 46px; color: var(--muted); font-size: 18px; }.product-mini-action text { font-size: 10px; line-height: 15px; }
.product-add,.product-buy { display: flex; align-items: center; justify-content: center; flex: 1; min-width: 0; min-height: 46px; padding: 8px 4px; border-radius: 11px; font-size: 12px; line-height: 18px; font-weight: 600; text-align: center; }.product-add { background-image: linear-gradient(rgba(255,255,255,.12),rgba(255,255,255,.12)); }.product-buy { color: #0F1D1F; background-image: linear-gradient(110deg,#E9C08A,#D9A35C); }
@media (max-width:350px) { .product-detail-content { padding-left: 16px; padding-right: 16px; }.product-footer.screen-footer { padding-left: 12px; padding-right: 12px; }.product-actions { gap: 5px; padding: 9px; }.product-detail-title { font-size: 18px; }.product-spec text { padding-left: 9px; padding-right: 9px; }.service-items { gap: 7px; } }
@media (prefers-reduced-motion:reduce) { .product-spec text { transition: none; } }
</style>
<style>
.pdf-screen[data-screen="8"].screen-with-footer { padding-bottom: calc(112px + env(safe-area-inset-bottom)); }
</style>
