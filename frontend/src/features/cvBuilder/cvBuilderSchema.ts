import { z } from 'zod'
import {
  CV_BUILDER_BLOCK_LABELS,
  CV_BUILDER_ERROR_TEMPLATES,
  CV_BUILDER_FIELD_LABELS,
  CV_BUILDER_LIMITS,
  EXCLUDED_INDUSTRY_CODE,
  type CvBuilderBlock,
} from './cvBuilderLabels'
import type { CvBuilderForm, CvBuilderRequest } from './types'

// FR-U12 - gia tri form tren trinh duyet: moi o chu la string ('' = trong), GPA la chuoi go vao, "Dang lam" la boolean.
// Chuan hoa R-F3; NFC chi dung khi dem do dai (L56), du lieu gui di khong NFC (backend chuan hoa). Kiem R-F5-R-F10 voi DUNG cau loi backend (REQUIREMENT 4.4, R-F11). KHONG
// kiem ky tu dieu khien/dinh dang, glyph va tu qua dai (R-F4, R-F12) - backend bao, cau hien o thanh thao tac.

const text = z.string()

const contactValuesSchema = z.object({
  fullName: text,
  email: text,
  phone: text,
  address: text,
  linkedin: text,
})

const educationValuesSchema = z.object({
  school: text,
  degree: text,
  major: text,
  startDate: text,
  endDate: text,
  gpa: text,
})

const experienceValuesSchema = z.object({
  company: text,
  title: text,
  startDate: text,
  endDate: text,
  current: z.boolean(),
  description: text,
})

const certificationValuesSchema = z.object({
  name: text,
  issuer: text,
  issueDate: text,
})

const projectValuesSchema = z.object({
  name: text,
  description: text,
  technologies: z.array(text),
})

export const cvBuilderValuesSchema = z.object({
  template: z.enum(['CLASSIC', 'MODERN'], { error: CV_BUILDER_ERROR_TEMPLATES.templateRequired }),
  versionLabel: text,
  contact: contactValuesSchema,
  currentTitle: text,
  locationText: text,
  industryCode: text,
  education: z.array(educationValuesSchema),
  experience: z.array(experienceValuesSchema),
  skills: z.array(text),
  certifications: z.array(certificationValuesSchema),
  projects: z.array(projectValuesSchema),
})

export type CvBuilderFormValues = z.infer<typeof cvBuilderValuesSchema>
export type CvBuilderEducationValues = CvBuilderFormValues['education'][number]
export type CvBuilderExperienceValues = CvBuilderFormValues['experience'][number]
export type CvBuilderCertificationValues = CvBuilderFormValues['certifications'][number]
export type CvBuilderProjectValues = CvBuilderFormValues['projects'][number]
// Phan form duoc dien san (R-E4: mau va ten phien ban giu nhu dang chon).
export type CvBuilderContentValues = Omit<CvBuilderFormValues, 'template' | 'versionLabel'>

export const EMPTY_EDUCATION: CvBuilderEducationValues = {
  school: '',
  degree: '',
  major: '',
  startDate: '',
  endDate: '',
  gpa: '',
}

export const EMPTY_EXPERIENCE: CvBuilderExperienceValues = {
  company: '',
  title: '',
  startDate: '',
  endDate: '',
  current: false,
  description: '',
}

export const EMPTY_CERTIFICATION: CvBuilderCertificationValues = { name: '', issuer: '', issueDate: '' }

export const EMPTY_PROJECT: CvBuilderProjectValues = { name: '', description: '', technologies: [] }

export const EMPTY_CONTENT: CvBuilderContentValues = {
  contact: { fullName: '', email: '', phone: '', address: '', linkedin: '' },
  currentTitle: '',
  locationText: '',
  industryCode: '',
  education: [],
  experience: [],
  skills: [],
  certifications: [],
  projects: [],
}

// UI.md muc 6 "Rong" - mau CLASSIC chon san.
export const EMPTY_FORM_VALUES: CvBuilderFormValues = { template: 'CLASSIC', versionLabel: '', ...EMPTY_CONTENT }

// ---------------------------------------------------------------------------------------------------------------------
// R-F3 (tru buoc 1 NFC). Buoc 5 strip theo String.strip() cua Java (Character.isWhitespace) - sau buoc 3 moi khoang
// trang Zs da thanh dau cach nen chi con cac ky tu duoi day. So bang ma ky tu (khong regex: luat no-control-regex).

