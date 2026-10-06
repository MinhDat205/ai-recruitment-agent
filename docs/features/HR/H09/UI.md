# FR-H09 — Giao diện

Trạng thái: ĐÃ DUYỆT 06/10/2026.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 2 (điều hướng HR), 3 (component), 4 (ràng buộc
màn hình xếp hạng/giải thích), 5 (rỗng/tải/lỗi), 6 (tương phản). Mã quy tắc `R-…` là của
`REQUIREMENT.md` cùng thư mục. File này không chép lại quy tắc chung, chỉ chốt cách áp cho màn hình này.

## 1. Trạng thái

ĐÃ DUYỆT 06/10/2026.

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| ★`/hr/applications/:id` | Hồ sơ đơn ứng tuyển — tab "CV & điểm", "Giải thích", "Lịch sử"; thanh thao tác Mời phỏng vấn / Từ chối / Trúng tuyển | ★Mới | FR-H09; FR-H04, FR-H05, FR-H06, FR-H07 |
| `/hr/jobs/:id/edit` (tab "Ứng viên") | Bỏ Sheet "Hồ sơ ứng viên", nút "Xem hồ sơ" thành liên kết | Hiện có | FR-H09 (R-P3, R-E1) |
| `/hr/candidates` | Thêm liên kết "Xem hồ sơ" mỗi dòng | Hiện có | FR-H09 (R-E2) |

Query `?tab=cv|explanation|history` (mặc định `cv`, giá trị lạ → `cv`), cùng cách đọc của
`HrJobEditPage.tsx:480-481` (R-E4). Đổi tab thì cập nhật URL bằng `replace` (không đẩy lịch sử mới mỗi lần
bấm tab). Dòng UI_GUIDE mục 7 cho route này được sửa ở đợt 3 cho khớp phạm vi 3 tab.

## 3. Điểm vào

- Tab "Ứng viên" của `/hr/jobs/:id/edit`: nút "Xem hồ sơ" (giữ vị trí, icon `FileText`, `variant="outline"
  size="sm"`) đổi thành `<Link>` tới `/hr/applications/{id}` (R-E1).
- `/hr/candidates`: thêm liên kết "Xem hồ sơ" cùng kiểu, đặt **đầu** nhóm thao tác của mỗi dòng, trước
  "Lịch sử đánh giá" (R-E2).
- Thông báo HR mới (chuông + `/hr/notifications`): bấm mở `link = /hr/applications/{id}` (R-N1). Thông báo
  cũ vẫn mở `/hr/jobs` (R-N2).
- Mở thẳng URL (dán link, F5): trang tự tải, tab theo `?tab=`.
- Rời trang: liên kết "← {tên tin}" ở đầu trang về `/hr/jobs/{jobId}/edit?tab=applications` (R-E3); nút
  quay lại của trình duyệt giữ nguyên hành vi.

## 4. Bố cục (khung ASCII)

Toàn bộ nội dung nằm trong **một** `Card` trắng (`bg-m3-surface`) `mx-auto max-w-5xl`, cùng khung với
`HrJobEditPage.tsx:486` — nền trang là `m3-surface-container`, đặt chữ trên thẻ trắng để dùng được
`m3-primary`/`m3-on-surface-variant` (UI_GUIDE mục 6). Tiêu đề `HrLayout`: "Hồ sơ ứng tuyển".

