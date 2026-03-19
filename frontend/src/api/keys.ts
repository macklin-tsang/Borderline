import { api } from './client'
import type { ApiKey, ApiKeyCreatedResponse } from '../types'

export const keyApi = {
  list: () => api.get<ApiKey[]>('/keys'),
  create: (name: string, rateLimitPerMinute: number) =>
    api.post<ApiKeyCreatedResponse>('/keys', { name, rateLimitPerMinute, scopes: [] }),
  revoke: (id: string) => api.delete<void>(`/keys/${id}`),
}
