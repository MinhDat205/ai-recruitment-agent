import { useQuery } from '@tanstack/react-query'
import { getDashboardStatsRequest } from './api'

const DASHBOARD_STATS_KEY = ['dashboard-stats']

export function useDashboardStatsQuery() {
  return useQuery({
    queryKey: DASHBOARD_STATS_KEY,
    queryFn: getDashboardStatsRequest,
  })
}
