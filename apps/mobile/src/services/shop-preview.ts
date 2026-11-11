export type ShopPreviewProduct = {
  id: string; title: string; detailTitle: string; coverUrl: string; detailCoverUrl: string;
  price: number; originalPrice: number | null; memberPrice: number | null; summary: string;
};
export const shopProducts: Record<'bowl' | 'mat', ShopPreviewProduct> = {
  bowl: { id:'demo-product-bowl',title:'藏式铜颂钵',detailTitle:'藏式手工铜颂钵 · 冥想音疗入门款',coverUrl:'/static/prototype/3b7f47075472.jpg',detailCoverUrl:'/static/prototype/aa74e248a33e.jpg',price:129,originalPrice:199,memberPrice:null,summary:'尼泊尔工匠手工锻造，音色绵长稳定，附槌与坐垫，含 8 节颂钵引导音频。' },
  mat: { id:'demo-product-mat',title:'天然亚麻冥想垫',detailTitle:'天然亚麻冥想垫',coverUrl:'/static/prototype/0ad22dca1722.jpg',detailCoverUrl:'/static/prototype/0ad22dca1722.jpg',price:168,originalPrice:null,memberPrice:151,summary:'天然亚麻冥想垫，陪伴日常静心练习。' },
};
export function productUrl(product: ShopPreviewProduct) { return `/pages/product/index?productId=${encodeURIComponent(product.id)}`; }
export function matchesShopQuery(product: ShopPreviewProduct, query: string) {
  const normalized = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return normalized.every(term => `${product.title}${product.summary}`.toLowerCase().includes(term));
}
