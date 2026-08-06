---
name: walkthrough
description: Viết tài liệu giải thích những gì đã làm trong một nhánh, lưu tại docs/walkthrough/. Dùng khi người dùng yêu cầu "viết walkthrough", "giải thích nhánh này đã làm gì", "tổng kết nhánh", hoặc khi vừa hoàn thành một mã FR và cần tài liệu bàn giao trước khi merge.
---

# Viết walkthrough cho một nhánh

Đây là tài liệu để người dùng **hiểu** code mà Claude vừa viết, phục vụ review và buổi bảo vệ đồ án.
Người đọc là sinh viên chưa từng đọc codebase này.

## Nơi lưu

`docs/walkthrough/<tên-nhánh-không-có-tiền-tố>.md`

Ví dụ nhánh `feat/fr-c01-auth` → `docs/walkthrough/fr-c01-auth.md`

Lấy tên nhánh bằng `git branch --show-current`.

## Cấu trúc bắt buộc — 7 mục

### 1. Mục tiêu
Mã FR này yêu cầu gì, viết lại bằng ngôn ngữ thường, một đoạn. Không copy nguyên văn SRS.

### 2. Các file đã tạo/sửa
Bảng hai cột: đường dẫn file | vai trò của file trong hệ thống.
Nhóm theo backend/frontend. Không liệt kê file cấu hình vặt.

### 3. Luồng chính
Mô tả **từng bước** một request đi qua hệ thống, từ lúc frontend gửi tới lúc trả response.
Nêu rõ đi qua class nào, phương thức nào, chạm bảng DB nào.

Nếu có nhiều luồng (ví dụ đăng ký và đăng nhập), mô tả riêng từng luồng.
Dùng sơ đồ mermaid nếu luồng có nhánh rẽ.

### 4. Quyết định thiết kế
Mỗi quyết định trình bày ba phần: **đã chọn gì** · **các lựa chọn khác là gì** · **vì sao chọn cái này**.

Đây là mục quan trọng nhất — hội đồng bảo vệ sẽ hỏi đúng những câu này.
Nếu một quyết định đến từ ràng buộc trong SRS thì nói rõ mã FR nào.

### 5. Ràng buộc SRS đã thực thi
Bảng: mã FR | ràng buộc | thực thi ở đâu (file + tên phương thức, hoặc tên constraint trong DB).

### 6. Đã kiểm thử gì
Liệt kê những gì đã test và **những gì chưa test**. Không được bỏ trống phần "chưa test".

### 7. Nợ kỹ thuật
Những chỗ làm tạm, giả định đã đặt, thứ cần sửa ở nhánh sau. Nếu không có thì ghi rõ "không có".

## Quy tắc viết

- Viết **tiếng Việt**.
- **Không copy nguyên code vào tài liệu.** Chỉ nêu tên class, tên phương thức, và mô tả việc nó làm.
- Giải thích cho người chưa biết codebase — không giả định người đọc đã hiểu Spring Security hay JPA.
- Trung thực về hạn chế. Tài liệu này để người dùng phát hiện vấn đề, không phải để trình bày thành tích.
- Độ dài hợp lý: 150–300 dòng. Dài hơn nghĩa là đang copy code vào.

## Sau khi viết xong

Đề xuất cho người dùng ba câu hỏi kiểm tra cụ thể cho nhánh này, theo mẫu:
1. Nếu xoá file X thì hỏng cái gì?
2. Dữ liệu đi từ đâu tới đâu, qua những class nào?
3. Vì sao chọn cách A mà không phải cách B?

Thay X, A, B bằng nội dung thật của nhánh, không để chung chung.
