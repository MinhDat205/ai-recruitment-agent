import { useQuery } from '@tanstack/react-query'
import { getCompanyRequest } from './api'

export function useCompanyQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['public-company', id],
    queryFn: () => getCompanyRequest(id as string),
    enabled: Boolean(id),
  })
}
