(function bootstrapStandardVideosPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;

    function getVideoLabel(item) {
        if (!item) {
            return "视频";
        }
        return item.display_name || item.original_filename || ("视频 #" + item.id);
    }

    function renderVideoCover(item, emptyText, className) {
        const nextClassName = className ? "video-cover " + className : "video-cover";
        if (item && item.thumbnail_url) {
            return [
                '<div class="', nextClassName, '">',
                '<img src="', api.escapeHtml(item.thumbnail_url), '" alt="', api.escapeHtml(getVideoLabel(item) + " 封面"), '" loading="lazy">',
                "</div>",
            ].join("");
        }

        return [
            '<div class="', nextClassName, ' is-empty">',
            '<div class="video-cover__fallback">', api.escapeHtml(emptyText), "</div>",
            "</div>",
        ].join("");
    }

    function renderTechDetails(item) {
        return [
            '<details class="tech-details">',
            "<summary>查看调试信息</summary>",
            '<div class="tech-grid">',
            '<div class="info-row"><strong>导入源名（调试）</strong><span>', api.escapeHtml(item.original_filename), "</span></div>",
            '<div class="info-row"><strong>存储文件名</strong><span>', api.escapeHtml(item.stored_filename), "</span></div>",
            '<div class="info-row"><strong>服务端路径</strong><span>', api.escapeHtml(item.file_path), "</span></div>",
            "</div>",
            "</details>",
        ].join("");
    }

    function getQueryVideoId(items) {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get("video_id"));
        if (!Number.isInteger(rawValue)) {
            return items[0].id;
        }

        const matched = items.find(function matchVideo(item) {
            return item.id === rawValue;
        });
        return matched ? matched.id : items[0].id;
    }

    function renderList(items, activeId) {
        return items.map(function renderItem(item) {
            const isActive = item.id === activeId ? " is-active" : "";
            return [
                '<button class="data-card', isActive, '" type="button" data-video-id="', item.id, '">',
                renderVideoCover(item, "封面待生成", "video-cover--card"),
                '<div class="data-card__body">',
                '<div class="data-card__title"><strong>', api.escapeHtml(getVideoLabel(item)), '</strong>',
                '<span class="badge">#', item.id, '</span></div>',
                '<div class="data-card__meta">',
                '<span>', api.formatNumber(item.duration_sec, 1), 's</span>',
                '<span>', api.escapeHtml(item.width), 'x', api.escapeHtml(item.height), '</span>',
                '<span>', api.formatNumber(item.frame_rate, 1), ' FPS</span>',
                '<span>', api.escapeHtml(item.created_at), '</span>',
                '</div></div>',
                "</button>",
            ].join("");
        }).join("");
    }

    function renderDetail(item) {
        const analysisLink = "/analysis?standard_video_id="
            + encodeURIComponent(item.id)
            + "&preview_target=standard";
        const previewLink = "/standard-preview?video_id=" + encodeURIComponent(item.id);
        return [
            '<div class="detail-hero">',
            renderVideoCover(item, "封面待生成", "video-cover--detail"),
            '<div class="detail-hero__body">',
            '<strong>', api.escapeHtml(getVideoLabel(item)), '</strong>',
            '<p class="detail-note">如果这段动作就是你想模仿的内容，默认直接回训练工作台，在当前页主舞台检查示范和练习素材。只有需要单独排查时，再打开示范预览页。</p>',
            '<div class="detail-actions">',
            '<a class="action-link" href="', analysisLink, '">进训练工作台并直接检查示范</a>',
            '<a class="action-link action-link--secondary" href="', previewLink, '">需要时再开单独预览</a>',
            "</div></div></div>",
            '<div class="result-group"><strong>这段示范动作的基础信息</strong>',
            '<div class="info-grid">',
            '<div class="info-row"><strong>视频名称</strong><span>', api.escapeHtml(getVideoLabel(item)), "</span></div>",
            '<div class="info-row"><strong>示范视频 ID</strong><span>#', item.id, "</span></div>",
            '<div class="info-row"><strong>时长</strong><span>', api.formatNumber(item.duration_sec, 1), " 秒</span></div>",
            '<div class="info-row"><strong>分辨率</strong><span>', api.escapeHtml(item.width), " x ", api.escapeHtml(item.height), "</span></div>",
            '<div class="info-row"><strong>帧率</strong><span>', api.formatNumber(item.frame_rate, 1), " FPS</span></div>",
            '<div class="info-row"><strong>导入时间</strong><span>', api.escapeHtml(item.created_at), "</span></div>",
            "</div></div>",
            renderTechDetails(item),
        ].join("");
    }

    async function initializePage() {
        const listElement = document.getElementById("standard-video-list");
        const detailElement = document.getElementById("standard-video-detail");
        const statusElement = document.getElementById("standard-video-status");
        const uploadForm = document.getElementById("standard-upload-form");
        const uploadInput = document.getElementById("standard-upload-input");
        const uploadStatus = document.getElementById("standard-upload-status");
        const uploadFeedback = document.getElementById("standard-upload-feedback");

        if (!listElement || !detailElement || !statusElement || !uploadForm || !uploadInput || !uploadStatus || !uploadFeedback || !api) {
            return;
        }

        let items = [];
        let activeId = null;

        async function handleUpload(event) {
            event.preventDefault();
            const file = uploadInput.files && uploadInput.files[0];
            if (!file) {
                uploadStatus.textContent = "缺少文件";
                uploadFeedback.textContent = "请先选择一段示范视频。";
                return;
            }

            uploadStatus.textContent = "上传中";
            uploadFeedback.textContent = "正在上传示范视频并读取基础信息。";

            const formData = new FormData();
            formData.append("file", file);

            try {
                const data = await api.postForm("/api/videos/standard", formData);
                uploadStatus.textContent = "上传成功";
                uploadFeedback.textContent = "示范视频已加入列表，你现在可以直接查看预览或开始比对。";
                uploadForm.reset();
                await loadItems(data.video.id);
            } catch (error) {
                uploadStatus.textContent = "上传失败";
                uploadFeedback.textContent = "示范视频上传失败：" + error.message;
            }
        }

        async function loadItems(nextActiveId) {
            statusElement.textContent = "加载中";
            const data = await api.fetchJson("/api/videos/standard");
            items = Array.isArray(data.items) ? data.items : [];

            if (!items.length) {
                listElement.innerHTML = '<div class="empty-panel"><p>当前还没有可用的示范视频。</p></div>';
                detailElement.innerHTML = '<div class="empty-panel"><p>先上传一段示范动作，再回到这里选择本次练习要参考的视频。</p></div>';
                statusElement.textContent = "空列表";
                return false;
            }

            activeId = nextActiveId || getQueryVideoId(items);
            if (!items.some(function hasActive(item) { return item.id === activeId; })) {
                activeId = items[0].id;
            }

            updatePage();
            return true;
        }

        function updatePage() {
            const activeItem = items.find(function findItem(item) {
                return item.id === activeId;
            }) || items[0];

            listElement.innerHTML = renderList(items, activeItem.id);
            detailElement.innerHTML = renderDetail(activeItem);
            statusElement.textContent = "共 " + items.length + " 条";
        }

        try {
            uploadForm.addEventListener("submit", handleUpload);
            await loadItems();
            listElement.addEventListener("click", function handleClick(event) {
                const button = event.target.closest("[data-video-id]");
                if (!button) {
                    return;
                }

                activeId = Number(button.getAttribute("data-video-id"));
                updatePage();
            });
        } catch (error) {
            listElement.innerHTML = '<div class="empty-panel"><p>示范视频列表加载失败，请稍后重试。</p></div>';
            detailElement.innerHTML = '<div class="empty-panel"><p>当前无法展示这段示范动作的详情。</p></div>';
            statusElement.textContent = "加载失败";
        }
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
