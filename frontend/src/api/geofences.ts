import { api } from './client'
import type { Geofence, GeofenceRequest } from '../types'

export const geofenceApi = {
  list: () => api.get<Geofence[]>('/geofences'),
  get: (id: string) => api.get<Geofence>(`/geofences/${id}`),
  create: (req: GeofenceRequest) => api.post<Geofence>('/geofences', req),
  update: (id: string, req: GeofenceRequest) => api.put<Geofence>(`/geofences/${id}`, req),
  delete: (id: string) => api.delete<void>(`/geofences/${id}`),
}
