// FR-C07 - khop DTO backend (REQUIREMENT muc 4.4). Khong co model, promptVersion, ngu canh hay prompt.

// Phia dang soan - chon tien to API /hr/... hay /candidates/... Khai rieng (cung gia tri voi MessageSide cua
// features/messages) vi features/messageDraft KHONG import features/messages (UI.md muc 5b - phu thuoc mot chieu).
export type DraftSide = 'hr' | 'candidate'

// Thu tu tra ve tu A1 = thu tu bang R-S1 cua phia goi.
export type DraftScenario =
  | 'REQUEST_MORE_INFO'
  | 'INTERVIEW_REMINDER'
  | 'THANK_FOR_APPLYING'
  | 'RESULT_NOTICE'
  | 'ASK_PROGRESS'
  | 'THANK_AFTER_INTERVIEW'
  | 'REQUEST_RESCHEDULE'
  | 'CUSTOM'

export type DraftTone = 'FORMAL' | 'FRIENDLY'

export type DraftUnavailableReason = 'RESULT_NOT_FINAL' | 'NO_ACTIVE_INTERVIEW' | 'NO_INTERVIEW'

export interface ScenarioOption {
  scenario: DraftScenario
  available: boolean
  // null khi available
  unavailableReason: DraftUnavailableReason | null
}

// A1
export interface DraftScenariosResponse {
  scenarios: ScenarioOption[]
}

// A2 - customPurpose chi gui khi scenario = CUSTOM (R-S4).
export interface MessageDraftRequest {
  scenario: DraftScenario
  tone: DraftTone
  customPurpose?: string
}

// A2 - DUNG MOT field.
export interface MessageDraftResponse {
  draft: string
}

// Lua chon cua khoi soan nhap - MessageComposer giu (khoi giu lua chon khi dong/mo lai trong cung lan xem tab, UI.md
// muc 4b). scenario null = chua chon (A1 chua tai xong).
export interface DraftSelection {
  scenario: DraftScenario | null
  tone: DraftTone
  customPurpose: string
}

export const INITIAL_DRAFT_SELECTION: DraftSelection = {
  scenario: null,
  tone: 'FORMAL',
  customPurpose: '',
}
