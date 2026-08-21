import type { ApplicationStatus } from '../applications/types'
import type { JobStatus } from '../jobs/ownerTypes'

// Khop DashboardStatsResponse.ConversionFunnel (backend, F3/FR-H08). everInvited/everHired dem
// theo "DA TUNG dat trang thai" doc tu application_status_history (KHONG phai trang thai hien
// tai) - don da rut sau khi tung duoc moi phong van van tinh vao everInvited (FR-U06). Ty le %
// hien thi tinh o frontend tu ba so tho nay (backend khong tu lam tron).
export interface ConversionFunnel {
  appliedTotal: number
  everInvited: number
  everHired: number
}

// Khop DashboardStatsResponse.JobPerformanceItem. averageScore la number | null (AVG tren tap
// rong tra NULL khi scoredApplications = 0 - KHONG suy dien 0). everInvitedCount/everHiredCount
// cung nguyen tac "da tung" voi ConversionFunnel, ap dung nhat quan cho ca hai khoi thong ke.
export interface JobPerformanceItem {
  jobId: string
  title: string
  status: JobStatus
  recruitmentCycle: number
  totalApplications: number
  scoredApplications: number
  averageScore: number | null
  everInvitedCount: number
  everHiredCount: number
}

// Khop DashboardStatsResponse (backend, package dashboard.dto). GET /hr/dashboard/stats - pham vi
// TOAN CONG TY cua HR dang dang nhap, khong theo tung job. statusBreakdown luon du 5 key (0 neu
// cong ty chua co don o trang thai do).
export interface DashboardStats {
  totalApplications: number
  statusBreakdown: Record<ApplicationStatus, number>
  funnel: ConversionFunnel
  jobPerformance: JobPerformanceItem[]
}