### 4a. Desktop (≥ `lg`) — tab "CV & điểm", đơn có lượt DONE (R-T3, R-T7)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ← Senior Java Backend Developer                                          │
│                                                                          │
│ Trần Minh Hoàng                    [Mời phỏng vấn] [Từ chối]             │
│ [Chờ duyệt]  Nộp ngày 28/08/2026 17:45                                   │
│                                                                          │
│ ┌────────────┬────────────┬──────────┐                                   │
│ │ CV & điểm  │ Giải thích │ Lịch sử  │                                   │
│ └────────────┴────────────┴──────────┘                                   │
├──────────────────────────────────────────────────────────────────────────┤
│ Hồ sơ đã nộp                                                             │
│ [FileText] cv-tran-minh-hoang.pdf   [Đã trích xuất]   [↓ Xem CV gốc]     │
│                                                                          │
│ Thư giới thiệu                                                           │
│ Tôi có hơn 4 năm làm chăm sóc khách hàng và mong muốn được làm việc     │
│ tại Đà Nẵng…  (tối đa 6 dòng)                                            │
│ Xem thêm                                                                 │
│ ──────────────────────────────────────────────────────────────────────── │
│ Điểm theo rubric                               [Chấm điểm hồ sơ]         │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Tổng điểm 67.50 / 100 · Hạng 1                                       │ │
│ │ Hệ thống tính theo trọng số rubric từ lượt chấm hoàn tất 28/08/2026. │ │
│ │ ┌──────────────────────────────────────────────────────────────────┐ │ │
│ │ │ > Kinh nghiệm lập trình Backend/Java      4/5 điểm  Trọng số 35% │ │ │
│ │ ├──────────────────────────────────────────────────────────────────┤ │ │
│ │ │ v Kỹ năng Spring Boot & thiết kế API/CSDL 3/5 điểm  Trọng số 30% │ │ │
│ │ │   Diễn giải của AI cho tiêu chí này — không phải khuyến nghị…    │ │ │
│ │ │   (reasoning)                                                    │ │ │
│ │ │   ┃ "…trích dẫn nguyên văn từ CV…"                               │ │ │
│ │ │   ┃ Nguồn: Kinh nghiệm                                           │ │ │
│ │ └──────────────────────────────────────────────────────────────────┘ │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ──────────────────────────────────────────────────────────────────────── │
│ CV đã trích xuất                                                         │
│ (Tổng quan nghề nghiệp · Liên hệ · Học vấn · Kinh nghiệm · Kỹ năng ·     │
│  Chứng chỉ · Dự án — phần thân dùng lại của ResumeParsedDataDialog)      │
└──────────────────────────────────────────────────────────────────────────┘
```

Thứ tự ba khối trong tab là cố định: **Hồ sơ đã nộp** (file gốc + thư giới thiệu) → **Điểm theo rubric** →
**CV đã trích xuất**. Khối dài nhất (CV đã trích xuất) đặt cuối để điểm và evidence không bị đẩy xuống.
Tổng điểm và hạng nằm **trong** khung của danh sách tiêu chí, ngay trên tiêu chí đầu tiên, không đứng riêng
ở đầu trang (R-G2, CLAUDE.md §8).

Khối "Điểm theo rubric" ở các trạng thái khác (thay phần trong khung):

```
R-T4  Đơn này chưa được chấm điểm.                         [Chấm điểm hồ sơ]
R-T5  [⟳ Đang chấm — Đã chấm 2/4 tiêu chí]                 [Chấm điểm hồ sơ] (khoá)
      Đơn này đang có một lượt chấm điểm chưa hoàn tất, vui lòng chờ lượt trước kết thúc.
R-T6  [Chấm điểm thất bại]                                 [Chấm điểm hồ sơ]
      LLM_RETRY_EXHAUSTED: Đã thử lại nhiều lần do lỗi kết nối/quá tải của AI…
R-T7  (lượt mới nhất đang chạy hoặc FAILED, có lượt DONE cũ hơn)
      [Chấm điểm thất bại]  (badge của lượt mới nhất)
      Lượt chấm mới nhất chưa hoàn tất hoặc không thành công. Điểm bên dưới là của lượt
      hoàn tất ngày 28/08/2026.
      ┌ Tổng điểm … (như 4a) ┐
