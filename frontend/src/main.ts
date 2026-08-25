import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import './styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
for (const [name, comp] of Object.entries(Icons)) {
  app.component(name, comp as never)
}

setUnauthorizedHandler(() => {
  import('./stores/auth').then(({ useAuthStore }) => {
    useAuthStore().clear()
    if (router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login' })
    }
  })
})

app.mount('#app')
