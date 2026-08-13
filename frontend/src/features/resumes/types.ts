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