```

Câu lý do khoá nút luôn hiện **thành chữ** ngay dưới hàng tiêu đề khối (không chỉ trong `title`), xem
mục 9.

### 4b. Desktop — tab "Giải thích"

```
├──────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ (i) Tổng hợp của AI dựa trên các tiêu chí đã chấm — không phải khuyến │ │
│ │     nghị tuyển dụng, HR tự xem xét từng hồ sơ.                        │ │
│ │ (summary)                                                            │ │
│ │ Điểm mạnh · Điểm cần cải thiện                                       │ │
│ │ Tiêu chí đã thể hiện trong CV:   [chip] [chip]                       │ │
│ │ Tiêu chí chưa thể hiện trong CV: [chip]                              │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ Báo cáo của lượt chấm hoàn tất ngày 28/08/2026.                          │
```

`ExplanationReport` nguyên trạng (R-T11). R-T9 (`PENDING`):

```
│ Báo cáo tổng hợp của AI đang được xử lý.                [⟳ Tải lại]      │
```

### 4c. Desktop — tab "Lịch sử"

```
├──────────────────────────────────────────────────────────────────────────┤
│ ┃ Nộp đơn → [Chờ duyệt]                                                  │
│ ┃ 15/07/2026 10:00                                                       │
│ ┃                                                                        │
│ ┃ [Chờ duyệt] → [Đã mời phỏng vấn]                                       │
│ ┃ 18/07/2026 09:00                                                       │
│ ┃                                                                        │
│ ┃ [Đã mời phỏng vấn] → [Trúng tuyển]                                     │
│ ┃ 28/07/2026 09:00                                                       │
```

Giao diện của `ApplicationHistoryTimeline` giữ nguyên (R-C3).

### 4d. Điện thoại (375px) — tab "CV & điểm"

Chiều rộng nội dung: 375 − 80 (rail HR, `HrLayout.tsx:35` `w-20`) − 2×16 (`main p-4`) − padding thẻ ≈
240px.

```
┌────────────────────────────┐
│ ← Senior Java Backend…     │
│ Trần Minh Hoàng            │
│ [Chờ duyệt]                │
│ Nộp ngày 28/08/2026 17:45  │
│ ┌────────────────────────┐ │
│ │     Mời phỏng vấn      │ │
│ └────────────────────────┘ │
│ ┌────────────────────────┐ │
│ │        Từ chối         │ │
│ └────────────────────────┘ │
│ ┌─────────┬────────┬─────┐ │
│ │CV & điểm│Giải thích│Lịch│→│  (cuộn ngang nếu thiếu chỗ)
│ └─────────┴────────┴─────┘ │
├────────────────────────────┤
│ Hồ sơ đã nộp               │
│ cv-tran-minh-hoang.pdf     │
│ [Đã trích xuất]            │
│ ┌────────────────────────┐ │
│ │    ↓ Xem CV gốc        │ │
│ └────────────────────────┘ │
│ Thư giới thiệu             │
│ (6 dòng) … Xem thêm        │
│ ────────────────────────── │
│ Điểm theo rubric           │
│ ┌────────────────────────┐ │
│ │   Chấm điểm hồ sơ      │ │
│ └────────────────────────┘ │
│ Tổng điểm 67.50 / 100      │
│ Hạng 1                     │
│ > Kinh nghiệm lập trình    │
│   Backend/Java             │
│   4/5 điểm · Trọng số 35%  │
│ …                          │
│ ────────────────────────── │
│ CV đã trích xuất           │
│ …                          │
└────────────────────────────┘
```

Thanh thao tác ở điện thoại nằm **trong luồng trang** ngay dưới khối tên ứng viên, nút xếp dọc rộng hết
cột. **Không** `fixed`/`sticky` ở đáy màn hình — không che nội dung, không đè lên evidence khi cuộn.

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 7.3 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/pages/HrApplicationDetailPage.tsx` | Trang; đọc `:id`, `?tab=`; gọi E1; render đầu trang + `Tabs` |
| `frontend/src/features/applicationDetail/api.ts` | Gọi E1–E5 |
| `frontend/src/features/applicationDetail/queries.ts` | Hook TanStack Query cho E1–E5; khoá query theo `applicationId` |
| `frontend/src/features/applicationDetail/types.ts` | Kiểu của `ApplicationHrDetailResponse`, `ApplicationScoresResponse`, `ApplicationExplanationResponse` |
| `frontend/src/features/applicationDetail/ApplicationDetailHeader.tsx` | Liên kết quay lại, tên ứng viên, badge, ngày nộp, chứa `ApplicationActionBar` |
| `frontend/src/features/applicationDetail/ApplicationActionBar.tsx` | 3 nút FR-H07 theo `status` (R-A1); mở hộp thoại dùng chung |
| `frontend/src/features/applicationDetail/CvAndScoreTab.tsx` | Ba khối của tab "CV & điểm" |
| `frontend/src/features/applicationDetail/CoverLetterBlock.tsx` | Thư giới thiệu thu gọn/mở rộng (mục 5c) |
| `frontend/src/features/applicationDetail/ScoreSection.tsx` | Khối "Điểm theo rubric": trạng thái R-T4…R-T7, nút "Chấm điểm hồ sơ" |
| `frontend/src/features/applicationDetail/ExplanationTab.tsx` | R-T8…R-T11 + nút "Tải lại" |
| `frontend/src/features/applicationDetail/HistoryTab.tsx` | Gọi E5, truyền dữ liệu vào `ApplicationHistoryTimeline` |

