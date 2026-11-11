import { reactive, ref } from 'vue';
import { defineStore } from 'pinia';
export const usePreviewPreferences = defineStore('preview-preferences', () => {
  const speed = ref(1);
  const switches = reactive<Record<string,boolean>>({reminder:true,live:true,logistics:false,bell:true,background:false});
  const booked = reactive([false,false]);
  function restore() {
    try { const data=uni.getStorageSync('cxj.preferences.v1');
      if (!data || typeof data !== 'object') return;
      if ([.75,1,1.25,1.5,2].includes(data.speed)) speed.value=data.speed;
      Object.keys(switches).forEach(key=>{if(typeof data.switches?.[key]==='boolean') switches[key]=data.switches[key];});
    } catch { /* Defaults remain usable if storage is unavailable. */ }
  }
  function save() { try { uni.setStorageSync('cxj.preferences.v1',{speed:speed.value,switches:{...switches}}); } catch { uni.showToast({title:'本机偏好暂时无法保存',icon:'none'}); } }
  return {speed,switches,booked,restore,save};
});
