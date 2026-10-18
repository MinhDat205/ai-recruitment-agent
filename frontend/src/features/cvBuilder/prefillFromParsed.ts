import type { ResumeParsedPayload } from '../resumes/types'
import { EXCLUDED_INDUSTRY_CODE } from './cvBuilderLabels'
import { isValidDate, isValidGpa, normalizeSingleLine, type CvBuilderContentValues } from './cvBuilderSchema'

export interface PrefillResult {
  form: CvBuilderContentValues
  // So moc thoi gian khac rong bi bo vi khong dung dang R-F6 (R-E3) - > 0 thi hien cau prefillDateNotice.
  droppedDates: number
}

// FR-U12 R-E2-R-E4 - anh xa CV da trich xuat sang form, chi o frontend, khong goi AI. Chep NGUYEN VAN tung truong vao o
// cung ten; khong cat bot (gia tri vuot R-F5 van chep, loi hien khi bam "Lưu CV"). Ngoai le L12: skills/technologies bo
// phan tu trung. industryCodes = ma nganh cua danh muc (useCatalogsQuery); null khi chua tai -> de trong nganh.
export function prefillFromParsed(
  payload: ResumeParsedPayload,
  industryCodes: ReadonlySet<string> | null,
): PrefillResult {
  let droppedDates = 0

  // R-E3 - chi chep moc khop R-F6 (kiem tren gia tri da chuan hoa R-F3, chep chuoi goc); moc khac rong ma sai dang
  // ("Present", "06-2020", "Hiện nay") de trong va dem.
  function date(raw: string | null | undefined): string {
    if (raw == null) {
      return ''
    }
    const value = normalizeSingleLine(raw)
    if (value === null) {
      return ''
    }
    if (!isValidDate(value)) {
      droppedDates += 1
      return ''
    }
    return raw
  }

  const contact = payload.contact
  const industryCode = payload.industryCode ?? null
  const form: CvBuilderContentValues = {
    // contact = null -> khoi lien he trong.
    contact: {
      fullName: contact?.fullName ?? '',
      email: contact?.email ?? '',
      phone: contact?.phone ?? '',
      address: contact?.address ?? '',
      linkedin: contact?.linkedin ?? '',
    },
    currentTitle: payload.currentTitle ?? '',
    locationText: payload.locationText ?? '',
    // R-E4 - chi chep ma co trong danh muc (khong tinh "OTHER" - backend tu choi, R-F5).
    industryCode:
      industryCode !== null &&
      industryCodes !== null &&
      industryCodes.has(industryCode) &&
      industryCode !== EXCLUDED_INDUSTRY_CODE
        ? industryCode
        : '',
    education: (payload.education ?? []).map((e) => ({
      school: e.school ?? '',
      degree: e.degree ?? '',
      major: e.major ?? '',
      startDate: date(e.startDate),
      endDate: date(e.endDate),
      // R-E4 - GPA chep khi qua R-F9, nguoc lai de trong.
      gpa: e.gpa != null && isValidGpa(e.gpa) ? String(e.gpa) : '',
    })),
    experience: (payload.experience ?? []).map((e) => ({
      company: e.company ?? '',
      title: e.title ?? '',
      startDate: date(e.startDate),
      endDate: date(e.endDate),
      // R-E3 - "Dang lam" khong suy ra tu chu.
      current: false,
      description: e.description ?? '',
    })),
    skills: withoutDuplicates(payload.skills ?? []),
    certifications: (payload.certifications ?? []).map((c) => ({
      name: c.name ?? '',
      issuer: c.issuer ?? '',
      issueDate: date(c.issueDate),
    })),
    projects: (payload.projects ?? []).map((p) => ({
      name: p.name ?? '',
      description: p.description ?? '',
      technologies: withoutDuplicates(p.technologies ?? []),
    })),
  }
  return { form, droppedDates }
}

// L12 - luat R-K2 cua SkillTagInput (U14): so khop khong phan biet hoa/thuong, giu lan xuat hien DAU, chu cua phan tu
// giu lai khong doi. Tranh hai the trung key={skill} (bam xoa mat moi ban trung).
function withoutDuplicates(items: string[]): string[] {
  const seen = new Set<string>()
  const kept: string[] = []
  for (const item of items) {
    const key = item.toLowerCase()
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    kept.push(item)
  }
  return kept
}