Thư mục `applicationDetail` theo kiểu camelCase của thư mục tính năng mới nhất (`features/candidateProfile`).

### 5b. Tách/dùng lại component có sẵn (không viết lại)

| Có sẵn | Cách dùng ở FR-H09 |
|---|---|
| `Tabs`, `TabsList`, `TabsTrigger`, `TabsContent` (`components/ui/tabs.tsx`) | Primary tabs (UI_GUIDE mục 3), **cùng cấu hình** với trang sửa Job (`HrJobEditPage.tsx:487-501`, `TabsList` mặc định) để hai trang có tab của HR nhìn giống nhau. `TabsList` thêm `max-w-full overflow-x-auto`. Không sửa `components/ui` (R-C1) |
| `Card`, `Button`, `Dialog` | Như trang sửa Job |
| `ApplicationStatusBadge` | Badge trạng thái ở đầu trang (bảng trung tính UI_GUIDE mục 3) |
| `ParseStatusBadge` | Cạnh tên file ở khối "Hồ sơ đã nộp" |
| `ScoringRunStatusBadge` | Tiến độ ở R-T5 và dòng "lượt mới nhất" ở R-T7 |
| `CriterionScoreBreakdown` | Danh sách tiêu chí + diễn giải + evidence (R-T7). Hàng tiêu đề tiêu chí đổi sang xếp dọc dưới `sm` (mục 8). Sau R-P3, component này chỉ còn được dùng ở trang này (`rg -n "CriterionScoreBreakdown" frontend/src` khi code để xác nhận), nên thay đổi responsive không ảnh hưởng màn hình khác; giao diện từ `sm` trở lên giữ nguyên |
| `ExplanationReport` | Tab "Giải thích" (R-T9…R-T11) |
| `ApplicationHistoryTimeline` | Tách thành phần hiển thị nhận `history`/`isLoading`/`isError` qua prop; trang ứng viên truyền từ `useApplicationHistoryQuery` (R-C3) |
| Phần thân `ResumeParsedDataDialog` | Tách thành `features/resumes/ResumeParsedDataView.tsx` nhận `data`; hộp thoại ứng viên dùng lại view này (R-C5) |
| `InterviewInvitationDialog` | Nới prop thành `{ id, candidateName }` + danh sách query cần làm mới (R-A4) |
| Hộp xác nhận Từ chối/Trúng tuyển | Chuyển từ `ApplicationsTab.tsx:332-345,463-489` sang `features/applications/ApplicationStatusConfirmDialog.tsx`, nội dung chữ giữ nguyên (R-A4) |
| `scoringDisabledReason` (`features/scoring/scoringRules.ts`) | Nút "Chấm điểm hồ sơ" (R-S2) |
| `useScoringRunsQuery`, `useCreateScoringRunMutation` (`features/scoring/queries.ts:169`) | Tiến độ + tạo lượt. Dùng **đúng** hook ở `features/scoring/queries.ts` (nhận `jobId`), mở rộng để làm mới thêm `candidatesKeyPrefix()` (`features/candidates/queries.ts:14`) và các query của trang (E1, E3, E4) (R-S4). Không tạo hook thứ ba; không đổi hook trùng tên ở `features/candidates/queries.ts:31` |
| Hàm tải blob CV gốc (`ApplicationsTab.tsx:175-193`) | Tách thành `features/scoring/downloadApplicationResume.ts`, dùng ở cả danh sách và trang này (R-C4) |
| `formatTotalScore` (`ApplicationsTab.tsx:38-40`) | Tách cùng file với hàm định dạng dùng chung để tổng điểm ở trang này hiện **đúng** như cột Tổng điểm của danh sách (2 chữ số thập phân) |

