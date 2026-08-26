import { ref } from 'vue'

const DARK_KEY = 'ops_theme_dark'
const isDark = ref(localStorage.getItem(DARK_KEY) === '1')

function apply() {
  document.documentElement.classList.toggle('dark', isDark.value)
}

export function useTheme() {
  apply()
  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem(DARK_KEY, isDark.value ? '1' : '0')
    apply()
  }
  return { isDark, toggle }
}
