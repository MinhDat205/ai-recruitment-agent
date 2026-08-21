import { http } from '../../lib/http'
import type { DashboardStats } from './types'

export async function getDashboardStatsRequest(): Promise<DashboardStats> {
  const response = await http.get<DashboardStats>('/hr/dashboard/stats')
  return response.data
}
