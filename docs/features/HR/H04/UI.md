# FR-H04 (mở rộng) — Giao diện

> Trạng thái: ĐÃ HOÀN THÀNH (06/11/2026). Duyệt 30/10/2026; sửa theo L17 (31/10/2026).
>
> Viết theo kế hoạch `docs/plans/2026-10-28-tang-toc-cham-diem.md` (mục "Nút chấm tất cả CV trong tin", phần "Giao
> diện") và điểm 5 mục "Sửa đổi…" của `docs/plans/2026-11-dot-bo-sung.md`.

Tham chiếu: `docs/UI_GUIDE.md` mục 1c (token `m3-*`), 3 (component), 4 (ràng buộc màn hình xếp hạng), 5 (rỗng/tải/lỗi),
6 (tương phản). Mã quy tắc `R-…` là của `REQUIREMENT.md` cùng thư mục. File này không chép lại quy tắc chung, chỉ chốt
cách áp cho phần mở rộng.

- Nhóm: HR
- Tóm tắt: Nút "Chấm tất cả (N)" ở tab Ứng viên của tin (mở rộng FR-H04, không phải mã mới)
- Phụ thuộc: FR-H03, FR-H04; sau `perf/scoring-latency`

## 1. Trạng thái

> Trạng thái: ĐÃ HOÀN THÀNH (06/11/2026). Duyệt 30/10/2026; sửa theo L17 (31/10/2026).

## 2. Route (lấy từ mục 7 UI_GUIDE)

| Route | Màn hình | Hiện có / ★Mới | FR |
|---|---|---|---|
| `/hr/jobs/:id/edit` | Sửa tin — tab "Ứng viên": thêm nút "Chấm tất cả (N)", hộp xác nhận, câu kết quả | Hiện có | FR-H02, FR-H07, …; **FR-H04 (mở rộng)** |

Không route mới, không tab mới. Dòng `/hr/jobs/:id/edit` ở UI_GUIDE mục 7 đã thêm nút "Chấm tất cả" và "FR-H04 (mở
rộng)" trong commit duyệt đặc tả (REQUIREMENT mục 11, Q12).

## 3. Điểm vào

- `/hr/jobs/:id/edit?tab=applications` (tab "Ứng viên"), đi từ danh sách tin `/hr/jobs` hoặc URL trực tiếp.
- Không có điểm vào từ thông báo, dashboard hay trang hồ sơ đơn `/hr/applications/:id`.

## 4. Bố cục (khung ASCII)

### 4a. Hàng điều khiển của tab Ứng viên — expanded (≥ `lg`)

