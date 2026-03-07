import { api } from './client'
import type { Device, LocationPingRequest } from '../types'

export const deviceApi = {
  list: () => api.get<Device[]>('/devices'),
  create: (name: string) => api.post<Device>('/devices', { name }),
  ping: (id: string, req: LocationPingRequest) =>
    api.post<Device>(`/devices/${id}/ping`, req),
}