// Character.isWhitespace cua Java ngoai Zs: tab, LF, VT, FF, CR, U+001C-U+001F, U+2028 (Zl), U+2029 (Zp).
function isJavaWhitespace(code: number): boolean {
  return (
    code === 0x20 ||
    (code >= 0x09 && code <= 0x0d) ||
    (code >= 0x1c && code <= 0x1f) ||
    code === 0x2028 ||
    code === 0x2029
  )
}

// Ky tu khoang trang ASCII ma \s cua regex Java khop: dau cach, tab, LF, VT, FF, CR.
function isAsciiWhitespace(code: number): boolean {
  return code === 0x20 || (code >= 0x09 && code <= 0x0d)
}

function javaStrip(value: string): string {
  let start = 0
  let end = value.length
  while (start < end && isJavaWhitespace(value.charCodeAt(start))) {
    start += 1
  }
  while (end > start && isJavaWhitespace(value.charCodeAt(end - 1))) {
    end -= 1
  }
  return value.slice(start, end)
}

function normalizeText(value: string, multiLine: boolean): string | null {
  // 2. CRLF, CR -> LF; 3. tab va moi khoang trang loai Zs (gom U+00A0, U+202F) -> dau cach thuong.
  let s = value.replace(/\r\n?/g, '\n').replace(/[\t\p{Zs}]/gu, ' ')
  // 4. o mot dong: xuong dong -> dau cach.
  if (!multiLine) {
    s = s.replace(/\n/g, ' ')
  }
  // 5. strip hai dau; rong -> null.
  s = javaStrip(s)
  return s.length === 0 ? null : s
}

// L56 - do dai dem SAU NFC (backend dem String.length() sau NFC - R-F3 buoc 1) de nguoi go tieng Viet dang to hop khong
// bi chan oan. Chi dung de dem: du lieu gui di giu nguyen, backend tu chuan hoa.
export function nfcLength(value: string): number {
  return value.normalize('NFC').length
}

// O mot dong: moi o tru hai o description (gom versionLabel, phan tu skills/technologies).
export function normalizeSingleLine(value: string): string | null {
  return normalizeText(value, false)
}

// O nhieu dong: experience[].description, projects[].description.
export function normalizeMultiLine(value: string): string | null {
  return normalizeText(value, true)
}

// ---------------------------------------------------------------------------------------------------------------------
// R-F6 - MM/YYYY (thang 01-12, du 2 chu so) hoac YYYY; nam 1950-2100. Chi chu so ASCII (cung regex backend).

const MONTH_YEAR = /^(0[1-9]|1[0-2])\/([0-9]{4})$/
const YEAR_ONLY = /^([0-9]{4})$/

interface MonthYear {
  year: number
  month: number | null
}

function parseDate(value: string): MonthYear | null {
  const monthYear = MONTH_YEAR.exec(value)
  const yearOnly = YEAR_ONLY.exec(value)
  let parsed: MonthYear
  if (monthYear) {
    parsed = { year: Number(monthYear[2]), month: Number(monthYear[1]) }
  } else if (yearOnly) {
    parsed = { year: Number(yearOnly[1]), month: null }
  } else {
    return null
  }
  if (parsed.year < CV_BUILDER_LIMITS.minYear || parsed.year > CV_BUILDER_LIMITS.maxYear) {
    return null
  }
  return parsed
}

// Moc da chuan hoa co dung dang R-F6 khong (dien san R-E3 dung chung).
export function isValidDate(value: string): boolean {
  return parseDate(value) !== null
}

// R-F7 - so (nam, thang); mot ben chi co nam -> so theo nam. Bang nhau hop le.
function startAfterEnd(start: MonthYear, end: MonthYear): boolean {
  if (start.year !== end.year) {
    return start.year > end.year
  }
  return start.month !== null && end.month !== null && start.month > end.month
}

// ---------------------------------------------------------------------------------------------------------------------
// R-F9 - GPA. O nhap la chuoi: chi nhan so thap phan dau cham ("3.5"); dang khac -> NaN -> loi GPA (backend cung bao
// NaN bang cau nay).

const GPA_INPUT = /^[0-9]+(\.[0-9]+)?$/

export function gpaFromInput(raw: string): number | null {
  const value = normalizeSingleLine(raw)
  if (value === null) {
    return null
  }
  return GPA_INPUT.test(value) ? Number(value) : Number.NaN
}