### 5c. Thư giới thiệu dài

- Mặc định thu gọn ở **6 dòng** (`line-clamp-6`, chữ `text-m3-body-md` cao dòng 20px → tối đa 120px).
- Nút chữ "Xem thêm" chỉ hiện khi nội dung thật sự bị cắt (đo `scrollHeight > clientHeight` sau khi
  render và khi đổi kích thước cửa sổ). Bấm → bỏ `line-clamp`, nút đổi thành "Thu gọn".
- `whitespace-pre-wrap break-words` (R-L2), không render HTML.
- Nút là `<button type="button">` dạng chữ, `text-m3-primary` (nằm trên thẻ trắng, 4.64:1), có
  `aria-expanded` và `aria-controls` trỏ tới đoạn văn bản.

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Thẻ chứa trang | `bg-m3-surface` | — | Nền trang là `m3-surface-container` (UI_GUIDE mục 5) |
| Liên kết "← {tên tin}", "Xem thêm"/"Thu gọn" | (thẻ trắng) | `text-m3-primary` | 4.64:1 chỉ trên `m3-surface` |
| Tên ứng viên | — | `text-m3-on-surface`, `text-m3-title-lg` | — |
| Ngày nộp, chú thích "Hệ thống tính theo trọng số…", câu lý do khoá nút | — | `text-m3-on-surface-variant` | Chỉ trên `m3-surface` (4.83:1) |
| Phân khối giữa ba khối của tab | — | `border-m3-outline-variant` | Chỉ để phân khối |
| Khung điểm (tổng + tiêu chí) | `bg-m3-surface` | `border border-m3-outline-variant` | Tiêu chí bên trong giữ nguyên token của `CriterionScoreBreakdown` |
| Tổng điểm, hạng | — | `text-m3-on-surface`, `text-m3-title-md` | **Một** màu, một cỡ cho mọi giá trị; không đổi theo điểm hay hạng |
| Câu trạng thái R-T1/R-T2/R-T4/R-T8, `errorMessage` của R-T6, "Lượt chấm mới nhất…" | — | `text-m3-on-surface` | Không dùng `m3-error` cho R-T6: chấm lỗi là lỗi kỹ thuật, không phải nhận xét về ứng viên; mã lỗi đã nói rõ |
| Thông báo lỗi tải tab / lỗi tải CV gốc | — | Icon `AlertCircle` `text-m3-error` + chữ `text-m3-on-surface` | Như `ApplicationsTab.tsx:317-322` |
| Ba nút quyết định | — | `variant="outline"` (viền `m3-outline`) | Cùng mức nhấn mạnh, như Sheet cũ (`ApplicationsTab.tsx:287-299`) |
| "Xem CV gốc", "Chấm điểm hồ sơ", "Tải lại" | — | `variant="outline"` | — |

## 6. Trạng thái hiển thị

