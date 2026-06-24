import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { installElementPlus } from './plugins/element-plus'
import { installElementPlusIcons } from './plugins/element-icons'

const app = createApp(App)

app.use(createPinia())
app.use(router)
installElementPlus(app)
installElementPlusIcons(app)

app.mount('#app')