// [0, 10], toi da 2 chu so thap phan sau khi bo so 0 cuoi (BigDecimal.valueOf(gpa).stripTrailingZeros().scale()).
// String(number) cua JS khong co so 0 cuoi; dang mu (vd 1e-7) chi gap o so rat nho -> scale > 2 -> loi.
export function isValidGpa(gpa: number): boolean {
  if (!Number.isFinite(gpa) || gpa < 0 || gpa > CV_BUILDER_LIMITS.gpaMax) {
    return false
  }
  const printed = String(gpa)
  if (printed.includes('e')) {
    return false
  }
  const dot = printed.indexOf('.')
  const scale = dot < 0 ? 0 : printed.length - dot - 1
  return scale <= CV_BUILDER_LIMITS.gpaMaxScale
}

// R-F5 - email co thi phai khop ^[^@\s]+@[^@\s]+$ (\s cua Java: chi khoang trang ASCII): dung mot '@', hai phia khac
// rong, khong co khoang trang ASCII.
function isValidEmail(value: string): boolean {
  const at = value.indexOf('@')
  if (at <= 0 || at === value.length - 1 || value.indexOf('@', at + 1) >= 0) {
    return false
  }
  for (let i = 0; i < value.length; i++) {
    if (isAsciiWhitespace(value.charCodeAt(i))) {
      return false
    }
  }
  return true
}

// ---------------------------------------------------------------------------------------------------------------------
// Ghep cau loi R-F11 tu mau REQUIREMENT 4.4 - chi tu nhan o va so thu tu (1-based), khong chua gia tri da go.

// Khoa loi R-F10 trong formState.errors. KHONG dung "root": handleSubmit cua react-hook-form xoa errors.root sau khi
// chay resolver (index.esm.mjs, unset(_formState.errors, ROOT_ERROR_TYPE)) -> form chi co loi nay se bi coi la hop le.
export const CV_EMPTY_ERROR_KEY = 'cvEmpty'

type IssuePath = (string | number)[]
type Report = (path: IssuePath, message: string) => void

const T = CV_BUILDER_ERROR_TEMPLATES
const F = CV_BUILDER_FIELD_LABELS
const L = CV_BUILDER_LIMITS

function fill(template: string, parts: Partial<Record<'Ô' | 'Mục' | 'Khối' | 'n', string | number>>): string {
  return template.replace(/\{(Ô|Mục|Khối|n)\}/g, (whole, key: 'Ô' | 'Mục' | 'Khối' | 'n') =>
    parts[key] === undefined ? whole : String(parts[key]),
  )
}

// "{Khối} {i}" - muc trong khoi danh sach.
function itemSubject(block: CvBuilderBlock, i: number): string {
  return `${CV_BUILDER_BLOCK_LABELS[block]} ${i}`
}

// "{Khối} {i}: {Ô}" - o trong muc (CvBuilderField.Path.subject()).
function fieldSubject(block: CvBuilderBlock, i: number, label: string): string {
  return `${itemSubject(block, i)}: ${label}`
}

// Bat buoc -> do dai (L5). Tra ve false khi da bao loi.
function checkText(
  report: Report,
  path: IssuePath,
  value: string | null,
  subject: string,
  required: boolean,
  maxLength?: number,
): boolean {
  if (value === null) {
    if (required) {
      report(path, fill(T.required, { Ô: subject }))
      return false
    }
    return true
  }
  if (maxLength !== undefined && nfcLength(value) > maxLength) {
    report(path, fill(T.tooLong, { Ô: subject, n: maxLength }))
    return false
  }
  return true
}

// R-F6. Tra ve moc da phan tich (null khi trong hoac sai dang - sai dang thi da bao loi).
function checkDate(report: Report, path: IssuePath, raw: string, subject: string): MonthYear | null {
  const value = normalizeSingleLine(raw)
  if (value === null) {
    return null
  }
  const parsed = parseDate(value)
  if (parsed === null) {
    report(path, fill(T.invalidDate, { Ô: subject }))
  }
  return parsed
}

function checkList(report: Report, block: CvBuilderBlock, size: number, maxItems: number) {
  if (size > maxItems) {
    report([block], fill(T.tooManyItems, { Khối: CV_BUILDER_BLOCK_LABELS[block], n: maxItems }))
  }
}

