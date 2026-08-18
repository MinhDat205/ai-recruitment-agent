import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { CriterionScoreItem } from './types'

// Anh xa section (schema CV D1, xem comment EvidenceEntry o types.ts) sang nhan tieng Viet de hien
// thi - KHONG hien chuoi tieng Anh tho cho HR. Fallback ve chinh chuoi section neu gap gia tri la
// (khong nen xay ra, CriterionScoringService da kiem o D2, nhung tang hien thi khong nen vo vi mot
// gia tri khong khop).
const SECTION_LABELS: Record<string, string> = {
  contact: 'Liên hệ',
  education: 'Học vấn',
  experience: 'Kinh nghiệm',
  skills: 'Kỹ năng',
  certifications: 'Chứng chỉ',
  projects: 'Dự án',
}

function sectionLabel(section: string): string {
  return SECTION_LABELS[section] ?? section
}

// Man hinh nay bi srs-guard soi ky nhat (ke hoach D3, Dot 5): CAM tuyet doi to mau diem theo
// nguong, nhan dat/khong dat, icon check/x, xep loai A/B/C, in dam dong diem cao hon. Diem/trong so
// hien thi dang so trung tinh, CUNG kieu chu voi moi cot khac trong bang - khong co className nao o
// day phu thuoc gia tri score/weightSnapshot.
function CriterionRow({ item }: { item: CriterionScoreItem }) {
  const [reasoningExpanded, setReasoningExpanded] = useState(false)

  return (
    <div className="rounded-(--radius-card) border border-line bg-surface">
      <button
        type="button"
        className="flex w-full items-center justify-between gap-4 px-4 py-2.5 text-left text-sm"
        onClick={() => setReasoningExpanded((expanded) => !expanded)}
        aria-expanded={reasoningExpanded}
      >
        <span className="flex items-center gap-2 text-ink">
          {reasoningExpanded ? (
            <ChevronDown className="h-3.5 w-3.5 shrink-0 text-ink-muted" aria-hidden="true" />
          ) : (
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-ink-muted" aria-hidden="true" />
          )}
          {item.criterionNameSnapshot}
        </span>
        <span className="flex shrink-0 items-center gap-4 text-ink-muted">
          <span>
            {item.score}/{item.maxScoreSnapshot} điểm
          </span>
          <span>Trọng số {item.weightSnapshot}%</span>
        </span>
      </button>
      {/* Q6 (ke hoach D3): reasoning CHI hien khi mo rong DUNG o tieu chi nay, tach biet khong gian
          voi cot Tong diem/Hang o bang ngoai - khong dat gan hai cot do de tranh gay lien tuong day
          la khuyen nghi chung ve ung vien. */}
      {reasoningExpanded && (
        <div className="border-t border-line px-4 py-3">
          <p className="text-xs text-ink-muted">
            Diễn giải của AI cho tiêu chí này — không phải khuyến nghị tuyển dụng
          </p>
          {/* reasoning la van ban AI da sinh tu D2, hien thi NGUYEN VAN - khong loc/sua/tom tat boi
              code (Q6, nguyen tac chong bia CLAUDE.md muc 2). Plain text, khong heading "Nhan
              xet:", khong bullet nhan manh. */}
          <p className="mt-1 text-sm whitespace-pre-wrap text-ink">{item.reasoning}</p>

          {/* Evidence (D4/FR-H06, Dot 5): trich xuat NGUYEN VAN tu CV, khong phai nhan xet cua AI
              nen KHONG dat chung nhan "khong phai khuyen nghi" nhu reasoning o tren (rang buoc da
              chot khi giao Dot 5 - evidence la loi trich dan that, khac ban chat voi reasoning la
              dien giai). Style dung dung PHASES/UI_GUIDE muc D4: vien trai brand, nen brand-light,
              font chu thuong (khong in nghieng/dam). */}
          {item.evidence.length > 0 ? (
            <div className="mt-3 flex flex-col gap-2">
              {item.evidence.map((entry, index) => (
                <blockquote
                  key={`${entry.section}-${index}`}
                  className="border-l-2 border-brand bg-brand-light px-3 py-2 text-sm font-normal text-ink"
                >
                  <p className="whitespace-pre-wrap">{entry.quote}</p>
                  <p className="mt-1 text-xs text-ink-muted">Nguồn: {sectionLabel(entry.section)}</p>
                </blockquote>
              ))}
            </div>
          ) : (
            // Mang evidence RONG la truong hop HOP LE khi score = 0 (Q2, ke hoach D2) - KHONG phai
            // loi/thieu du lieu, nen KHONG de trong tron (trong nhu vay se giong loi hien thi). Chu
            // van thuong (khong bang khung trich dan brand-light) - de khong gay hieu nham day la
            // MOT trich dan rong that su, chi la mot cau mo ta trung tinh ve tinh trang du lieu.
            <p className="mt-3 text-sm text-ink-muted">Không tìm thấy trích dẫn nào trong CV cho tiêu chí này.</p>
          )}
        </div>
      )}
    </div>
  )
}

export function CriterionScoreBreakdown({ criterionScores }: { criterionScores: CriterionScoreItem[] }) {
  return (
    <div className="flex flex-col gap-2 p-4">
      {criterionScores.map((item) => (
        <CriterionRow key={item.criterionNameSnapshot} item={item} />
      ))}
    </div>
  )
}
