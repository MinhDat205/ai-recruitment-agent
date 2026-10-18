import type { JobSearchCondition, JobSearchInterpretation } from './types'

// Q-T5 - cach hieu cua AI (tru the vua xoa, neu co) -> URLSearchParams cua bo loc U07, cung ten tham so useJobFilters
// doc. KHONG keyword, sort, page; phan mo ta KHONG duoc chuyen (U07 khong co) va khong bao gio len URL (Q-T6).
export function buildFilterParams(
  interpretation: JobSearchInterpretation,
  removed?: JobSearchCondition,
): URLSearchParams {
  const params = new URLSearchParams()

  for (const code of interpretation.locationCodes) {
    if (!(removed?.kind === 'location' && removed.code === code)) {
      params.append('locationCode', code)
    }
  }
  for (const code of interpretation.industryCodes) {
    if (!(removed?.kind === 'industry' && removed.code === code)) {
      params.append('categoryCode', code)
    }
  }
  if (removed?.kind !== 'salary') {
    if (interpretation.salaryMin != null) {
      params.set('salaryMin', String(interpretation.salaryMin))
    }
    if (interpretation.salaryMax != null) {
      params.set('salaryMax', String(interpretation.salaryMax))
    }
  }
  for (const mode of interpretation.workModes) {
    if (!(removed?.kind === 'workMode' && removed.code === mode)) {
      params.append('workMode', mode)
    }
  }

  return params
}