function checkForm(values: CvBuilderFormValues, industryCodes: ReadonlySet<string> | null, report: Report) {
  const { contact } = values
  checkText(report, ['contact', 'fullName'], normalizeSingleLine(contact.fullName), F.fullName, true, L.fullName)
  const email = normalizeSingleLine(contact.email)
  if (checkText(report, ['contact', 'email'], email, F.email, false, L.email) && email !== null && !isValidEmail(email)) {
    report(['contact', 'email'], T.invalidEmail)
  }
  checkText(report, ['contact', 'phone'], normalizeSingleLine(contact.phone), F.phone, false, L.phone)
  checkText(report, ['contact', 'address'], normalizeSingleLine(contact.address), F.address, false, L.address)
  checkText(report, ['contact', 'linkedin'], normalizeSingleLine(contact.linkedin), F.linkedin, false, L.linkedin)
  checkText(report, ['currentTitle'], normalizeSingleLine(values.currentTitle), F.currentTitle, false, L.currentTitle)
  checkText(report, ['locationText'], normalizeSingleLine(values.locationText), F.locationText, false, L.locationText)
  // Danh muc chua tai xong (null) -> de backend kiem; Select chi cho chon ma trong danh muc.
  const industryCode = normalizeSingleLine(values.industryCode)
  if (
    industryCode !== null &&
    industryCodes !== null &&
    (!industryCodes.has(industryCode) || industryCode === EXCLUDED_INDUSTRY_CODE)
  ) {
    report(['industryCode'], T.unknownIndustry)
  }

  checkList(report, 'education', values.education.length, L.educationItems)
  values.education.forEach((e, index) => {
    const n = index + 1
    const at = (key: string): IssuePath => ['education', index, key]
    checkText(report, at('school'), normalizeSingleLine(e.school), fieldSubject('education', n, F.school), true, L.school)
    checkText(report, at('degree'), normalizeSingleLine(e.degree), fieldSubject('education', n, F.degree), false, L.degree)
    checkText(report, at('major'), normalizeSingleLine(e.major), fieldSubject('education', n, F.major), false, L.major)
    const start = checkDate(report, at('startDate'), e.startDate, fieldSubject('education', n, F.startDate))
    const end = checkDate(report, at('endDate'), e.endDate, fieldSubject('education', n, F.endDate))
    // L19 - cau GPA co tien to muc hoc van.
    const gpa = gpaFromInput(e.gpa)
    if (gpa !== null && !isValidGpa(gpa)) {
      report(at('gpa'), fill(T.invalidGpa, { Mục: itemSubject('education', n) }))
    }
    if (start && end && startAfterEnd(start, end)) {
      report(at('endDate'), fill(T.startAfterEnd, { Mục: itemSubject('education', n) }))
    }
  })

  checkList(report, 'experience', values.experience.length, L.experienceItems)
  values.experience.forEach((e, index) => {
    const n = index + 1
    const at = (key: string): IssuePath => ['experience', index, key]
    checkText(report, at('company'), normalizeSingleLine(e.company), fieldSubject('experience', n, F.company), true, L.company)
    checkText(
      report,
      at('title'),
      normalizeSingleLine(e.title),
      fieldSubject('experience', n, F.experienceTitle),
      false,
      L.experienceTitle,
    )
    const start = checkDate(report, at('startDate'), e.startDate, fieldSubject('experience', n, F.startDate))
    const end = checkDate(report, at('endDate'), e.endDate, fieldSubject('experience', n, F.endDate))
    checkText(
      report,
      at('description'),
      normalizeMultiLine(e.description),
      fieldSubject('experience', n, F.description),
      false,
      L.description,
    )
    if (start && end && startAfterEnd(start, end)) {
      report(at('endDate'), fill(T.startAfterEnd, { Mục: itemSubject('experience', n) }))
    }
    // R-F8 - dang lam ma co moc ket thuc (ke ca moc sai dang).
    if (e.current && normalizeSingleLine(e.endDate) !== null) {
      report(at('endDate'), fill(T.currentWithEndDate, { Mục: itemSubject('experience', n) }))
    }
  })

  checkList(report, 'skills', values.skills.length, L.skillsItems)
  values.skills.forEach((skill, index) => {
    // L18 - phan tu skills: "Kỹ năng {i} ..."; loi gan vao ca khoi (o the khong co o rieng cho tung phan tu).
    checkText(report, ['skills'], normalizeSingleLine(skill), itemSubject('skills', index + 1), true, L.tag)
  })

  checkList(report, 'certifications', values.certifications.length, L.certificationsItems)
  values.certifications.forEach((c, index) => {
    const n = index + 1
    const at = (key: string): IssuePath => ['certifications', index, key]
    checkText(
      report,
      at('name'),
      normalizeSingleLine(c.name),
      fieldSubject('certifications', n, F.certificationName),
      true,
      L.certificationName,
    )
    checkText(report, at('issuer'), normalizeSingleLine(c.issuer), fieldSubject('certifications', n, F.issuer), false, L.issuer)
    checkDate(report, at('issueDate'), c.issueDate, fieldSubject('certifications', n, F.issueDate))
  })

  checkList(report, 'projects', values.projects.length, L.projectsItems)
  values.projects.forEach((p, index) => {
    const n = index + 1
    const at = (key: string): IssuePath => ['projects', index, key]
    checkText(report, at('name'), normalizeSingleLine(p.name), fieldSubject('projects', n, F.projectName), true, L.projectName)
    checkText(
      report,
      at('description'),
      normalizeMultiLine(p.description),
      fieldSubject('projects', n, F.description),
      false,
      L.description,
    )
    // L18 - "Dự án {i}: Công nghệ tối đa 20 mục.", "Dự án {i}: Công nghệ {j} ...".
    const technologiesSubject = fieldSubject('projects', n, F.technologies)
    if (p.technologies.length > L.technologiesItems) {
      report(at('technologies'), fill(T.tooManyItems, { Khối: technologiesSubject, n: L.technologiesItems }))
    }
    p.technologies.forEach((technology, j) => {
      checkText(
        report,
        at('technologies'),
        normalizeSingleLine(technology),
        `${technologiesSubject} ${j + 1}`,
        true,
        L.tag,
      )
    })
  })

  checkText(report, ['versionLabel'], normalizeSingleLine(values.versionLabel), F.versionLabel, false, L.versionLabel)

  // R-F10 - ho ten da bat buoc o tren; con lai: it nhat mot muc o mot trong nam khoi. Loi chung cua form, hien o thanh
  // thao tac (UI.md muc 6).
  if (
    values.education.length === 0 &&
    values.experience.length === 0 &&
    values.skills.length === 0 &&
    values.certifications.length === 0 &&
    values.projects.length === 0
  ) {
    report([CV_EMPTY_ERROR_KEY], T.empty)
  }
}

