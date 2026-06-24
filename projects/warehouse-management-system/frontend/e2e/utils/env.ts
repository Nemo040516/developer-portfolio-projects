/*
 * @file 速览索引
 * @summary E2E 环境变量解析工具，负责把 process.env 转成可复用的强类型配置对象。
 * @core 1. 读取并清洗 E2E_* 变量
 * @core 2. 提供三角色账号访问入口
 * @core 3. 统一默认值与空值兜底策略
 * @entry 先看：readEnvValue、loadE2EEnvironment、getRoleAccount
 * @deps 依赖：process.env
 * @state 关键数据：E2EEnvironment、E2EAccount、E2ERole
 * @risk 高风险修改点：变量名映射、默认账号、空白字符串处理
 * @link 相关文件：前端/e2e/fixtures/test.ts、前端/.env.e2e.example
 */
export type E2ERole = "admin" | "warehouse" | "purchaser";

export interface E2EAccount {
  username: string;
  password: string;
}

export interface E2EEnvironment {
  baseURL: string;
  accounts: Record<E2ERole, E2EAccount>;
}

// 统一读取环境变量，自动 trim 并在缺失时回退到默认值。
function readEnvValue(envName: string, fallbackValue: string): string {
  const rawValue = process.env[envName];
  if (!rawValue) {
    return fallbackValue;
  }

  const trimmedValue = rawValue.trim();
  if (!trimmedValue) {
    return fallbackValue;
  }

  return trimmedValue;
}

// 组装当前 E2E 执行环境，供夹具和测试用例统一复用。
export function loadE2EEnvironment(): E2EEnvironment {
  return {
    baseURL: readEnvValue("E2E_BASE_URL", "http://localhost:5173"),
    accounts: {
      admin: {
        username: readEnvValue("E2E_ADMIN_USERNAME", "admin"),
        password: readEnvValue("E2E_ADMIN_PASSWORD", "12345")
      },
      warehouse: {
        username: readEnvValue("E2E_WAREHOUSE_USERNAME", "warehouse"),
        password: readEnvValue("E2E_WAREHOUSE_PASSWORD", "12345")
      },
      purchaser: {
        username: readEnvValue("E2E_PURCHASER_USERNAME", "purchaser"),
        password: readEnvValue("E2E_PURCHASER_PASSWORD", "12345")
      }
    }
  };
}

// 按角色拿到账号信息，避免各用例直接拼接环境变量名。
export function getRoleAccount(e2eEnv: E2EEnvironment, role: E2ERole): E2EAccount {
  return e2eEnv.accounts[role];
}
