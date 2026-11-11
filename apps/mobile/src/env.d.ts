/// <reference types="vite/client" />
declare module '*.vue' { import type { DefineComponent } from 'vue'; const component: DefineComponent; export default component; }
interface ImportMetaEnv { readonly VITE_API_BASE_URL?: string; readonly VITE_USE_MOCK?: string; readonly VITE_DEMO_MODULES?: string; }
