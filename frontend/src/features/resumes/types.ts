export type ResumeFileType = 'PDF' | 'DOCX'
export type ParseStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

// FR-C05 R-R4 - yeu cau trich xuat lai gan nhat cua CV. errorMessage la ma loi chuan hoa backend da luu.
export type ResumeReparseStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface ResumeReparse {
  status: ResumeReparseStatus
  errorMessage: string | null
}

export interface Resume {
  id: string
  fileName: string
  fileType: ResumeFileType
  fileSize: number | null
  versionLabel: string | null
  isPrimary: boolean
  parseStatus: ParseStatus
  parseError: string | null
  uploadedAt: string
  // FR-C05: phien ban schema du lieu trich xuat (1|2), null khi chua DONE.
  schemaVersion: number | null
  reparse: ResumeReparse | null
}

// Khop dung 6 khoi trong backend ResumeParsedPayload.java (com.recruitment.resume). Ten field giu
// nguyen camelCase nhu component cua Java record - Jackson serialize record giu nguyen ten, khong
// doi quy uoc.
export interface ResumeParsedContact {
  fullName: string | null
  email: string | null
  phone: string | null
  address: string | null
  linkedin: string | null
}

export interface ResumeParsedEducation {
  school: string | null
  degree: string | null
  major: string | null
  startDate: string | null
  endDate: string | null
  gpa: number | null
}

export interface ResumeParsedExperience {
  company: string | null
  title: string | null
  startDate: string | null
  endDate: string | null
  description: string | null
}

export interface ResumeParsedCertification {
  name: string | null
  issuer: string | null
  issueDate: string | null
}

export interface ResumeParsedProject {
  name: string | null
  description: string | null
  technologies: string[]
}

export interface ResumeParsedPayload {
  contact: ResumeParsedContact | null
  education: ResumeParsedEducation[]
  experience: ResumeParsedExperience[]
  skills: string[]
  certifications: ResumeParsedCertification[]
  projects: ResumeParsedProject[]
  // FR-C05 schema v2 - ban ghi v1 khong co (null/vang mat).
  currentTitle?: string | null
  industryCode?: string | null
  locationText?: string | null
}

export interface CatalogRef {
  code: string
  label: string
}

// FR-C05 R-E7/R-E8 - backend tinh va quy doi san; frontend KHONG tu chia thang -> nam.
export interface ResumeExperienceSummary {
  months: number | null
  // So thap phan 1 chu so do backend tinh (BigDecimal HALF_UP), vd 3.5. JSON.parse lam mat so 0 cuoi
  // (2.0 -> 2) nen khi hien thi phai co dinh 1 chu so thap phan - chi dinh dang, khong lam tron lai.
  years: number | null
  countedEntries: number
  skippedEntries: number
  // "YYYY-MM" - thang tham chieu (gio Viet Nam).
  referenceMonth: string
}

export interface ResumeParsedDataResponse {
  resumeId: string
  data: ResumeParsedPayload
  parsedAt: string
  // FR-C05 - tong quan nghe nghiep (REQUIREMENT muc 4).
  schemaVersion: number | null
  currentTitle: string | null
  industry: CatalogRef | null
  location: CatalogRef | null
  locationText: string | null
  // null khi backend chua tinh.
  experience: ResumeExperienceSummary | null
}

export type CvImprovementStatus = 'NOT_REQUESTED' | 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface CvImprovementSectionSuggestion {
  section: string
  suggestion: string
}

export interface CvImprovementLearningPathItem {
  topic: string
  reason: string
}

export interface CvImprovementSuggestion {
  resumeId: string
  status: CvImprovementStatus
  missingKeywords: string[]
  sectionSuggestions: CvImprovementSectionSuggestion[]
  learningPath: CvImprovementLearningPathItem[]
  generatedAt: string | null
}
