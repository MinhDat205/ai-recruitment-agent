import { ApplicationHistoryTimeline } from '../applications/ApplicationHistoryTimeline'
import { useApplicationHrHistoryQuery } from './queries'

// Tab "Lịch sử" (R-T12, R-D7) - goi E5 phia HR roi truyen du lieu vao ApplicationHistoryTimeline (chi
// con phan hien thi, R-C3). Dang tai / loi / rong / dong thoi gian theo dung component do.
export function HistoryTab({ applicationId }: { applicationId: string }) {
  const { data: history, isLoading, isError } = useApplicationHrHistoryQuery(applicationId)
  return <ApplicationHistoryTimeline history={history} isLoading={isLoading} isError={isError} />
}
