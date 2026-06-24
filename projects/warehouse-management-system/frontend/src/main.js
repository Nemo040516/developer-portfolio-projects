/*
 * @file 速览索引
 * @summary 前端启动入口，负责创建Vue应用、挂载Element Plus与全局样式。
 * @core 1. 创建根应用并挂载 `App.vue`
 * @core 2. 注入 Element Plus 中文语言包
 * @core 3. 引入全局基础样式与统一UI样式
 * @entry 先看：createApp(App).use(ElementPlus, { locale: zhCn }).mount("#app")
 * @deps 依赖：App.vue、element-plus、style.css、styles/unified-ui.css
 * @risk 高风险修改点：全局样式引入顺序、UI库初始化参数、挂载节点 `#app`
 * @link 相关文件：前端/src/App.vue、前端/src/styles/unified-ui.css
 */
import { createApp } from "vue";
import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import App from "./App.vue";
import "./style.css";
import "./styles/unified-ui.css";

createApp(App).use(ElementPlus, { locale: zhCn }).mount("#app");
