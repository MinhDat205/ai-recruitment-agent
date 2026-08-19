import { Info } from 'lucide-react'
import type { Explanation, ExplanationPoint, ExplanationStatus } from './types'

// Man hinh nay bi srs-guard soi ky nhat (giong CriterionScoreBreakdown, ke hoach D3 muc 8): CAM
// tuyet doi to mau theo nguong, nhan dat/khong dat, icon check/x, cau goi y hanh dong ("nen
// tuyen"...). metCriteria/missingCriteria dung CHUNG MOT kieu chip trung tinh (bg-canvas/text-ink-
// muted) - KHONG do cho missing, KHONG xanh cho met, du hai danh sach CO Y NGHIA khac nhau (day la
// diem de vi pham nhat cua ca dot, theo yeu cau).
function ExplanationPointList({ title, points }: { title: string; points: ExplanationPoint[] }) {
  return (
    <div>
      <h4 className="text-sm font-medium text-ink">{title}</h4>
      {points.length === 0 ? (
        <p className="mt-1 text-sm text-ink-muted">Không có mục nào.</p>
      ) : (
        <ul className="mt-1 flex flex-col gap-1">
          {points.map((point, index) => (
            <li key={`${point.criterionName}-${index}`} className="text-sm text-ink">
              <span className="font-medium">{point.criterionName}</span> — {point.point}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function CriteriaChipList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h4 className="text-sm font-medium text-ink">{title}</h4>
      {items.length === 0 ? (
        <p className="mt-1 text-sm text-ink-muted">Không có mục nào.</p>
      ) : (
        <div className="mt-1 flex flex-wrap gap-1.5">
          {items.map((name, index) => (
            <span
              key={`${name}-${index}`}
              className="rounded-(--radius-badge) bg-canvas px-2 py-1 text-xs font-medium text-ink-muted"
            >
              {name}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

// Khu vuc bao cao tong hop (FR-H06, Dot 5) - TACH RIENG hoan toan khoi CriterionScoreBreakdown (bo
// khung/nen rieng) va khoi cot Hang/Tong diem cua bang ngoai (Q6, ke hoach D3: khong dat gan hai cot
// do de tranh gay lien tuong day la khuyen nghi chung ve ung vien). explanationStatus/explanation
// = ca hai null <=> don chua co lot DONE nao (xem comment types.ts) - khong hien gi ca, giong cach
// CriterionScoreBreakdown khong render khi criterionScores rong.
export function ExplanationReport({
  explanation,
  explanationStatus,
}: {
  explanation: Explanation | null
  explanationStatus: ExplanationStatus | null
}) {
  if (explanation === null && explanationStatus === null) {
    return null
  }

  return (
    <div className="mx-4 mb-4 rounded-(--radius-card) border border-line bg-surface p-4">
      {explanation === null ? (
        // PENDING/FAILED: cau chu THUAN TUY mo ta trang thai xu ly ky thuat, khong do loi cho ung
        // vien, khong goi y hanh dong (nut "Cham diem ho so" da co san o bang ngoai neu HR muon thu
        // lai - khong can nhac lai o day).
        <p className="text-sm text-ink-muted">
          {explanationStatus === 'PENDING'
            ? 'Báo cáo tổng hợp của AI đang được xử lý.'
            : 'Không tạo được báo cáo tổng hợp của AI cho lượt chấm này.'}
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {/* Disclaimer NGAY TREN DAU khoi (yeu cau bat buoc, khong phai chu nho phia duoi) - mau
              trung tinh (bg-canvas/text-ink-muted), KHONG dung mau warning/danger vi day khong phai
              loi hay canh bao, chi la lam ro pham vi trach nhiem. */}
          <p className="flex items-start gap-2 rounded-(--radius-badge) bg-canvas px-3 py-2 text-xs text-ink-muted">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            Tổng hợp của AI dựa trên các tiêu chí đã chấm — không phải khuyến nghị tuyển dụng, HR tự
            xem xét từng hồ sơ.
          </p>

          {/* summary la LLM sinh (Dot 2, FR-H06) - hien thi nguyen van, khong loc/sua boi code, cung
              tinh than voi reasoning o CriterionScoreBreakdown. */}
          <p className="text-sm whitespace-pre-wrap text-ink">{explanation.summary}</p>

          <ExplanationPointList title="Điểm mạnh" points={explanation.strengths} />
          <ExplanationPointList title="Điểm cần cải thiện" points={explanation.weaknesses} />
          <CriteriaChipList title="Tiêu chí đã thể hiện trong CV" items={explanation.metCriteria} />
          <CriteriaChipList title="Tiêu chí chưa thể hiện trong CV" items={explanation.missingCriteria} />
        </div>
      )}
    </div>
  )
}
