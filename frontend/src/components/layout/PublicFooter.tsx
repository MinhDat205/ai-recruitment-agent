export function PublicFooter() {
  return (
    <footer className="bg-ink text-surface">
      <div className="mx-auto grid max-w-[1200px] grid-cols-2 gap-8 px-4 py-10 text-sm sm:grid-cols-4 md:px-6">
        <div>
          <h4 className="mb-3 font-medium">Về chúng tôi</h4>
          <ul className="flex flex-col gap-2 opacity-80">
            <li>Giới thiệu</li>
            <li>Tuyển dụng</li>
          </ul>
        </div>
        <div>
          <h4 className="mb-3 font-medium">Dành cho ứng viên</h4>
          <ul className="flex flex-col gap-2 opacity-80">
            <li>Tìm việc làm</li>
            <li>Tạo hồ sơ</li>
          </ul>
        </div>
        <div>
          <h4 className="mb-3 font-medium">Dành cho nhà tuyển dụng</h4>
          <ul className="flex flex-col gap-2 opacity-80">
            <li>Đăng tin tuyển dụng</li>
            <li>Tìm ứng viên</li>
          </ul>
        </div>
        <div>
          <h4 className="mb-3 font-medium">Liên hệ</h4>
          <ul className="flex flex-col gap-2 opacity-80">
            <li>support@ai-recruitment.example</li>
          </ul>
        </div>
      </div>
    </footer>
  )
}
