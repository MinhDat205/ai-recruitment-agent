# FR-C08 — Giao diện

> Trạng thái: ĐÃ DUYỆT (14/10/2026). Đi cùng `REQUIREMENT.md` (cùng trạng thái). Quy tắc nghiệp vụ, mã lỗi,
> DTO: `REQUIREMENT.md`; file này chỉ nói về màn hình.

Nguồn: `docs/UI_GUIDE.md` (token mục 1, ràng buộc mục 4 "Nội dung do AI tạo", "Trích dẫn từ CV", trạng thái mục 5, tiếp cận
mục 6, bản đồ mục 7). Khối hỏi đáp **dùng chung** cho hai phía — một bộ component trong `features/cvQa/`, khác nhau ở câu
chữ theo `side` (`hr` | `candidate`).

## 1. Trạng thái

> Trạng thái: ĐÃ DUYỆT (14/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Thay đổi |
|---|---|---|
| `/hr/applications/:id?tab=cvqa` | Trang hồ sơ đơn ứng tuyển — **màn hình có sẵn (FR-H09), mở rộng** thêm tab **"Hỏi đáp CV"** | Không phải màn hình mới (★ ở bản đồ là từ lúc H09 tạo trang). Tab mới đặt **sau "Giải thích", trước "Sàng lọc"** (Q14). Tab mặc định vẫn `cv`. `?tab=` lạ → `cv` (giữ `parseTab` có sẵn) |
| ★`/candidate/resumes/:id/qa` | Hỏi đáp CV (ứng viên) | Route mới, đã có trong bản đồ (`UI_GUIDE.md:342`); đặt trong nhóm `ProtectedRoute(CANDIDATE)` + `RequireCandidateProfileOnboarding` như `/candidate/resumes/:id/improvement-suggestions` |
| `/candidate/profile` | "Hồ sơ và CV" — màn hình có sẵn, mở rộng | Thêm nút "Hỏi đáp CV" ở mỗi CV `DONE` |

Sửa `UI_GUIDE.md` mục 7 ở đợt cuối (REQUIREMENT mục 10, D3).

## 3. Điểm vào

- HR: tab "Hỏi đáp CV" trên trang hồ sơ đơn (vào trang từ tab Ứng viên của sửa tin, `/hr/candidates`, thông báo).
- Ứng viên: `/candidate/profile` → danh sách CV → nút **"Hỏi đáp CV"** (icon `MessageSquareText`, `variant="outline"`,
  `size="sm"`), đặt **ngay sau** "Gợi ý cải thiện CV", cùng điều kiện `parseStatus === 'DONE'` (`ResumeList.tsx:246-256`).
- Không có điểm vào nào khác (không từ trang chi tiết đơn phía ứng viên — Q9: hỏi theo CV, không theo đơn).

## 4. Bố cục (khung ASCII)

### 4a. Desktop (≥ `lg`) — tab "Hỏi đáp CV" phía HR, có hai lượt

```
┌ Card trang hồ sơ đơn (max-w-5xl) ───────────────────────────────────────────────────────────┐
│ [Đầu trang H09: tên ứng viên · tên tin · badge trạng thái · thanh thao tác]                   │
│ [CV & điểm] [Giải thích] [Hỏi đáp CV] [Sàng lọc] [Giấy mời phỏng vấn] [Lịch sử] [Trao đổi]   │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Đặt câu hỏi về nội dung CV ứng viên đã nộp cho đơn này. AI chỉ trả lời dựa trên CV …          │
│ ⓘ Các câu hỏi và câu trả lời không được lưu. Tải lại hoặc rời trang sẽ mất toàn bộ.           │
│                                                                                              │
│ Câu hỏi của bạn                                                                              │
│ ┌──────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ Ví dụ: Ứng viên đã dùng Docker trong dự án nào?                                           │ │
│ └──────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                   42/500     │
│                                                                                    [ Hỏi ]   │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬ (LinearProgress — chỉ khi đang chờ)  AI đang đọc CV…                            │
│                                                                                              │
│ ── Lượt mới nhất ─────────────────────────────────────────────────────────────────────────── │
│ Câu hỏi                                                                                      │
│ Ứng viên đã làm việc ở những công ty nào?                                                    │
│ ┌ khối AI (surface-container-high + viền outline-variant) ─────────────────────────────────┐ │
│ │ ✦ Do AI tạo                                                                               │ │
│ │ Theo CV, ứng viên từng làm ở Công ty A (2019–2022) và Công ty B (2022–nay).               │ │
│ │ ⓘ CV dài, hệ thống chỉ dùng phần đầu và phần cuối của CV để trả lời.   (chỉ cvTruncated)   │ │
│ │ Trích dẫn đã được đối chiếu khớp nguyên văn với CV. Phần trả lời là lời của AI — hãy      │ │
│ │ dựa vào trích dẫn.                                                                        │ │
│ │ Trích dẫn từ CV                                                                           │ │
│ │ ┃ Chuyên viên CSKH – Công ty A (03/2019 – 06/2022)                                        │ │
│ │ ┃ Trưởng nhóm CSKH – Công ty B (07/2022 – nay)                                            │ │
│ └───────────────────────────────────────────────────────────────────────────────────────────┘ │
│ ── ─────────────────────────────────────────────────────────────────────────────────────── │
│ Câu hỏi                                                                                      │
│ Ứng viên bao nhiêu tuổi?                                                                     │
│ ┌ khối thông tin (surface + viền outline-variant) ─────────────────────────────────────────┐ │
│ │ ⓘ Hệ thống không trả lời câu hỏi về thông tin nhân thân (tuổi, giới tính, …).            │ │
│ └───────────────────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

- Ô hỏi ở trên; lượt **mới nhất ở trên** danh sách (O7). Mỗi lượt cách nhau `gap-6`, ngăn bằng `border-t
  border-m3-outline-variant`.
- `┃` = `blockquote` kiểu evidence H06 (mục 5d). Mỗi trích dẫn một khối, xếp dọc `gap-2`.
- Câu `truncatedNotice` (icon `Info` + chữ, mục 5d) chỉ hiện khi `cvTruncated = true`; vị trí: ngay dưới câu trả lời (khối
  `ANSWERED`) hoặc ngay dưới câu `notFound` (khối `NOT_FOUND`, 4b-2). Không bao giờ ở khối từ chối.

### 4b. Các trạng thái của một lượt

```
(1) Đang chờ — lượt chưa có kết quả KHÔNG hiện trong danh sách; chỉ có LinearProgress dưới nút "Hỏi".

(2) Không tìm thấy căn cứ (NOT_FOUND)
Câu hỏi
CV có ghi ứng viên dùng Kubernetes không?
┌ khối AI ─────────────────────────────────────────────┐
│ ✦ Do AI tạo                                           │
│ Không tìm thấy căn cứ trong CV cho câu hỏi này.       │
│ ⓘ CV dài, hệ thống chỉ dùng phần đầu và phần cuối     │   (chỉ khi cvTruncated)
│   của CV để trả lời.                                  │
└───────────────────────────────────────────────────────┘

(3) Không trả lời (REFUSED) — khối thông tin, KHÔNG nhãn "Do AI tạo" (lý do có thể đến từ bộ chặn từ khoá)
┌ khối thông tin ──────────────────────────────────────────────────────────────┐
│ ⓘ Gợi ý bổ sung, cải thiện CV có ở chức năng Gợi ý cải thiện CV.            │
│   Mở Gợi ý cải thiện CV →          (chỉ lý do CV_IMPROVEMENT, chỉ ứng viên) │
└───────────────────────────────────────────────────────────────────────────────┘

(4) Lỗi — KHÔNG thêm lượt vào danh sách; hiện ngay dưới nút "Hỏi", câu hỏi giữ nguyên trong ô
⚠ AI phản hồi quá lâu, vui lòng thử lại.                                [ Thử lại ]
```

### 4c. Desktop — trang ứng viên `/candidate/resumes/:id/qa`

```
┌ CandidateLayout ─────────────────────────────────────────────────────────────┐
│ ← Về Hồ sơ và CV                                                              │
│ Hỏi đáp CV                                     (text-m3-headline-sm)          │
│ cv-mau-quoc-huy.docx · Bản DOCX - CSKH         (tên file · nhãn phiên bản)    │
│ ┌ Card (max-w-3xl) ─────────────────────────────────────────────────────────┐ │
│ │ [Khối hỏi đáp như 4a, side = candidate]                                    │ │
│ └────────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 4d. CV chưa hỏi được (thay cả khối hỏi đáp, cả hai phía)

```
┌──────────────────────────────────────────────────────────────┐
│ CV của đơn này chưa trích xuất xong nên chưa hỏi đáp được.     │   (PENDING / PROCESSING)
│ Không trích xuất được nội dung CV của đơn này nên không hỏi    │   (FAILED)
│ đáp được.                                                      │
└──────────────────────────────────────────────────────────────┘
```

Không ô hỏi, không nút. Phía ứng viên thêm liên kết "Về Hồ sơ và CV".

### 4e. Điện thoại (375px)

```
┌ 375px ───────────────────────────────┐
│ [tab cuộn ngang …  Hỏi đáp CV  …]     │
│ Đặt câu hỏi về nội dung CV …          │
│ ⓘ Các câu hỏi … không được lưu …      │
│ Câu hỏi của bạn                       │
│ ┌───────────────────────────────────┐ │
│ │                                   │ │
│ └───────────────────────────────────┘ │
│                              42/500   │
│ [              Hỏi                ]   │  (w-full)
│ Câu hỏi                               │
│ Ứng viên đã làm việc ở …              │
│ ┌ khối AI ──────────────────────────┐ │
│ │ ✦ Do AI tạo                        │ │
│ │ Theo CV, ứng viên từng …           │ │
│ │ Trích dẫn từ CV                    │ │
│ │ ┃ Chuyên viên CSKH – Công ty A     │ │
│ │ ┃ (03/2019 – 06/2022)              │ │  (xuống dòng, không cuộn ngang)
│ └────────────────────────────────────┘ │
└───────────────────────────────────────┘
```

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 9 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/cvQa/types.ts` | `CvQaSide = 'hr' \| 'candidate'`, `CvQaOutcome`, `CvQaRefusalReason`, `CvQaResponse` (gồm `cvTruncated: boolean`), `CvQaTurn` (`id` sinh ở client, `question`, `response`) — khớp DTO REQUIREMENT 4.5 |
| `frontend/src/features/cvQa/api.ts` | `askHrCvQuestion(applicationId, question)`, `askCandidateCvQuestion(resumeId, question)` |
| `frontend/src/features/cvQa/cvQaLabels.ts` | Mọi chuỗi mục 7 — một chỗ duy nhất; `MAX_QUESTION_LENGTH = 500` |
| `frontend/src/features/cvQa/useCvQaSession.ts` | `useCvQaSession(side, key)` — `key` = `applicationId` (HR) hoặc `resumeId` (ứng viên). Giữ `turns`, lỗi đang hiện, câu hỏi chờ "Thử lại", trạng thái đang chờ; gọi API bằng `useMutation`. **Gọi ở cấp trang** (mục 5b) để không mất khi đổi tab (a25). Quy tắc khoá (REQUIREMENT R-N3, S4): (1) `key` đổi → xoá sạch `turns`, lỗi, câu chờ "Thử lại" và trạng thái đang chờ (theo dõi `key` trước đó, xoá ngay trong lần render đổi khoá — không chờ `useEffect`, để không có khung hình nào hiện lượt của khoá cũ); (2) mỗi lần gửi ghi lại `key` lúc gửi; khi request xong, `key` hiện tại khác `key` lúc gửi → bỏ kết quả/lỗi (không thêm lượt, không hiện lỗi, không bỏ khoá nút của request mới); (3) thành công cùng khoá → thêm lượt vào **đầu** mảng; lỗi cùng khoá → giữ lỗi để hiện (4b-4) |
| `frontend/src/features/cvQa/CvQaPanel.tsx` | Câu giới thiệu + câu "không được lưu" + ô hỏi + bộ đếm + nút + tiến trình + lỗi + danh sách lượt; nhận `side`, `session`, `resumeId` (để dựng liên kết U05, phía ứng viên) |
| `frontend/src/features/cvQa/CvQaTurnItem.tsx` | Một lượt: câu hỏi + khối theo `outcome` |
| `frontend/src/features/cvQa/CvQaCitationQuote.tsx` | Một trích dẫn — đúng class evidence H06 (mục 5d) |
| `frontend/src/features/cvQa/CvQaUnavailable.tsx` | Khối 4d theo `parseStatus` |
| `frontend/src/pages/CandidateCvQaPage.tsx` | Trang 4c; lấy CV từ `useResumesQuery()` (khuôn `CvImprovementSuggestionsPage.tsx:96-107`); `useCvQaSession('candidate', resumeId)` ở cấp trang, khoá = `:id` |

### 5b. File có sẵn bị sửa

| File | Sửa gì |
|---|---|
| `frontend/src/pages/HrApplicationDetailPage.tsx` | `VALID_TABS` thêm `'cvqa'` (sau `'explanation'`); một `TabsTrigger` "Hỏi đáp CV" giữa "Giải thích" và "Sàng lọc"; một `TabsContent value="cvqa"` render `CvQaPanel` hoặc `CvQaUnavailable` theo `detail.resumeParseStatus`; `useCvQaSession('hr', applicationId)` gọi trong `ApplicationDetailContent` (không trong tab), khoá = `applicationId` từ `useParams`. Comment ghi FR-C08 cạnh các comment FR có sẵn. Không đổi gì khác |
| `frontend/src/App.tsx` | Route `/candidate/resumes/:id/qa` → `CandidateCvQaPage`, ngay sau route `improvement-suggestions` |
| `frontend/src/features/resumes/ResumeList.tsx` | Nút "Hỏi đáp CV" (mục 3) |

### 5c. Dùng lại có sẵn

`Button`, `Textarea`, `Label`, `Card` (`components/ui`); `LinearProgress` (`features/resumes/LinearProgress.tsx`, khuôn H11 L12);
`HrLayout`/`CandidateLayout`; `useResumesQuery`; icon lucide `Sparkles`, `Info`, `AlertCircle`, `MessageSquareText`,
`ArrowLeft`. Không thêm component vào `components/ui`, không thêm thư viện.

### 5d. Bảng token

| Phần | Token / class | Ghi chú |
|---|---|---|
| Câu giới thiệu | `text-sm text-m3-on-surface` | Nằm trong thẻ trắng |
| Câu "không được lưu" | `text-sm text-m3-on-surface-variant` + icon `Info` cùng màu | Trên `m3-surface` (4.83:1 — UI_GUIDE mục 6) |
| Ô hỏi | `Textarea` (viền `--input` → `m3-outline`), `min-h-20`, `aria-describedby` = bộ đếm | |
| Bộ đếm | `text-sm text-m3-on-surface-variant`; > 500 → `text-m3-error` | Cả hai trên `m3-surface` |
| Nút "Hỏi" | `Button` mặc định (`m3-primary`), `min-h-10`, `w-full sm:w-auto` | |
| Nhãn "Câu hỏi" | `text-xs font-medium text-m3-on-surface-variant` | |
| Nội dung câu hỏi | `text-sm text-m3-on-surface whitespace-pre-wrap break-words` | |
| Khối AI | `rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3` | Viền bắt buộc (UI_GUIDE mục 4) |
| Chữ trong khối AI | **chỉ** `text-m3-on-surface` (kể cả nhãn "Do AI tạo", câu lưu ý trích dẫn, tiêu đề "Trích dẫn từ CV") | `on-surface-variant` không đạt trên nền này |
| Trích dẫn | `blockquote` `border-l-2 border-m3-primary bg-m3-primary-container px-3 py-2 text-sm font-normal text-m3-on-surface`; `p` bên trong `whitespace-pre-wrap break-words` | **Đúng** class `CriterionScoreBreakdown.tsx:75-79` |
| Câu `truncatedNotice` (trong khối AI) | `text-sm text-m3-on-surface` + icon `Info` `text-m3-on-surface` `aria-hidden` | Nằm trên `surface-container-high` → chỉ dùng `on-surface` |
| Khối từ chối | `rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3`, icon `Info` `text-m3-on-surface-variant`, chữ `text-m3-on-surface` | Trung tính, không đỏ/vàng |
| Liên kết U05 | `text-sm text-m3-primary hover:underline` | Trên `m3-surface` (4.64:1) |
| Lỗi | icon `AlertCircle text-m3-error` + chữ `text-m3-on-surface`, `role="alert"`; nút "Thử lại" `variant="outline"` | Khuôn `MessageDraftPanel.tsx:170-177` |
| Khối 4d | `text-sm text-m3-on-surface` | |

## 6. Trạng thái hiển thị

| Trạng thái | Hiển thị |
|---|---|
| Đang tải đầu trang (HR) | Skeleton có sẵn của H09; tab chưa render |
| Đang tải danh sách CV (ứng viên) | Skeleton 3 dòng `bg-m3-surface-container animate-pulse` thay khối hỏi đáp |
| Không tìm thấy | HR: thông điệp có sẵn của H09 (400/403/404 cùng một câu). Ứng viên: CV không có trong `useResumesQuery` → "Không tìm thấy CV này." + "Về Hồ sơ và CV"; Q2 trả 404 (CV bị đổi chủ — lý thuyết) → cùng câu |
| Thiếu dữ liệu | `resumeParseStatus`/`parseStatus` ≠ `DONE` → khối 4d; Q1/Q2 trả 409 `CV_TEXT_NOT_AVAILABLE` (trạng thái frontend cũ) → hiện câu của backend ở vị trí lỗi 4b-4 |
| Rỗng | "Chưa có câu hỏi nào trong phiên này." (`text-sm text-m3-on-surface-variant`) dưới ô hỏi |
| AI đang xử lý | Nút "Hỏi" khoá + chữ "Đang hỏi…"; `LinearProgress` nhãn "Đang trả lời câu hỏi" + dòng "AI đang đọc CV…"; ô hỏi **vẫn gõ được**; vùng danh sách `aria-busy="true"`; đổi tab được, kết quả vẫn vào danh sách khi xong |
| Trả lời / Không tìm thấy / Không trả lời | 4a, 4b-2, 4b-3; `cvTruncated = true` → thêm câu `truncatedNotice` ở hai khối đầu |
| Đổi sang đơn/CV khác trên cùng trang (khoá đổi — R-N3) | Danh sách lượt trống, không lỗi, không tiến trình, ô hỏi trống; request của khoá cũ về sau đó bị bỏ, không hiện gì |
| Lỗi | 4b-4: câu `message` của backend nếu response có `{error, message}` (400 `INVALID_CV_QA_QUESTION`, 409, 429, 502, 503, 504); không có (mạng, 5xx khác) → câu dự phòng mục 7. "Thử lại" gửi lại **đúng câu hỏi lần lỗi** (không đọc lại ô hỏi). Lỗi mất khi gửi câu hỏi mới |
| Không có quyền (403) | HR: như "Không tìm thấy" (H09 R-T13). Q1 403 giữa phiên (đơn đổi công ty — lý thuyết) → câu dự phòng |

## 7. Nội dung chữ (tiếng Việt có dấu)

Tất cả ở `cvQaLabels.ts`. `{n}` là số ký tự.

| Khoá | Chuỗi |
|---|---|
| `tabLabel` | Hỏi đáp CV |
| `pageTitle` | Hỏi đáp CV |
| `backToProfile` | Về Hồ sơ và CV |
| `resumeButton` | Hỏi đáp CV |
| `introHr` | Đặt câu hỏi về nội dung CV ứng viên đã nộp cho đơn này. AI chỉ trả lời dựa trên CV và kèm trích dẫn nguyên văn để bạn đối chiếu. |
| `introCandidate` | Đặt câu hỏi về nội dung CV này. AI chỉ trả lời dựa trên CV và kèm trích dẫn nguyên văn. |
| `notSavedNotice` | Các câu hỏi và câu trả lời không được lưu. Tải lại hoặc rời trang sẽ mất toàn bộ. |
| `questionLabel` | Câu hỏi của bạn |
| `placeholderHr` | Ví dụ: Ứng viên đã dùng Docker trong dự án nào? |
| `placeholderCandidate` | Ví dụ: CV của tôi ghi những chứng chỉ nào? |
| `counter` | {n}/500 |
| `askButton` | Hỏi |
| `asking` | Đang hỏi… |
| `progressLabel` | Đang trả lời câu hỏi |
| `progressHint` | AI đang đọc CV… |
| `emptyTurns` | Chưa có câu hỏi nào trong phiên này. |
| `turnQuestionLabel` | Câu hỏi |
| `aiBadge` | Do AI tạo |
| `citationNote` | Trích dẫn đã được đối chiếu khớp nguyên văn với CV. Phần trả lời là lời của AI — hãy dựa vào trích dẫn. |
| `citationsHeading` | Trích dẫn từ CV |
| `notFound` | Không tìm thấy căn cứ trong CV cho câu hỏi này. |
| `truncatedNotice` | CV dài, hệ thống chỉ dùng phần đầu và phần cuối của CV để trả lời. |
| `refused.PERSONAL_INFO` (chỉ phía HR — S3) | Hệ thống không trả lời câu hỏi về thông tin nhân thân (tuổi, giới tính, tình trạng hôn nhân, tôn giáo, sức khoẻ…). |
| `refused.HIRING_JUDGEMENT` | Hệ thống không đưa ra nhận định đạt hay không đạt, phù hợp hay không phù hợp. Hãy hỏi về một nội dung cụ thể trong CV, ví dụ một kinh nghiệm hay kỹ năng. |
| `refused.CV_IMPROVEMENT` | Gợi ý bổ sung, cải thiện CV có ở chức năng Gợi ý cải thiện CV. |
| `refusedLinkCvImprovement` | Mở Gợi ý cải thiện CV |
| `refused.OUT_OF_SCOPE` | Câu hỏi này không liên quan tới nội dung CV. Hãy hỏi về thông tin có trong CV. |
| `emptyQuestion` | Vui lòng nhập câu hỏi. |
| `questionTooLong` | Câu hỏi tối đa 500 ký tự. |
| `askFailedFallback` | Chưa trả lời được câu hỏi, vui lòng thử lại. |
| `retry` | Thử lại |
| `unavailablePendingHr` | CV của đơn này chưa trích xuất xong nên chưa hỏi đáp được. |
| `unavailableFailedHr` | Không trích xuất được nội dung CV của đơn này nên không hỏi đáp được. |
| `unavailablePendingCandidate` | CV này chưa trích xuất xong nên chưa hỏi đáp được. |
| `unavailableFailedCandidate` | Không trích xuất được nội dung CV này nên không hỏi đáp được. |
| `resumeNotFound` | Không tìm thấy CV này. |

Kiểm phía client trước khi gọi API (không gọi khi sai): ô rỗng/chỉ khoảng trắng → `emptyQuestion` dưới ô (`role="alert"`),
focus vào ô; > 500 ký tự sau trim → `questionTooLong`, focus vào ô. Nút "Hỏi" **không** khoá vì lý do này (chỉ khoá khi
đang chờ) — người dùng bàn phím/đọc màn hình biết vì sao không gửi được. Câu backend trả về cho cùng lỗi trùng nguyên văn
hai câu này (REQUIREMENT 4.4).

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Cỡ | Khác biệt |
|---|---|
| Compact (< 600px) | Nút "Hỏi", "Thử lại" `w-full`; bộ đếm canh phải trên dòng riêng; trích dẫn và câu hỏi `break-words`; `TabsList` cuộn ngang (có sẵn H09) |
| Medium (600–1023px) | Nút `w-auto` canh phải; còn lại như compact |
| Expanded (≥ 1024px) | Như 4a; trang ứng viên `max-w-3xl` giữa trang |

Không cuộn ngang toàn trang ở 375px; vùng chạm nút ≥ 40px (`min-h-10`).

## 9. Khả năng tiếp cận

- `Textarea` có `Label` thật (`questionLabel`); bộ đếm nối bằng `aria-describedby`; quá giới hạn → `aria-invalid`.
- Danh sách lượt bọc trong vùng `aria-live="polite"`, `aria-busy` khi đang chờ — lượt mới được đọc khi xuất hiện.
- Lỗi `role="alert"`; sau lỗi focus không tự nhảy (người dùng vẫn ở nút/ô).
- Mỗi lượt là `article` có `aria-labelledby` trỏ tới câu hỏi; danh sách trích dẫn là `ul`/`li` chứa `blockquote`.
- `LinearProgress` có `role="progressbar"` + nhãn (có sẵn).
- Icon chỉ trang trí → `aria-hidden="true"`; nhãn "Do AI tạo" là chữ thật, không chỉ icon.
- Không truyền đạt kết quả bằng màu: ba `outcome` khác nhau bằng chữ và khối, không bằng màu.
- Liên kết "Mở Gợi ý cải thiện CV" là `Link` thật (điều hướng được bằng bàn phím).

## 10. Không được làm

- Hiện nhãn "phù hợp/không phù hợp", "đạt/không đạt", điểm, thanh tiến trình điểm, màu đỏ-vàng-xanh theo kết quả (CLAUDE.md §8).
- Dùng màu khác kiểu evidence H06 cho trích dẫn; tô sáng/gạch chân trong câu trả lời; hiện trích dẫn ngoài khối "Trích dẫn từ CV".
- Hiện văn bản gốc CV, đoạn quanh trích dẫn, nút "Xem trong CV" (Q3).
- Gắn nhãn "Do AI tạo" cho khối từ chối (lý do có thể từ bộ chặn từ khoá, không phải AI).
- Thêm nút "Dùng", "Lưu", "Sao chép vào tin nhắn", "Gửi cho ứng viên" — câu trả lời chỉ để đọc, không có hiệu lực nào (UI_GUIDE mục 4).
- Lưu lượt vào `localStorage`/`sessionStorage`, đặt state lượt trong component tab (mất khi đổi tab), hay gửi lượt trước lên backend.
- Giữ lượt hỏi đáp, lỗi hay câu chờ "Thử lại" khi đổi sang đơn/CV khác trên cùng trang; hiện kết quả hay lỗi của câu hỏi gửi
  ở đơn/CV trước. Quy tắc: "Không giữ lượt hỏi đáp khi đổi sang đơn/CV khác trên cùng trang." (R-N3, S4)
- Thêm nút Dùng/Lưu/Bỏ qua trên câu trả lời (O9 — nội dung chỉ để đọc).
- Hiện câu `truncatedNotice` ở khối từ chối, hay khi `cvTruncated = false`.
- Ẩn câu "không được lưu"; tự gửi lại khi lỗi; khoá cả trang khi đang chờ.
- Thêm ô chọn Job/JD ở trang ứng viên (Q9); thêm nút Hỏi đáp CV ở trang chi tiết đơn phía ứng viên.
- Hiển thị `refusalReason` dạng mã thô; hiện `answer` khi `outcome ≠ ANSWERED`.
