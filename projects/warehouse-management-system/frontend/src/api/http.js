/*
 * @file 速览索引
 * @summary 前端 HTTP 基础层，负责统一请求实例、鉴权头注入、错误转换与业务码拦截。
 * @core 1. 创建 axios 实例
 * @core 2. 在请求头中注入 token
 * @core 3. 统一把后端业务异常转换成前端可读错误
 * @core 4. 统一处理 ApiResponse 的 code/message/data 结构
 * @entry 先看：axios.create、request interceptor、response interceptor、toRequestError
 * @deps 关键依赖：sessionStorage、localStorage、后端 ApiResponse 结构
 * @risk 高风险修改点：401 处理、业务码解析、拦截器返回值结构、超时与网络错误文案
 * @link 相关文件：前端/src/App.vue、后端/src/main/java/com/wms/backend/common/ApiResponse.java
 */
import axios from "axios";

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000
});

const statusMessageMap = {
  400: "请求参数错误，请检查输入后重试",
  401: "未登录或登录已过期，请重新登录",
  403: "无权限执行该操作",
  404: "请求地址不存在，请联系管理员",
  405: "请求方法不支持",
  408: "请求超时，请稍后重试",
  409: "数据冲突，请刷新页面后重试",
  422: "提交数据校验失败，请检查表单",
  429: "请求过于频繁，请稍后重试",
  500: "服务器内部错误，请稍后重试",
  502: "网关错误，请稍后重试",
  503: "服务暂不可用，请稍后重试",
  504: "网关超时，请稍后重试"
};

function pickResponseMessage(data) {
  if (!data) return "";
  if (typeof data === "string") return data;
  if (typeof data.message === "string" && data.message.trim()) return data.message;
  if (typeof data.msg === "string" && data.msg.trim()) return data.msg;
  if (typeof data.error === "string" && data.error.trim()) return data.error;
  return "";
}

function resolveErrorMessage(error) {
  if (!error) return "请求失败，请稍后重试";

  if (axios.isAxiosError(error)) {
    if (error.code === "ECONNABORTED") {
      return "请求超时，请检查网络后重试";
    }

    if (!error.response) {
      if (error.message === "Network Error") {
        return "网络连接异常，请检查网络后重试";
      }
      return "网络请求失败，请稍后重试";
    }

    const responseMessage = pickResponseMessage(error.response.data);
    if (responseMessage) {
      return responseMessage;
    }

    return statusMessageMap[error.response.status] || `请求失败（状态码 ${error.response.status}）`;
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "请求失败，请稍后重试";
}

function normalizeError(error) {
  const normalized = new Error(resolveErrorMessage(error));
  if (axios.isAxiosError(error) && error.response) {
    normalized.status = error.response.status;
    normalized.businessCode = error.response.data?.code;
  }
  return normalized;
}

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("wms_token") || localStorage.getItem("wms_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && body.code === 0) {
      return body.data;
    }
    return Promise.reject(normalizeError(new Error(body?.message || "请求失败")));
  },
  (error) => {
    return Promise.reject(normalizeError(error));
  }
);

export default http;
