import { ref } from 'vue'

const DARK_KEY = 'ops_theme_dark'

function readInitial(): boolean {
  const stored = localStorage.getItem(DARK_KEY)
  if (stored === '1') return true
  if (stored === '0') return false
  // 首次访问跟随系统偏好
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

const isDark = ref(readInitial())

function apply() {
  document.documentElement.classList.toggle('dark', isDark.value)
}

apply()

export function useTheme() {
  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem(DARK_KEY, isDark.value ? '1' : '0')
    apply()
  }
  return { isDark, toggle }
}