// Gia tri form DA qua checkForm -> than request B1/B3 (R-B1), moi chuoi da chuan hoa R-F3 (tru NFC).
function toCvBuilderRequest(values: CvBuilderFormValues): CvBuilderRequest {
  const one = normalizeSingleLine
  // Phan tu skills/technologies da qua kiem "bat buoc" nen khong con null.
  const tag = (value: string) => one(value) ?? ''
  const form: CvBuilderForm = {
    contact: {
      fullName: one(values.contact.fullName),
      email: one(values.contact.email),
      phone: one(values.contact.phone),
      address: one(values.contact.address),
      linkedin: one(values.contact.linkedin),
    },
    currentTitle: one(values.currentTitle),
    locationText: one(values.locationText),
    industryCode: one(values.industryCode),
    education: values.education.map((e) => ({
      school: one(e.school),
      degree: one(e.degree),
      major: one(e.major),
      startDate: one(e.startDate),
      endDate: one(e.endDate),
      gpa: gpaFromInput(e.gpa),
    })),
    experience: values.experience.map((e) => ({
      company: one(e.company),
      title: one(e.title),
      startDate: one(e.startDate),
      endDate: one(e.endDate),
      current: e.current,
      description: normalizeMultiLine(e.description),
    })),
    skills: values.skills.map(tag),
    certifications: values.certifications.map((c) => ({
      name: one(c.name),
      issuer: one(c.issuer),
      issueDate: one(c.issueDate),
    })),
    projects: values.projects.map((p) => ({
      name: one(p.name),
      description: normalizeMultiLine(p.description),
      technologies: p.technologies.map(tag),
    })),
  }
  return { template: values.template, versionLabel: one(values.versionLabel), form }
}

// industryCodes = ma nganh cua danh muc (useCatalogsQuery); null khi chua tai xong.
export function createCvBuilderSchema(industryCodes: ReadonlySet<string> | null) {
  return cvBuilderValuesSchema
    .superRefine((values, ctx) => {
      checkForm(values, industryCodes, (path, message) => ctx.addIssue({ code: 'custom', path, message }))
    })
    .transform(toCvBuilderRequest)
}
