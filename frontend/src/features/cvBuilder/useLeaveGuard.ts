import { useEffect } from 'react'

// FR-U12 UI.md muc 5a - hoi xac nhan khi tai lai/dong tab luc form con thay doi chua luu. Du an dung BrowserRouter (khong
// co useBlocker) nen chi chan beforeunload, khong chan dieu huong noi bo. active = form isDirty va chua luu xong; doi
// sang false (luu thanh cong) hoac unmount -> go listener. Trinh duyet tu hien cau cua no (UI.md muc 7 leaveWarning).
export function useLeaveGuard(active: boolean): void {
  useEffect(() => {
    if (!active) {
      return
    }
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      // Trinh duyet cu chi hoi khi returnValue khac rong.
      event.returnValue = true
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [active])
}
