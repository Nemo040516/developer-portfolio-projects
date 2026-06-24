/*
 * 文件速览：
 * 1. 文件职责：统一控制器层的分页参数归一化，处理 current/page/size 的兼容兜底。
 * 2. 对外入口：resolveCurrent、resolveSize、buildPage。
 * 3. 关键结构：支持 Integer 版 size 默认值，也支持 int 版固定 size。
 * 4. 阅读建议：直接看 buildPage 两个重载即可。
 */
package com.example.backend.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数工具类。
 */
public final class PageQueryUtils {

    private PageQueryUtils() {
    }

    public static int resolveCurrent(Integer current, Integer page) {
        return current != null ? current : (page != null ? page : 1);
    }

    public static int resolveSize(Integer size, int defaultSize) {
        return size != null ? size : defaultSize;
    }

    public static <T> Page<T> buildPage(Integer current, Integer page, Integer size, int defaultSize) {
        return new Page<>(resolveCurrent(current, page), resolveSize(size, defaultSize));
    }

    public static <T> Page<T> buildPage(Integer current, Integer page, int size) {
        return new Page<>(resolveCurrent(current, page), size);
    }
}
