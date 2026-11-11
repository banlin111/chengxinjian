import type { Envelope, ErrorEnvelope } from '../../../../contracts/api';
import { config } from '../config';
export class ApiError extends Error {
  constructor(message: string, public code: string, public requestId?: string) { super(message); }
}
// Read-only bootstrap transport. Auth refresh/write idempotency are added with login/transaction features.
export function get<D>(path: string): Promise<D> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${config.apiBase}${path}`, method: 'GET', timeout: 10000,
      success(response) {
        const body = response.data as Envelope<D> | ErrorEnvelope;
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 'OK') resolve(body.data as D);
        else reject(new ApiError(body?.message || '暂时无法加载，请稍后再试', body?.code || 'HTTP_ERROR', body?.requestId));
      },
      fail() { reject(new ApiError('网络连接失败，请检查网络后重试', 'NETWORK_ERROR')); },
    });
  });
}
