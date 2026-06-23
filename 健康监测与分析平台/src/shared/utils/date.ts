/**
 * 日期格式化工具（避免在业务页面里写重复的 date 逻辑）
 */
export function formatDateCN(date: Date): string {
  const y = date.getFullYear()
  const m = date.getMonth() + 1
  const d = date.getDate()
  const weekMap = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const w = weekMap[date.getDay()]
  return `${y}年${m}月${d}日 ${w}`
}