Trạng thái theo field backend trả về (REQUIREMENT mục 3.4); dưới đây là cách hiện.

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Đang tải đầu trang (E1) | Cả thẻ | Skeleton xám: 1 dòng tên, 1 dòng badge, 3 ô tab (UI_GUIDE mục 5); không spinner toàn trang |
| Lỗi mạng/5xx ở E1 | Cả thẻ | "Không tải được hồ sơ đơn ứng tuyển." + nút "Thử lại" |
| **R-T13** E1 trả 403, 404 hoặc 400 (id không phải UUID) | Cả thẻ | "Không tìm thấy đơn ứng tuyển." + liên kết "Về danh sách ứng viên" → `/hr/candidates`. Cùng câu cho 403, 404 và 400; không hiện tab, không gọi E2–E5 |
| Đang tải một tab | Trong tab | Skeleton của phần đó; đầu trang và thanh thao tác vẫn dùng được |
| Lỗi tải một tab | Trong tab | Icon `AlertCircle` + "Không tải được dữ liệu, vui lòng thử lại." + nút "Thử lại" (chỉ tải lại tab đó) |
| **R-T1** CV `PENDING`/`PROCESSING` | Khối "CV đã trích xuất" | "CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong." "Xem CV gốc" ở khối trên vẫn bấm được (R-V1) |
| **R-T2** CV `FAILED` | Khối "CV đã trích xuất" | "Trích xuất CV thất bại." + dòng `resumeParseError` nguyên văn. "Xem CV gốc" vẫn bấm được |
| **R-T3** CV `DONE` | Khối "CV đã trích xuất" | `ResumeParsedDataView`; mục nào trống hiện "Chưa có dữ liệu" (UI_GUIDE mục 4 "Dữ liệu thiếu") |
| **R-T3b** Thư giới thiệu | Khối "Hồ sơ đã nộp" | Có nội dung → khối mục 5c. `null`/rỗng → **không** hiện tiêu đề "Thư giới thiệu" |
| **R-T4** chưa chấm | Khối điểm | "Đơn này chưa được chấm điểm." + nút "Chấm điểm hồ sơ" (khoá/mở theo hàm) |
| **R-T5** đang chấm | Khối điểm | `ScoringRunStatusBadge` (N/M tiêu chí), poll có sẵn; nút hiện, khoá/mở theo hàm; hết 10 phút → dòng "Đã dừng tự động cập nhật do chờ quá lâu." + nút "Tải lại" (như `ApplicationsTab.tsx:404-412`) |
| **R-T6** lỗi, chưa có DONE | Khối điểm | `ScoringRunStatusBadge` trạng thái `FAILED` (chữ có sẵn "Chấm điểm thất bại", không viết thêm câu trùng) + `errorMessage` nguyên văn; nút hiện |
| **R-T7** có DONE | Khối điểm | Khung 4a; nếu lượt mới nhất khác lượt DONE thì dòng badge + câu "Lượt chấm mới nhất …" phía trên khung |
| **R-T8** chưa có DONE | Tab Giải thích | "Chưa có báo cáo giải thích vì đơn chưa có lượt chấm hoàn tất." |
| **R-T9** `PENDING` | Tab Giải thích | Câu có sẵn của `ExplanationReport` + nút "Tải lại" (icon `RotateCw`). Đang tải lại: nút khoá, nhãn "Đang tải lại…". Không tự poll (R-T14) |
| **R-T10** `FAILED` | Tab Giải thích | Câu có sẵn của `ExplanationReport`; không có nút "Tải lại" |
| **R-T11** có báo cáo | Tab Giải thích | `ExplanationReport` + dòng "Báo cáo của lượt chấm hoàn tất ngày …" |
| **R-T12** | Tab Lịch sử | Đang tải / lỗi / "Chưa có lịch sử chuyển trạng thái." / dòng thời gian — theo `ApplicationHistoryTimeline` |
| Trạng thái cuối (`HIRED`/`REJECTED`/`WITHDRAWN`) | Thanh thao tác | Thay ba nút bằng một dòng chữ (mục 7); badge vẫn hiện |
| Thao tác đang gửi | Hộp thoại | Hành vi có sẵn của hai hộp thoại (nút khoá, nhãn "Đang lưu..."/"Đang gửi...") |
| Lỗi tải CV gốc | Dưới nút "Xem CV gốc" | Icon `AlertCircle` + "Tải CV gốc thất bại, vui lòng thử lại." (R-V3) |
| Lỗi tạo lượt chấm | Dưới nút "Chấm điểm hồ sơ" | Icon `AlertCircle` + thông điệp backend, fallback "Tạo lượt chấm điểm thất bại, vui lòng thử lại." (như `ApplicationsTab.tsx:451-455`) |

