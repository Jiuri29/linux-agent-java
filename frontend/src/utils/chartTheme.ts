/**
 * 统一图表主题：所有 ECharts 实例共用一套取色与工具提示样式，
 * 与设计令牌（styles.css）保持一致，暗色模式自动切换。
 */
export const CHART_PALETTE = ['#2f6bff', '#00b42a', '#ffb02e', '#7b3ff2', '#00b4d8', '#ff5c7a']

export interface ChartVars {
  axis: string
  split: string
  tooltipBg: string
  tooltipBorder: string
  tooltipText: string
  legendText: string
}

export function chartVars(): ChartVars {
  const dark = document.documentElement.classList.contains('dark')
  return {
    axis: dark ? '#6e7a91' : '#8a94a6',
    split: dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)',
    tooltipBg: dark ? '#1c2330' : '#ffffff',
    tooltipBorder: dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)',
    tooltipText: dark ? '#e6edf3' : '#303133',
    legendText: dark ? '#9aa8c0' : '#55627a',
  }
}
