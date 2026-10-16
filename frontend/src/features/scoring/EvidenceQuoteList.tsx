import type { EvidenceEntry } from './types'

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

// FR-H14 (UI.md 5a): TACH nguyen khoi evidence + cau rong tu CriterionScoreBreakdown (FR-H06/H09) de trang so sanh
// dung CUNG mot kieu trich dan. Markup/class giu nguyen tung ky tu - trang ho so don khong doi giao dien.
// emptyTextClassName: mau chu cua cau rong. Mac dinh = mau cu (on-surface-variant); trang so sanh truyen
// text-m3-on-surface khi hang co nen "Khac nhau" (on-surface-variant tren nen do chi 4.24:1 - UI.md 5d).
export function EvidenceQuoteList({
  evidence,
  emptyTextClassName = 'text-m3-on-surface-variant',
}: {
  evidence: EvidenceEntry[]
  emptyTextClassName?: string
}) {
  // Evidence (D4/FR-H06, Dot 5): trich xuat NGUYEN VAN tu CV, khong phai nhan xet cua AI
  // nen KHONG dat chung nhan "khong phai khuyen nghi" nhu reasoning o tren (rang buoc da
  // chot khi giao Dot 5 - evidence la loi trich dan that, khac ban chat voi reasoning la
  // dien giai). Style dung dung PHASES/UI_GUIDE muc D4: vien trai brand, nen brand-light,
  // font chu thuong (khong in nghieng/dam).
  return evidence.length > 0 ? (
    <div className="mt-3 flex flex-col gap-2">
      {evidence.map((entry, index) => (
        <blockquote
          key={`${entry.section}-${index}`}
          className="border-l-2 border-m3-primary bg-m3-primary-container px-3 py-2 text-sm font-normal text-m3-on-surface"
        >
          <p className="whitespace-pre-wrap">{entry.quote}</p>
          <p className="mt-1 text-xs text-m3-on-surface">Nguồn: {sectionLabel(entry.section)}</p>
        </blockquote>
      ))}
    </div>
  ) : (
    // Mang evidence RONG la truong hop HOP LE khi score = 0 (Q2, ke hoach D2) - KHONG phai
    // loi/thieu du lieu, nen KHONG de trong tron (trong nhu vay se giong loi hien thi). Chu
    // van thuong (khong bang khung trich dan brand-light) - de khong gay hieu nham day la
    // MOT trich dan rong that su, chi la mot cau mo ta trung tinh ve tinh trang du lieu.
    <p className={`mt-3 text-sm ${emptyTextClassName}`}>Không tìm thấy trích dẫn nào trong CV cho tiêu chí này.</p>
  )
}
