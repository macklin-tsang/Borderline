import { api } from './client'
import type { Device, LocationPingRequest } from '../types'

export const deviceApi = {
  list: () => api.get<Device[]>('/devices'),
  create: (name: string) => api.post<Device>('/devices', { name }),
  rename: (id: string, name: string) => api.patch<Device>(`/devices/${id}`, { name }),
  delete: (id: string) => api.delete<void>(`/devices/${id}`),
  ping: (id: string, req: LocationPingRequest) =>
    api.post<Device>(`/devices/${id}/location`, req),
}