Không có trạng thái "AI đang xử lý đồng bộ": trang không gọi AI (REQUIREMENT mục 5).

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Tiêu đề `HrLayout` | "Hồ sơ ứng tuyển" |
| Liên kết quay lại | "← {jobTitle}" (`aria-label` "Quay lại danh sách ứng viên của tin {jobTitle}") |
| Ngày nộp | "Nộp ngày {dd/MM/yyyy HH:mm}" (định dạng `vi-VN` như `ApplicationsTab.tsx:28-36`) |
| Tab | "CV & điểm" · "Giải thích" · "Lịch sử" |
| Nút quyết định | "Mời phỏng vấn" · "Từ chối" · "Trúng tuyển" (như Sheet cũ) |
| Trạng thái cuối — `HIRED`/`REJECTED` | "Đơn đã có kết quả cuối. Không còn thao tác nào." |
| Trạng thái cuối — `WITHDRAWN` | "Ứng viên đã rút đơn. Không còn thao tác nào." |
| Khối 1 | "Hồ sơ đã nộp" |
| Nút tải file | "Xem CV gốc" |
| Thư giới thiệu | "Thư giới thiệu" · "Xem thêm" · "Thu gọn" |
| Khối 2 | "Điểm theo rubric" |
| Tổng điểm | "Tổng điểm {67.50} / 100" |
| Hạng | "Hạng {n}" |
| Chú thích dưới tổng điểm | "Hệ thống tính theo trọng số rubric từ lượt chấm hoàn tất ngày {dd/MM/yyyy}." |
| R-T4 | "Đơn này chưa được chấm điểm." |
| R-T5, R-T6 | Chữ có sẵn của `ScoringRunStatusBadge` ("Đang chờ xử lý", "Đang chấm — Đã chấm N/M tiêu chí", "Đã chấm xong, đang chờ tổng hợp", "Chấm điểm thất bại") |
| R-T7 — lượt mới nhất khác lượt DONE | "Lượt chấm mới nhất chưa hoàn tất hoặc không thành công. Điểm bên dưới là của lượt hoàn tất ngày {dd/MM/yyyy}." |
| Nút chấm | "Chấm điểm hồ sơ" |
| Câu lý do khoá nút chấm | Nguyên văn từ `scoringDisabledReason` (không viết câu mới, R-S2) |
| Khối 3 | "CV đã trích xuất" |
| R-T1 | "CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong." |
| R-T2 | "Trích xuất CV thất bại." |
| R-T8 | "Chưa có báo cáo giải thích vì đơn chưa có lượt chấm hoàn tất." |
| R-T9 nút | "Tải lại" · khi đang tải "Đang tải lại…" |
| R-T11 chú thích | "Báo cáo của lượt chấm hoàn tất ngày {dd/MM/yyyy}." |
| R-T13 | "Không tìm thấy đơn ứng tuyển." · liên kết "Về danh sách ứng viên" |
| Lỗi tải trang | "Không tải được hồ sơ đơn ứng tuyển." · nút "Thử lại" |
| Lỗi tải tab | "Không tải được dữ liệu, vui lòng thử lại." · nút "Thử lại" |
| Liên kết ở danh sách (R-E1, R-E2) | "Xem hồ sơ" |

Chuỗi đã có ở `ExplanationReport`, `CriterionScoreBreakdown`, `ApplicationHistoryTimeline`, hai hộp thoại
FR-H07, `ScoringRunStatusBadge` giữ nguyên, không sửa.

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Đầu trang | Tabs | Tab "CV & điểm" |
|---|---|---|---|
| Expanded (≥ `lg`) | Tên + badge + ngày bên trái, thanh thao tác bên phải cùng hàng (`flex justify-between`, nút xuống dòng nếu thiếu chỗ) | `TabsList` `w-fit` | Một cột; hàng tiêu đề khối có nút bên phải |
| Medium (`sm`–`lg`) | Như expanded nhưng thanh thao tác xuống hàng dưới tên, nút nằm ngang | Như trên | Như trên |
| Compact (< `sm`, 375px) | Tên, badge, ngày xếp dọc; thanh thao tác dưới đó, mỗi nút `w-full`, xếp dọc, **trong luồng trang** | `TabsList` `max-w-full overflow-x-auto`; không bẻ nhãn tab | Nút "Xem CV gốc", "Chấm điểm hồ sơ" `w-full` dưới tiêu đề khối; hàng tiêu chí của `CriterionScoreBreakdown` cho phép xuống dòng (tên tiêu chí trên, "4/5 điểm · Trọng số 35%" dưới) |