Hàng hiện có (`ApplicationsTab.tsx:414-428`) chỉ có ô "Sắp xếp theo". Thêm nút ở **cuối** hàng, căn phải; câu lý do khi
khoá (ẩn trực quan, `sr-only`) và câu kết quả nằm ngay dưới hàng, trên bảng.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [i] Đang ở chế độ ẩn danh…                     (banner FR-H16, nếu bật)    │
│                                                                          │
│ Sắp xếp theo [Tổng điểm      v]                   [≡ Chấm tất cả (4)]    │
│                                                                          │
│ Đã xếp 4 đơn vào hàng chấm. Bỏ qua 3 đơn: 1 không ở trạng thái chờ       │
│ duyệt, 2 đã có điểm.          (role="status", sau khi xếp hàng thành công)│
│                                                                          │
│ [Đã dừng tự động cập nhật… [Tải lại]]            (có sẵn, khi quá 10 phút)│
│ ┌ thanh chọn so sánh (FR-H14, có sẵn) ─────────────────────────────────┐ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌ bảng ứng viên (có sẵn) ──────────────────────────────────────────────┐ │
└──────────────────────────────────────────────────────────────────────────┘
```

N = 0: nút cùng vị trí, bị khoá, nhãn "Chấm tất cả (0)".

### 4b. Hàng điều khiển — compact (375px)

```
┌───────────────────────────────┐
│ Sắp xếp theo [Tổng điểm   v]  │
│ [≡ Chấm tất cả (4)          ] │  ← w-full, cao ≥ 48px
│ Đã xếp 4 đơn vào hàng chấm.   │
│ Bỏ qua 3 đơn: 1 không ở trạng │
│ thái chờ duyệt, 2 đã có điểm. │
└───────────────────────────────┘
```

Câu bỏ qua (Q13, R-F5) chỉ liệt kê lý do có số > 0, theo thứ tự R-E3; ví dụ trên không có đơn nào thuộc hai lý do giữa
nên không có phần "0 …".

### 4c. Hộp xác nhận (`Dialog`)

```
┌──────────────────────────────────────────────────┐
│ Chấm tất cả hồ sơ chưa có điểm?                  │
│                                                  │
│ • Số đơn sẽ xếp vào hàng chấm: 4                 │
│ • Thời gian ước tính: khoảng 4 phút — các đơn    │
│   được chấm lần lượt, mỗi đơn khoảng 1 phút.     │
│                                                  │
│ (câu lỗi 409/404/429/mạng, role="alert")         │
│                                                  │
│                      [Huỷ]  [Chấm tất cả]        │
└──────────────────────────────────────────────────┘
```

N > 50: thêm một dòng ngay dưới dòng "Số đơn": "Mỗi lần chỉ xếp tối đa 50 đơn nộp sớm nhất; còn {N − 50} đơn, bấm lại
sau để xếp tiếp." Mọi con số trong hộp tính với `k = min(N, 50)` (R-F3). Hộp chỉ có hai ý số đơn và thời gian (L17):
không ước tính số lượt gọi AI, không câu nhắc polling.

## 5. Component

### 5a. File mới (chốt tên — `REQUIREMENT.md` mục 9 dùng đúng các tên này)

| File | Vai trò |
|---|---|
| `frontend/src/features/scoring/BulkScoringButton.tsx` | Nút "Chấm tất cả (N)" + câu lý do khoá (`sr-only`, `aria-describedby`) + câu kết quả (`role="status"`); mở `BulkScoringConfirmDialog`; nhận `applications`, `job` từ `ApplicationsTab` |
| `frontend/src/features/scoring/BulkScoringConfirmDialog.tsx` | Hộp mục 4c (số đơn, thời gian — L17); gọi mutation; hiện lỗi trong hộp |
| `frontend/src/features/scoring/bulkScoringTexts.ts` | Chuỗi mục 7 |

### 5b. File sửa

| File | Sửa |
|---|---|
| `features/scoring/ApplicationsTab.tsx` | Hàng "Sắp xếp theo" thành `flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between`; thêm `BulkScoringButton` (truyền `applications`, `job`, `jobId`). Bảng, hàng, thanh chọn giữ nguyên |
| `features/scoring/scoringRules.ts` | Thêm `isBulkScorable(application, jobRecruitmentCycle)` (R-F1); `scoringDisabledReason` không đổi |
| `features/scoring/api.ts` | `bulkCreateScoringRunsRequest(jobId)` → `POST /hr/jobs/{jobId}/scoring-runs/bulk` |
| `features/scoring/types.ts` | `BulkScoringSkipReason`, `BulkScoringRunResult` khớp DTO REQUIREMENT mục 4.3 |
| `features/scoring/queries.ts` | `useBulkCreateScoringRunsMutation(jobId)`: thành công → invalidate `hrApplicationsKeyPrefix(jobId)` + tiền tố `['scoring-runs']` (R-F4). Không đổi `MAX_POLL_DURATION_MS`, `POLL_INTERVAL_MS` |

### 5c. Dùng lại có sẵn (không viết lại)

| Có sẵn | Cách dùng |
|---|---|
| `Button`, `Dialog` (`components/ui`) | Nút, hộp xác nhận — không sửa file trong `components/ui` |
| `useHrJobQuery` (đã gọi trong `ApplicationsTab.tsx:378`) | `recruitmentCycle`, `deletedAt` cho R-F1, R-F2 — không gọi thêm query |
| `extractErrorMessage` (`lib/httpError`) | Câu lỗi backend trong hộp |
| Khuôn `ApplicationStatusConfirmDialog.tsx` | Cấu trúc hộp xác nhận, nút đang chạy |
| Khuôn khoá nút FR-H16 (`ApplicationsTab.tsx:291-305`) | `disabled` + `aria-describedby` → `<span className="sr-only">` |
| Icon `ListChecks` (`lucide-react`) | Icon trang trí của nút (`aria-hidden`) |

### 5d. Bảng token

| Thành phần | Nền | Chữ / viền | Ghi chú |
|---|---|---|---|
| Nút "Chấm tất cả (N)" | `bg-m3-primary-container` | `text-m3-on-primary-container` | Filled tonal = hành động phụ quan trọng (UI_GUIDE mục 3), 6.23:1; cao 40px (≥ `sm`), `min-h-12 w-full` (< `sm`) |
| Nút khoá | — | `disabled:opacity-38` như ô chọn FR-H14 | Không đỏ; lý do bằng chữ |
| Câu kết quả | (nền trang tab `m3-surface`) | `text-m3-on-surface` | Không tô màu theo số bỏ qua (R-F5) |
| Nội dung hộp | `bg-m3-surface` | `text-m3-on-surface`; dòng chú thích `text-m3-on-surface-variant` | 4.83:1 trên `m3-surface` |
| Lỗi trong hộp | `bg-m3-surface` | icon `AlertCircle` `text-m3-error` + chữ `text-m3-error` | `m3-error` chỉ trên `m3-surface` (4.74:1, UI_GUIDE mục 6) |
| Nút "Chấm tất cả" trong hộp | `bg-m3-primary` | `text-m3-on-primary` | Hành động chính của hộp |
| Nút "Huỷ" | — | `variant="outline"` | |

Không có cặp màu mới cần đo.

## 6. Trạng thái hiển thị

| Trạng thái | Ở đâu | Hiển thị |
|---|---|---|
| Danh sách đang tải / lỗi tải | Tab | Có sẵn ("Đang tải...", câu lỗi); nút chưa render |
| `job` chưa tải xong | Nút | Khoá, lý do "Đang tải thông tin tin tuyển dụng." |
| Tin đã xoá mềm (`job.deletedAt !== null`) | Nút | Khoá, lý do mục 7 (R-F2, Q5) |
| Danh sách rỗng (0 đơn) | Tab | Câu rỗng có sẵn; nút **vẫn** hiện, khoá, N = 0, lý do mục 7 |
| N = 0, có đơn | Nút | Khoá, lý do mục 7 |
| N > 0 | Nút | Bật, "Chấm tất cả (N)" |
| Đang gửi | Hộp | Nút "Chấm tất cả" khoá, chữ "Đang xếp hàng..."; nút "Huỷ" khoá; không đóng bằng Esc/bấm nền |
| 409 `RUBRIC_INCOMPLETE` / 404 / 403 / 429 | Hộp | Câu backend trả về (`extractErrorMessage`), `role="alert"`; hộp giữ mở |
| Lỗi mạng/5xx | Hộp | Câu cố định mục 7 |
| Thành công, `created > 0` | Dưới hàng nút | Hộp đóng, focus về nút "Chấm tất cả"; câu kết quả mục 7; danh sách làm mới, các dòng hiện badge tiến độ có sẵn |
| Thành công, `created = 0` | Dưới hàng nút | Câu "Không có đơn nào được xếp vào hàng chấm." + số bỏ qua |
| `remaining > 0` | Dưới hàng nút | Thêm câu "Còn {remaining} đơn chưa xếp vì mỗi lần tối đa 50 đơn; bấm lại để xếp tiếp." |
| AI đang xử lý | Bảng | Badge tiến độ có sẵn ở từng dòng (`ScoringRunStatusBadge`); polling có sẵn; quá 10 phút → khối "Tải lại" có sẵn |
| Đổi "Sắp xếp theo" sau khi xếp hàng | Tab | Câu kết quả giữ nguyên tới khi bấm lại nút hoặc rời tab |
| Không có quyền (403) | Tab | Không xảy ra qua UI (tab chỉ mở được cho tin của HR); nếu API trả 403 thì hiện câu backend trong hộp |

## 7. Nội dung chữ (tiếng Việt có dấu)

| Khoá | Chuỗi |
|---|---|
| Nút | "Chấm tất cả ({n})" |
| Lý do khoá — N = 0 | "Không có đơn nào cần chấm: chỉ chấm đơn đang chờ duyệt, CV đã trích xuất xong, chưa có điểm và không có lượt chấm đang chạy, thuộc chu kỳ tuyển dụng hiện tại." |
| Lý do khoá — tin đã xoá | "Tin tuyển dụng đã bị xoá, không thể chấm điểm." |
| Lý do khoá — đang tải tin | "Đang tải thông tin tin tuyển dụng." |
| Tiêu đề hộp | "Chấm tất cả hồ sơ chưa có điểm?" |
| Dòng số đơn | "Số đơn sẽ xếp vào hàng chấm: {k}" |
| Dòng vượt trần (N > 50) | "Mỗi lần chỉ xếp tối đa 50 đơn nộp sớm nhất; còn {N − 50} đơn, bấm lại sau để xếp tiếp." |
| Dòng thời gian | "Thời gian ước tính: khoảng {k} phút — các đơn được chấm lần lượt, mỗi đơn khoảng 1 phút." |
| Nút hộp | "Huỷ" · "Chấm tất cả" · đang chạy "Đang xếp hàng..." |
| Kết quả, `created > 0` | "Đã xếp {created} đơn vào hàng chấm." |
| Kết quả, `created = 0` | "Không có đơn nào được xếp vào hàng chấm." |
| Kết quả, bỏ qua (chỉ khi `skipped.length > 0`) | "Bỏ qua {k} đơn: {các phần}." — `k = skipped.length`; {các phần} = các nhãn lý do dưới đây có số > 0, đúng thứ tự R-E3, nối bằng ", " (Q13). Đủ cả 4: "Bỏ qua {k} đơn: {a} không ở trạng thái chờ duyệt, {b} CV chưa trích xuất xong, {c} đang có lượt chấm chạy, {d} đã có điểm." |
| Nhãn lý do — `NOT_PENDING` | "{a} không ở trạng thái chờ duyệt" |
| Nhãn lý do — `RESUME_NOT_PARSED` | "{b} CV chưa trích xuất xong" |
| Nhãn lý do — `RUN_IN_PROGRESS` | "{c} đang có lượt chấm chạy" |
| Nhãn lý do — `ALREADY_SCORED` | "{d} đã có điểm" |
| Kết quả, vượt trần | "Còn {remaining} đơn chưa xếp vì mỗi lần tối đa 50 đơn; bấm lại để xếp tiếp." |
| Lỗi mạng/5xx | "Không xếp được các đơn vào hàng chấm, vui lòng thử lại." |
| 409 `RUBRIC_INCOMPLETE` (nguyên văn backend) | "Không thể chấm điểm vì tổng trọng số rubric hiện là {x}%, cần đúng 100% và có ít nhất một tiêu chí. Vui lòng bổ sung hoặc điều chỉnh tiêu chí trước khi chấm điểm." |

"Chu kỳ tuyển dụng" là đúng từ FR-H14 đang dùng (`comparisonLabels.ts:26`, `differentCycle`). Câu kết quả không liệt kê
tên đơn bị bỏ qua (R-G3).

## 8. Responsive (compact / medium / expanded — UI_GUIDE mục 2)

| Lớp | Hàng điều khiển | Hộp xác nhận |
|---|---|---|
| Expanded (≥ `lg`) | Một hàng: "Sắp xếp theo" trái, nút phải (mục 4a) | `Dialog` rộng tối đa `sm:max-w-lg` (mặc định shadcn) |
| Medium (`sm`–`lg`) | Như expanded | Như expanded |
| Compact (< `sm`, 375px) | Xếp dọc: "Sắp xếp theo" rồi nút `w-full min-h-12` (mục 4b) | Toàn bề ngang trừ lề; hai nút xếp dọc, "Chấm tất cả" trên |

- Không nút `fixed`/`sticky`; không cuộn ngang trang ở 375px (bảng có sẵn tự cuộn trong khung của nó).
- Câu kết quả và câu lý do tự xuống dòng (`break-words`).

## 9. Khả năng tiếp cận

- Nút là `<button type="button">`; khi khoá dùng `disabled` + `aria-describedby` trỏ `<span id="bulk-scoring-reason"
  className="sr-only">` (khuôn FR-H16), không chỉ `title`.
- Câu kết quả `role="status"` (đọc khi xuất hiện, không cướp focus); lỗi trong hộp `role="alert"`.
- Hộp dùng `Dialog` (Radix lo focus trap, Esc, `aria-labelledby` tiêu đề); đóng thì focus trả về nút "Chấm tất cả (N)".
- Danh sách trong hộp là `<ul>`; icon trang trí `aria-hidden="true"`.
- Không truyền đạt kết quả chỉ bằng màu (UI_GUIDE mục 6).
- Vùng chạm ≥ 48px ở compact (`min-h-12`).

## 10. Không được làm

- Không tô màu, không nhãn "Đạt/Không đạt/Phù hợp", không icon ✓/✗ cho câu kết quả hay dòng bảng sau khi chấm (CLAUDE.md
  §8, R-G2).
- Không liệt kê tên/email ứng viên bị bỏ qua (R-G3).
- Không tự kiểm tổng trọng số 100 ở frontend (R-F6); không thêm endpoint đếm N (R-F1).
- Không viết điều kiện N nội tuyến trong `ApplicationsTab.tsx` — chỉ `isBulkScorable` (R-F1).
- Không sửa `MAX_POLL_DURATION_MS`/`POLL_INTERVAL_MS`; không vòng poll mới riêng cho lô (R-F4).
- Không cài thư viện toast, không sửa/thêm file trong `components/ui` (Q7).
- Không nút `fixed`/`sticky` ở đáy màn hình điện thoại.
- Không đổi nút "Chấm điểm hồ sơ" từng dòng (R-G4).
- Không sao chép logo, tên thương hiệu hay CSS của trang tuyển dụng nào (CLAUDE.md §8).
