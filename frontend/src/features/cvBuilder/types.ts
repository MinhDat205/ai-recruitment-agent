// FR-U12 - khop DTO backend (REQUIREMENT muc 4.5) va CvBuilderForm (R-F1). KHONG co truong nhan than nao: anh, ngay
// sinh, tuoi, gioi tinh, hon nhan... (R-F2). Khoi lien he dung 5 truong cua ResumeParsedPayload.Contact.

export type CvTemplate = 'CLASSIC' | 'MODERN'

export interface CvBuilderContact {
  fullName: string | null
  email: string | null
  phone: string | null
  address: string | null
  linkedin: string | null
}

export interface CvBuilderEducation {
  school: string | null
  degree: string | null
  major: string | null
  startDate: string | null
  endDate: string | null
  gpa: number | null
}

export interface CvBuilderExperience {
  company: string | null
  title: string | null
  startDate: string | null
  endDate: string | null
  current: boolean | null
  description: string | null
}

export interface CvBuilderCertification {
  name: string | null
  issuer: string | null
  issueDate: string | null
}

export interface CvBuilderProject {
  name: string | null
  description: string | null
  technologies: string[]
}

// R-F1. industryCode KHONG in ra PDF (O1).
export interface CvBuilderForm {
  contact: CvBuilderContact | null
  currentTitle: string | null
  locationText: string | null
  industryCode: string | null
  education: CvBuilderEducation[]
  experience: CvBuilderExperience[]
  skills: string[]
  certifications: CvBuilderCertification[]
  projects: CvBuilderProject[]
}

// B1, B3 - than request (R-B1).
export interface CvBuilderRequest {
  template: CvTemplate
  versionLabel: string | null
  form: CvBuilderForm
}

// B2 - response.
export interface CvBuilderDocumentResponse {
  resumeId: string
  template: CvTemplate
  versionLabel: string | null
  form: CvBuilderForm
  createdAt: string
}

// B4 (R-A1) - mot doan mo ta kinh nghiem hoac du an.
export type CvRephraseKind = 'EXPERIENCE' | 'PROJECT'

export interface CvRephraseRequest {
  kind: CvRephraseKind
  text: string
}

export interface CvRephraseResponse {
  suggestion: string
}

// UI.md muc 6 "Lưu xong" - location.state khi ve /candidate/profile; trang ho so hien cau savedCreate/savedEdit.
export interface CvBuilderSavedState {
  cvBuilderSaved: 'create' | 'edit'
}
