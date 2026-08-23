export type ResumeFileType = 'PDF' | 'DOCX'
export type ParseStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

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
}

export interface ResumeParsedDataResponse {
  resumeId: string
  data: ResumeParsedPayload
  parsedAt: string
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