- Không có thanh dưới đáy cố định ở bất kỳ khổ nào (yêu cầu "thanh thao tác không che nội dung").
- Không cuộn ngang toàn trang ở 375px; chỉ `TabsList` được cuộn ngang nếu cần.
- Evidence (`blockquote`) và thư giới thiệu tự ngắt từ dài (`break-words`).

## 9. Khả năng tiếp cận

- Một `h1` duy nhất là tên ứng viên; tiêu đề ba khối là `h2`.
- Tabs: Radix lo `role="tablist"`/`tab`/`tabpanel`, điều hướng bằng phím mũi tên — không tự viết lại.
- Câu lý do khoá nút "Chấm điểm hồ sơ" hiện thành chữ và gắn vào nút bằng `aria-describedby`; vẫn giữ
  `title` như danh sách. Lý do: `title` không đọc được trên màn hình cảm ứng và nhiều trình đọc màn hình bỏ
  qua; chuỗi vẫn là kết quả nguyên văn của `scoringDisabledReason` (R-S2).
- Vùng tiến độ chấm (R-T5) và câu trạng thái tab Giải thích (R-T9) bọc `aria-live="polite"` để thông báo
  khi đổi trạng thái sau poll hoặc sau "Tải lại".
- Nút "Xem thêm"/"Thu gọn": `aria-expanded`, `aria-controls`.
- Sau khi đổi trạng thái đơn thành công, focus về badge trạng thái ở đầu trang (Radix `Dialog` trả focus về
  nút đã mở; nút đó có thể biến mất khi đổi trạng thái, nên phải chủ động đặt focus).
- Badge luôn có chữ, không truyền đạt bằng màu (UI_GUIDE mục 6). Icon trang trí `aria-hidden="true"`.
- Tương phản: chỉ dùng các cặp đã đo ở UI_GUIDE mục 1c/6 và bảng 5d; không có cặp mới cần đo.

## 10. Không được làm

- Không nhãn Đạt/Không đạt/Phù hợp, không đổi màu/cỡ/độ đậm tổng điểm theo giá trị, không làm nổi hạng 1,
  không icon ✓/✗ cạnh tiêu chí hay chip (CLAUDE.md §8, UI_GUIDE mục 4).
- Không đặt tổng điểm hay hạng ở đầu trang tách khỏi danh sách tiêu chí (R-G2).
- Không dùng màu đỏ/xanh cho nút quyết định; không làm một nút quyết định nổi hơn hai nút còn lại.
- Không dựng tab, nút hay khoảng trống chờ cho Sàng lọc, Câu hỏi phỏng vấn, Trao đổi, Hỏi đáp CV, Thêm vào
  kho (R-P2).
- Không thanh thao tác `fixed`/`sticky` ở đáy màn hình điện thoại.
- Không ẩn nút "Chấm điểm hồ sơ" ở bất kỳ trạng thái nào; không tự thêm điều kiện khoá (R-S1).
- Không khoá "Xem CV gốc" ở trang này (R-V1).
- Không đặt thư giới thiệu trong khối "Do AI tạo" hay kiểu evidence; không render HTML (R-L).
- Không dùng `m3-error` làm màu chữ cho câu "Trích xuất CV thất bại" hay `errorMessage` của lượt chấm (là trạng thái
  xử lý, không phải lỗi thao tác của người dùng; tránh gợi ý tiêu cực về ứng viên).
- Không hiện khác nhau giữa 403, 404 và 400 của E1 (R-T13).
- Không sửa file trong `components/ui`; không đổi giao diện màn hình ứng viên khi tách
  `ApplicationHistoryTimeline`/`ResumeParsedDataView` (R-C1, R-C3, R-C5).
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
