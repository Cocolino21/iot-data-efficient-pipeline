// Shared ECharts theming so every panel feels coherent.
export const SERIES_COLORS = ['#818CF8', '#A78BFA', '#22D3EE', '#34D399', '#FBBF24', '#F472B6']

export const baseGrid = {
  left: 44,
  right: 16,
  top: 16,
  bottom: 28,
  containLabel: false,
}

export const axisStyle = {
  axisLine: { lineStyle: { color: '#252536' } },
  axisTick: { show: false },
  axisLabel: { color: '#475569', fontSize: 9, fontFamily: 'Inter, sans-serif' },
  splitLine: { lineStyle: { color: '#252536', type: 'solid', opacity: 0.5 } },
}

export function colorForType(type) {
  switch (type) {
    case 'temperature': return '#818CF8'
    case 'humidity':    return '#A78BFA'
    case 'co2':         return '#22D3EE'
    case 'light':       return '#FBBF24'
    case 'energy':      return '#34D399'
    case 'pressure':    return '#06B6D4'
    default: return '#818CF8'
  }
}

export const tooltipStyle = {
  trigger: 'axis',
  backgroundColor: '#252536',
  borderColor: '#333',
  borderWidth: 1,
  textStyle: { color: '#CBD5E1', fontSize: 11, fontFamily: 'Inter, sans-serif' },
  axisPointer: {
    type: 'line',
    lineStyle: { color: '#4F46E5', type: 'dashed', opacity: 0.6 },
  },
}
