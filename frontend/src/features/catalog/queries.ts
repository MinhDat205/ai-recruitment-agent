import { useQuery } from '@tanstack/react-query'
import { getCatalogsRequest } from './api'

// Danh muc co dinh (chi doi qua migration backend) - staleTime dai de khong goi lai moi lan mo form.
const CATALOG_STALE_TIME_MS = 24 * 60 * 60 * 1000

export function useCatalogsQuery() {
  return useQuery({
    queryKey: ['public-catalogs'],
    queryFn: getCatalogsRequest,
    staleTime: CATALOG_STALE_TIME_MS,
  })
}
