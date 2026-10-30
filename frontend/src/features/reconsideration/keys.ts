import type { HrReconsiderationFilter } from './types'

// FR-C09 - khoa query H1, tach file rieng KHONG import gi de applicationDetail/queries.ts (applicationDetailInvalidateKeys)
// dung duoc ma khong tao vong import voi reconsideration/queries.ts.
export function hrReconsiderationsKeyPrefix(jobId: string) {
  return ['hr-reconsiderations', jobId]
}

export function hrReconsiderationsKey(jobId: string, filter: HrReconsiderationFilter) {
  return [...hrReconsiderationsKeyPrefix(jobId), filter]
}
