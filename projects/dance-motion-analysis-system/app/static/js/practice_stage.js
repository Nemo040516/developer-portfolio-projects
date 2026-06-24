(function bootstrapPracticeStagePage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const analysisPlayer = window.MotionAnalysisPlayer;
    const SPEED_OPTIONS = [0.5, 1.0, 1.5, 2.0];
    const PAGE_PATH = window.location.pathname === "/analysis" ? "/analysis" : "/practice-stage";
    const IS_EMBEDDED_IN_ANALYSIS = PAGE_PATH === "/analysis";

    function getPositiveQueryParam(name) {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get(name));
        return Number.isInteger(rawValue) && rawValue > 0 ? rawValue : null;
    }

    function getPreviewTarget() {
        const params = new URLSearchParams(window.location.search);
        const value = String(params.get("preview_target") || "").trim().toLowerCase();
        if (value === "standard" || value === "learner" || value === "both") {
            return value;
        }
        return "";
    }

    function replaceStageUrl(standardVideoId, learnerVideoId) {
        const params = new URLSearchParams(window.location.search);
        if (standardVideoId) {
            params.set("standard_video_id", String(standardVideoId));
        } else {
            params.delete("standard_video_id");
        }
        if (learnerVideoId) {
            params.set("learner_video_id", String(learnerVideoId));
        } else {
            params.delete("learner_video_id");
        }
        params.delete("preview_target");
        const query = params.toString();
        window.history.replaceState(null, "", query ? (PAGE_PATH + "?" + query) : PAGE_PATH);
    }

    function resolvePreviewTarget(standardVideoId, learnerVideoId, preferredTarget) {
        if (preferredTarget === "both" && standardVideoId && learnerVideoId) {
            return "both";
        }
        if (preferredTarget === "standard" && standardVideoId) {
            return "standard";
        }
        if (preferredTarget === "learner" && learnerVideoId) {
            return "learner";
        }
        if (standardVideoId && learnerVideoId) {
            return "both";
        }
        if (standardVideoId) {
            return "standard";
        }
        if (learnerVideoId) {
            return "learner";
        }
        return "";
    }

    function resolveAnalysisMode(standardVideoId, learnerVideoId) {
        if (standardVideoId && learnerVideoId) {
            return "dual";
        }
        if (learnerVideoId) {
            return "single";
        }
        return "empty";
    }

    function getVideoLabel(item) {
        if (!item) {
            return "视频";
        }
        return item.display_name || item.original_filename || ("视频 #" + item.id);
    }

    function formatTimestamp(timestampMs) {
        if (!Number.isFinite(Number(timestampMs))) {
            return "--";
        }
        return api.formatNumber(Number(timestampMs) / 1000, 2) + "s";
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

    function buildMaterialCard(video, eyebrow, fallbackLabel, videoId) {
        const label = video ? getVideoLabel(video) : (fallbackLabel + " #" + videoId);
        return [
            '<div class="selector-summary selector-summary--media selector-summary--compact practice-stage-material-card">',
            renderVideoCover(video, "封面待生成", "video-cover--summary"),
            '<div class="selector-summary__content">',
            '<span class="practice-stage-chip"><strong>', api.escapeHtml(eyebrow), "</strong></span>",
            '<div class="selector-summary__head"><strong>', api.escapeHtml(label), "</strong>",
            '<span class="badge">#', api.escapeHtml(videoId || "--"), "</span></div>",
            '<div class="selector-summary__meta">',
            video && video.duration_sec ? "<span>" + api.formatNumber(video.duration_sec, 1) + "s</span>" : "",
            video && video.frame_rate ? "<span>" + api.formatNumber(video.frame_rate, 1) + " FPS</span>" : "",
            video && video.created_at ? "<span>" + api.escapeHtml(video.created_at) + "</span>" : "",
            "</div></div></div>",
        ].join("");
    }

    function getVideoById(items, videoId) {
        return (Array.isArray(items) ? items : []).find(function findItem(item) {
            return item.id === videoId;
        }) || null;
    }

    function upsertVideoItem(items, item) {
        if (!item || !item.id) {
            return Array.isArray(items) ? items : [];
        }
        const source = Array.isArray(items) ? items.slice() : [];
        const index = source.findIndex(function findIndex(candidate) {
            return candidate.id === item.id;
        });
        if (index >= 0) {
            source[index] = item;
            return source;
        }
        source.push(item);
        return source;
    }

    function setActionLinkState(element, href, enabled) {
        if (!element) {
            return;
        }
        element.href = enabled ? href : "#";
        element.classList.toggle("is-disabled", !enabled);
        element.setAttribute("aria-disabled", enabled ? "false" : "true");
        element.tabIndex = enabled ? 0 : -1;
    }

    function initializePage() {
        const elements = {
            page: document.getElementById("analysis-page"),
            workspaceSection: document.getElementById("analysis-workspace-section"),
            commandPanel: document.getElementById("practice-stage-anchor-overview"),
            actionPanel: document.querySelector(".practice-stage-action-panel"),
            commandCopy: document.getElementById("practice-stage-command-copy"),
            commandStatus: document.getElementById("practice-stage-command-status"),
            contextChips: document.getElementById("practice-stage-context-chips"),
            materialGrid: document.getElementById("practice-stage-material-grid"),
            actionStatus: document.getElementById("practice-stage-action-status"),
            flowButtons: Array.from(document.querySelectorAll("[data-stage-anchor]")),
            sampleFpsSelect: document.getElementById("practice-stage-sample-fps"),
            loadStandardButton: document.getElementById("practice-stage-load-standard"),
            loadLearnerButton: document.getElementById("practice-stage-load-learner"),
            feedback: document.getElementById("practice-stage-feedback"),
            actionMeta: document.getElementById("practice-stage-action-meta"),
            startAnalysisButton: document.getElementById("practice-stage-start-analysis"),
            backAnalysisLink: document.getElementById("practice-stage-back-analysis"),
            standardPreviewLink: document.getElementById("practice-stage-standard-preview-link"),
            learnerPreviewLink: document.getElementById("practice-stage-learner-preview-link"),
            modeBadge: document.getElementById("analysis-mode-badge"),
            modeTitle: document.getElementById("analysis-mode-title"),
            modeDescription: document.getElementById("analysis-mode-description"),
            sessionStatus: document.getElementById("analysis-session-status"),
            sessionFeedback: document.getElementById("analysis-session-feedback"),
            sessionPrimaryLink: document.getElementById("analysis-session-primary-link"),
            previewStatus: document.getElementById("practice-stage-preview-status"),
            previewStandardStatus: document.getElementById("practice-stage-preview-standard-status"),
            previewLearnerStatus: document.getElementById("practice-stage-preview-learner-status"),
            previewMode: document.getElementById("practice-stage-preview-mode"),
            generateFlowButton: document.getElementById("practice-stage-generate-flow"),
            learnerEyebrow: document.getElementById("practice-stage-learner-eyebrow"),
            learnerHeading: document.getElementById("practice-stage-learner-heading"),
            learnerHint: document.getElementById("practice-stage-learner-hint"),
            playbackStatus: document.getElementById("practice-stage-playback-status"),
            stepLabel: document.getElementById("practice-stage-step-label"),
            speedLabel: document.getElementById("practice-stage-speed-label"),
            progress: document.getElementById("practice-stage-progress"),
            playButton: document.getElementById("practice-stage-play"),
            pauseButton: document.getElementById("practice-stage-pause"),
            speedButton: document.getElementById("practice-stage-speed"),
            muteButton: document.getElementById("practice-stage-mute"),
            stepBackButton: document.getElementById("practice-stage-step-back"),
            stepForwardButton: document.getElementById("practice-stage-step-forward"),
            toggleStatus: document.getElementById("practice-stage-toggle-status"),
            viewModeButtons: Array.from(document.querySelectorAll("[data-stage-view-mode]")),
            toggleInputs: {
                keypoints: document.getElementById("practice-stage-toggle-keypoints"),
                trajectory: document.getElementById("practice-stage-toggle-trajectory"),
                angles: document.getElementById("practice-stage-toggle-angles"),
            },
            toggleCards: {
                keypoints: document.getElementById("practice-stage-toggle-keypoints-card"),
                trajectory: document.getElementById("practice-stage-toggle-trajectory-card"),
                angles: document.getElementById("practice-stage-toggle-angles-card"),
            },
            players: {
                standard: {
                    mediaStage: document.querySelector("#practice-stage-standard-stage .analysis-video-stage"),
                    video: document.getElementById("practice-stage-standard-player"),
                    overlay: document.getElementById("practice-stage-standard-overlay"),
                    empty: document.getElementById("practice-stage-standard-empty"),
                    title: document.getElementById("practice-stage-standard-title"),
                    caption: document.getElementById("practice-stage-standard-caption"),
                    frameMeta: document.getElementById("practice-stage-standard-frame-meta"),
                    timeMeta: document.getElementById("practice-stage-standard-time-meta"),
                    chips: document.getElementById("practice-stage-standard-overlays"),
                    readout: document.getElementById("practice-stage-standard-angle-readout"),
                },
                learner: {
                    mediaStage: document.querySelector("#practice-stage-learner-stage .analysis-video-stage"),
                    video: document.getElementById("practice-stage-learner-player"),
                    overlay: document.getElementById("practice-stage-learner-overlay"),
                    empty: document.getElementById("practice-stage-learner-empty"),
                    title: document.getElementById("practice-stage-learner-title"),
                    caption: document.getElementById("practice-stage-learner-caption"),
                    frameMeta: document.getElementById("practice-stage-learner-frame-meta"),
                    timeMeta: document.getElementById("practice-stage-learner-time-meta"),
                    chips: document.getElementById("practice-stage-learner-overlays"),
                    readout: document.getElementById("practice-stage-learner-angle-readout"),
                },
            },
        };

        if (!api || !analysisPlayer || Object.values(elements).some(function hasMissingElement(value) { return !value; })) {
            return;
        }

        const state = {
            standardVideos: [],
            learnerVideos: [],
            selectedStandardVideoId: getPositiveQueryParam("standard_video_id"),
            learnerVideoId: getPositiveQueryParam("learner_video_id"),
            mode: resolveAnalysisMode(getPositiveQueryParam("standard_video_id"), getPositiveQueryParam("learner_video_id")),
            pendingPreviewTarget: getPreviewTarget(),
            progress: 0,
            progressMax: 0,
            isPlaying: false,
            isMuted: true,
            speedIndex: 1,
            previewLoading: false,
            previewLoadingKey: "",
            previews: {
                standard: null,
                learner: null,
            },
            previewErrors: {
                standard: "",
                learner: "",
            },
            activeFlowStep: "overview",
            toggles: {
                keypoints: true,
                trajectory: false,
                angles: false,
            },
            viewMode: "skeleton",
            isCreatingAnalysis: false,
            createError: "",
        };

        function syncPageMode() {
            state.mode = resolveAnalysisMode(state.selectedStandardVideoId, state.learnerVideoId);
            if (elements.page) {
                elements.page.setAttribute("data-analysis-mode", state.mode);
            }

            if (elements.modeBadge) {
                elements.modeBadge.textContent = state.mode === "dual"
                    ? "双视频评分"
                    : (state.mode === "single" ? "单视频观察" : "等待导入");
            }
            if (elements.modeTitle) {
                elements.modeTitle.textContent = state.mode === "dual"
                    ? "双视频动作评分"
                    : (state.mode === "single" ? "单视频动作观察" : "动作预览与分析");
            }
            if (elements.modeDescription) {
                elements.modeDescription.textContent = state.mode === "dual"
                    ? "当前页专注做双视频对比、主舞台检查和评分结果生成。"
                    : (state.mode === "single"
                        ? "当前页专注做单视频动作观察，重点看关键点、骨骼、轨迹和节奏变化。"
                        : "先在导入页放入 1 个或 2 个视频，再回到这里进行观察或评分。");
            }
            if (elements.sessionStatus) {
                elements.sessionStatus.textContent = state.mode === "dual"
                    ? "双视频评分"
                    : (state.mode === "single" ? "单视频观察" : "等待导入");
            }
            if (elements.sessionFeedback) {
                elements.sessionFeedback.textContent = state.mode === "dual"
                    ? "当前已进入双视频评分流程。过程态不会留存，最终只保留评分结果和建议。"
                    : (state.mode === "single"
                        ? "当前已进入单视频观察流程。你可以直接看关键点、轨迹和动作走势，不强制定义视频角色。"
                        : "当前还没有可分析的视频。先导入素材，再回到这里查看动作。");
            }
            if (elements.sessionPrimaryLink) {
                elements.sessionPrimaryLink.textContent = state.mode === "empty" ? "去导入视频" : "重新导入视频";
            }
            if (elements.learnerEyebrow) {
                elements.learnerEyebrow.textContent = "当前画面";
            }
            if (elements.learnerHeading) {
                elements.learnerHeading.textContent = "当前视频";
            }
            if (elements.learnerHint) {
                elements.learnerHint.textContent = state.mode === "dual"
                    ? "与参考视频同步对比"
                    : "支持关键点与轨迹观察";
            }
            if (elements.generateFlowButton) {
                elements.generateFlowButton.hidden = state.mode !== "dual";
            }
        }

        function syncFlowState() {
            const preparedState = getPreparedState();
            const hasPlaybackData = playback.hasPreviewData();
            const readiness = {
                overview: Boolean(preparedState.standardItem || preparedState.learnerItem),
                workspace: Boolean(preparedState.standardPreviewLoaded || preparedState.learnerPreviewLoaded),
                playback: hasPlaybackData,
                overlays: hasPlaybackData,
                generate: Boolean(preparedState.readyToGenerate || state.isCreatingAnalysis),
            };

            elements.flowButtons.forEach(function syncFlowButton(button) {
                const anchorKey = button.getAttribute("data-stage-anchor");
                button.classList.toggle("is-active", anchorKey === state.activeFlowStep);
                button.classList.toggle("is-done", readiness[anchorKey] && anchorKey !== state.activeFlowStep);
                button.classList.toggle("is-disabled", !readiness[anchorKey] && anchorKey !== "overview");
            });
        }

        function setActiveFlowStep(anchorKey) {
            state.activeFlowStep = String(anchorKey || "overview");
            syncFlowState();
        }

        function getSelectedVideo(playerKey) {
            return playerKey === "standard"
                ? getVideoById(state.standardVideos, state.selectedStandardVideoId)
                : getVideoById(state.learnerVideos, state.learnerVideoId);
        }

        const playback = analysisPlayer.createRuntime({
            api: api,
            elements: elements,
            state: state,
            getAnalysisVideoItem: getSelectedVideo,
            getVideoLabel: getVideoLabel,
            formatTimestamp: formatTimestamp,
            onPreviewError: function handlePreviewError() {
                syncStageState();
            },
            emptyCopy: {
                standard: {
                    title: "示范动作尚未载入",
                    emptyTitle: "等待素材检查",
                    emptyText: "主舞台会把示范素材载入这里，用来确认动作和骨骼走势是否稳定。",
                },
                learner: {
                    title: "练习动作尚未载入",
                    emptyTitle: "等待素材检查",
                    emptyText: "主舞台会把练习素材载入这里，用来确认上传内容和动作走势。",
                },
            },
        });

        function getPreparedState() {
            const standardItem = getSelectedVideo("standard");
            const learnerItem = getSelectedVideo("learner");
            const standardPreviewLoaded = Boolean(
                state.previews.standard &&
                standardItem &&
                state.previews.standard.video &&
                state.previews.standard.video.id === standardItem.id
            );
            const learnerPreviewLoaded = Boolean(
                state.previews.learner &&
                learnerItem &&
                state.previews.learner.video &&
                state.previews.learner.video.id === learnerItem.id
            );

            return {
                standardItem: standardItem,
                learnerItem: learnerItem,
                standardPreviewLoaded: standardPreviewLoaded,
                learnerPreviewLoaded: learnerPreviewLoaded,
                readyToGenerate: Boolean(standardItem && learnerItem && standardPreviewLoaded && learnerPreviewLoaded),
            };
        }

        function renderMaterials() {
            const preparedState = getPreparedState();
            const cards = [];

            if (state.mode === "dual" && (preparedState.standardItem || state.selectedStandardVideoId)) {
                cards.push(buildMaterialCard(
                    preparedState.standardItem,
                    "参考视频",
                    "参考视频",
                    preparedState.standardItem ? preparedState.standardItem.id : state.selectedStandardVideoId
                ));
            }
            if (preparedState.learnerItem || state.learnerVideoId) {
                cards.push(buildMaterialCard(
                    preparedState.learnerItem,
                    "当前视频",
                    "当前视频",
                    preparedState.learnerItem ? preparedState.learnerItem.id : state.learnerVideoId
                ));
            }

            elements.materialGrid.innerHTML = cards.length
                ? cards.join("")
                : '<div class="empty-panel"><p>当前还没有可检查的素材。</p></div>';
        }

        function renderContextChips() {
            const preparedState = getPreparedState();
            const chips = [];

            if (state.mode === "dual") {
                chips.push(
                    '<span class="practice-stage-chip"><strong>参考</strong>' +
                    api.escapeHtml(preparedState.standardItem ? getVideoLabel(preparedState.standardItem) : "待带入") +
                    "</span>"
                );
            }
            chips.push(
                '<span class="practice-stage-chip"><strong>' + api.escapeHtml(state.mode === "dual" ? "评分视频" : "当前视频") + "</strong>" +
                api.escapeHtml(preparedState.learnerItem ? getVideoLabel(preparedState.learnerItem) : "待带入") +
                "</span>"
            );
            chips.push(
                '<span class="practice-stage-chip"><strong>采样</strong>' +
                api.escapeHtml(String(Number(elements.sampleFpsSelect.value || 5))) +
                " FPS</span>"
            );

            if (preparedState.standardPreviewLoaded || preparedState.learnerPreviewLoaded) {
                chips.push(
                    '<span class="practice-stage-chip"><strong>主舞台</strong>' +
                    api.escapeHtml(preparedState.readyToGenerate ? "双素材已就绪" : "已载入部分素材") +
                    "</span>"
                );
            }

            elements.contextChips.innerHTML = chips.join("");
        }

        function syncPreviewLinks(preparedState) {
            const sampleFps = Number(elements.sampleFpsSelect.value || 5);
            setActionLinkState(
                elements.standardPreviewLink,
                preparedState.standardItem
                    ? "/standard-preview?video_id=" + encodeURIComponent(preparedState.standardItem.id) + "&sample_fps=" + encodeURIComponent(sampleFps)
                    : "",
                Boolean(preparedState.standardItem)
            );
            setActionLinkState(
                elements.learnerPreviewLink,
                preparedState.learnerItem
                    ? "/learner-preview?video_id=" + encodeURIComponent(preparedState.learnerItem.id) + "&sample_fps=" + encodeURIComponent(sampleFps)
                    : "",
                Boolean(preparedState.learnerItem)
            );
        }

        function syncBackAnalysisLink() {
            if (IS_EMBEDDED_IN_ANALYSIS) {
                elements.backAnalysisLink.href = "/session";
                return;
            }
            const params = new URLSearchParams();
            if (state.selectedStandardVideoId) {
                params.set("standard_video_id", String(state.selectedStandardVideoId));
            }
            if (state.learnerVideoId) {
                params.set("learner_video_id", String(state.learnerVideoId));
            }
            const query = params.toString();
            elements.backAnalysisLink.href = query ? "/analysis?" + query : "/analysis";
        }

        function handleExternalSelectionChange(event) {
            const detail = event && event.detail ? event.detail : {};
            const nextStandardVideoId = Number(detail.standardVideoId);
            const nextLearnerVideoId = Number(detail.learnerVideoId);
            const nextStandardVideo = detail.standardVideo && detail.standardVideo.id ? detail.standardVideo : null;
            const nextLearnerVideo = detail.learnerVideo && detail.learnerVideo.id ? detail.learnerVideo : null;
            const resolvedStandardVideoId = Number.isInteger(nextStandardVideoId) && nextStandardVideoId > 0 ? nextStandardVideoId : null;
            const resolvedLearnerVideoId = Number.isInteger(nextLearnerVideoId) && nextLearnerVideoId > 0 ? nextLearnerVideoId : null;
            const standardChanged = state.selectedStandardVideoId !== resolvedStandardVideoId;
            const learnerChanged = state.learnerVideoId !== resolvedLearnerVideoId;
            const hadPreviewLoaded = Boolean(state.previews.standard || state.previews.learner);

            if (nextStandardVideo) {
                state.standardVideos = upsertVideoItem(state.standardVideos, nextStandardVideo);
            }
            if (nextLearnerVideo) {
                state.learnerVideos = upsertVideoItem(state.learnerVideos, nextLearnerVideo);
            }

            if (!standardChanged && !learnerChanged) {
                return;
            }

            state.selectedStandardVideoId = resolvedStandardVideoId;
            state.learnerVideoId = resolvedLearnerVideoId;
            syncPageMode();
            state.pendingPreviewTarget = "";
            state.createError = "";

            if (standardChanged) {
                playback.clearPlayerPreview("standard");
            }
            if (learnerChanged) {
                playback.clearPlayerPreview("learner");
            }

            replaceStageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            if (hadPreviewLoaded) {
                let nextPreviewTarget = "";

                if (standardChanged && learnerChanged && resolvedStandardVideoId && resolvedLearnerVideoId) {
                    nextPreviewTarget = "both";
                } else if (standardChanged && resolvedStandardVideoId) {
                    nextPreviewTarget = "standard";
                } else if (learnerChanged && resolvedLearnerVideoId) {
                    nextPreviewTarget = "learner";
                }

                if (nextPreviewTarget) {
                    state.pendingPreviewTarget = nextPreviewTarget;
                    autoLoadCurrentSelection(nextPreviewTarget).catch(function handleAutoLoadError() {
                        syncStageState();
                    });
                    return;
                }
            }

            syncStageState();
        }

        async function autoLoadCurrentSelection(preferredTarget) {
            const previewTarget = resolvePreviewTarget(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                preferredTarget || state.pendingPreviewTarget
            );

            if (!previewTarget || state.previewLoading || state.isCreatingAnalysis) {
                syncStageState();
                return;
            }

            if (previewTarget === "standard" || previewTarget === "both") {
                await loadSelectionPreviewToStage("standard");
            }
            if (previewTarget === "learner" || previewTarget === "both") {
                await loadSelectionPreviewToStage("learner");
            }

            replaceStageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            state.pendingPreviewTarget = "";
            syncStageState();
        }

        function handleOpenStageRequest(event) {
            const detail = event && event.detail ? event.detail : {};
            const nextStandardVideoId = Number(detail.standardVideoId);
            const nextLearnerVideoId = Number(detail.learnerVideoId);
            const resolvedStandardVideoId = Number.isInteger(nextStandardVideoId) && nextStandardVideoId > 0
                ? nextStandardVideoId
                : state.selectedStandardVideoId;
            const resolvedLearnerVideoId = Number.isInteger(nextLearnerVideoId) && nextLearnerVideoId > 0
                ? nextLearnerVideoId
                : state.learnerVideoId;

            state.selectedStandardVideoId = resolvedStandardVideoId || null;
            state.learnerVideoId = resolvedLearnerVideoId || null;
            syncPageMode();
            state.pendingPreviewTarget = resolvePreviewTarget(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                String(detail.previewTarget || "").trim().toLowerCase()
            );
            state.createError = "";

            autoLoadCurrentSelection(state.pendingPreviewTarget).catch(function handleAutoLoadError() {
                syncStageState();
            });
        }

        function syncStageState() {
            const preparedState = getPreparedState();
            const isSingleMode = state.mode === "single";
            const isDualMode = state.mode === "dual";
            let stageState = "idle";
            let commandStatusText = "等待素材";
            let previewStatusText = "等待素材";
            let previewModeText = "当前以训练主舞台为主";
            let actionStatusText = "待检查";
            let commandCopyText = "当前素材会自动带入。先看动作和骨骼走势，再决定是否生成结果。";
            let feedbackText = "从其他页面带回的素材，会在这里自动检查。";
            const actionMetaItems = [
                '<span class="practice-stage-chip"><strong>采样</strong>' +
                api.escapeHtml(String(Number(elements.sampleFpsSelect.value || 5))) +
                " FPS</span>",
            ];

            if (state.isCreatingAnalysis) {
                stageState = "creating";
                commandStatusText = "生成中";
                previewStatusText = "结果生成中";
                previewModeText = "主舞台会保持当前素材，完成后跳转结果复盘页";
                actionStatusText = "生成中";
                commandCopyText = "系统正在基于当前双素材创建结果，请保持当前页面并等待跳转。";
                feedbackText = "正在生成本次练习结果。";
            } else if (state.createError) {
                stageState = preparedState.readyToGenerate ? "ready" : "error";
                commandStatusText = "创建失败";
                previewStatusText = preparedState.readyToGenerate ? "主舞台已就绪" : "待继续检查";
                actionStatusText = "创建失败";
                commandCopyText = "双素材仍保留在当前页主舞台。修正问题后，可直接重新开始生成。";
                feedbackText = "本次比对生成失败：" + state.createError;
            } else if (isSingleMode && preparedState.learnerPreviewLoaded) {
                stageState = "ready";
                commandStatusText = "观察中";
                previewStatusText = "主舞台已就绪";
                previewModeText = "当前是单视频观察模式，可直接查看关键点、轨迹和角度变化";
                actionStatusText = "观察就绪";
                commandCopyText = "当前视频已载入主舞台。你可以直接播放、逐帧回看和切换图层。";
                feedbackText = "单视频已就绪，可继续观察动作走势。";
            } else if (isSingleMode && preparedState.learnerItem) {
                stageState = "prepared";
                commandStatusText = "待观察";
                previewStatusText = "可开始观察";
                previewModeText = "当前是单视频观察模式，先把当前视频载入主舞台";
                actionStatusText = "待装配";
                commandCopyText = "当前视频已带入。先载入主舞台，再观察关键点和骨骼走势。";
                feedbackText = "单视频模式下，只需要载入当前视频即可。";
            } else if (state.previewLoading || state.previewLoadingKey) {
                stageState = "loading";
                commandStatusText = "检查中";
                previewStatusText = "主舞台装配中";
                actionStatusText = "检查中";
                commandCopyText = "正在载入当前素材。装配完成后，再确认动作和骨骼走势。";
                feedbackText = "正在载入主舞台。";
            } else if (state.previewErrors.standard || state.previewErrors.learner) {
                stageState = "error";
                commandStatusText = "检查失败";
                previewStatusText = "部分素材失败";
                previewModeText = "可先检查已成功的一侧，或回准备页调整素材";
                actionStatusText = "检查失败";
                commandCopyText = "当前至少有一侧没有载入成功。先看失败信息，再决定是否继续。";
                feedbackText = state.previewErrors.standard || state.previewErrors.learner;
            } else if (preparedState.readyToGenerate) {
                stageState = "ready";
                commandStatusText = "主舞台已就绪";
                previewStatusText = "主舞台已就绪";
                previewModeText = "示范和练习都已检查，可直接生成结果";
                actionStatusText = "可开始";
                commandCopyText = "双素材已就绪。确认无误后，直接生成结果。";
                feedbackText = "双素材检查完成，可直接生成结果。";
            } else if (preparedState.standardPreviewLoaded && preparedState.learnerItem && !preparedState.learnerPreviewLoaded) {
                stageState = "partial";
                commandStatusText = "示范已就绪";
                previewStatusText = "示范已就绪";
                previewModeText = "示范已确认，继续载入练习即可开始";
                actionStatusText = "待补练习";
                commandCopyText = "示范已检查完成。再载入练习，就可以生成结果。";
                feedbackText = "示范已检查完成。继续载入练习素材。";
            } else if (preparedState.learnerPreviewLoaded && preparedState.standardItem && !preparedState.standardPreviewLoaded) {
                stageState = "partial";
                commandStatusText = "练习已就绪";
                previewStatusText = "练习已就绪";
                previewModeText = "练习已确认，继续载入示范即可开始";
                actionStatusText = "待补示范";
                commandCopyText = "练习已检查完成。再载入示范，就可以生成结果。";
                feedbackText = "练习已检查完成。继续载入示范素材。";
            } else if (preparedState.standardItem || preparedState.learnerItem) {
                stageState = "prepared";
                commandStatusText = "待检查";
                previewStatusText = "可开始检查";
                actionStatusText = "待检查";
                commandCopyText = "素材已带入。先载入主舞台，再确认动作和骨骼走势。";
                feedbackText = "素材已带入。先把示范或练习载入主舞台。";
            }

            if (elements.workspaceSection) {
                elements.workspaceSection.setAttribute("data-stage-state", stageState);
            }
            if (elements.commandPanel) {
                elements.commandPanel.setAttribute("data-stage-state", stageState);
            }
            if (elements.actionPanel) {
                elements.actionPanel.setAttribute("data-stage-state", stageState);
            }

            elements.commandStatus.textContent = commandStatusText;
            elements.previewStatus.textContent = previewStatusText;
            elements.previewMode.textContent = previewModeText;
            elements.actionStatus.textContent = actionStatusText;
            elements.commandCopy.textContent = commandCopyText;
            elements.feedback.textContent = feedbackText;
            if (preparedState.standardPreviewLoaded || preparedState.learnerPreviewLoaded) {
                actionMetaItems.push(
                    '<span class="practice-stage-chip"><strong>主舞台</strong>' +
                    api.escapeHtml(isDualMode && preparedState.readyToGenerate ? "双素材已就绪" : "已载入素材") +
                    "</span>"
                );
            } else if (preparedState.standardItem || preparedState.learnerItem) {
                actionMetaItems.push('<span class="practice-stage-chip"><strong>主舞台</strong>待装配</span>');
            } else {
                actionMetaItems.push('<span class="practice-stage-chip"><strong>主舞台</strong>等待素材</span>');
            }
            actionMetaItems.push(
                '<span class="practice-stage-chip"><strong>下一步</strong>' +
                api.escapeHtml(isDualMode
                    ? (preparedState.readyToGenerate ? "可开始生成结果" : actionStatusText)
                    : (preparedState.learnerPreviewLoaded ? "继续观察动作" : actionStatusText)) +
                "</span>"
            );
            elements.actionMeta.innerHTML = actionMetaItems.join("");
            elements.previewStandardStatus.textContent = state.previewErrors.standard
                ? "参考视频检查失败"
                : (preparedState.standardItem
                    ? (preparedState.standardPreviewLoaded ? "参考视频已就绪" : "参考视频待检查")
                    : (isDualMode ? "参考视频待带入" : "单视频模式无需参考"));
            elements.previewLearnerStatus.textContent = state.previewErrors.learner
                ? "当前视频检查失败"
                : (preparedState.learnerItem
                    ? (preparedState.learnerPreviewLoaded ? "当前视频已就绪" : "当前视频待检查")
                    : "当前视频待带入");
            elements.loadStandardButton.disabled = !isDualMode || !preparedState.standardItem || state.previewLoading || state.isCreatingAnalysis;
            elements.loadLearnerButton.disabled = !preparedState.learnerItem || state.previewLoading || state.isCreatingAnalysis;
            elements.loadStandardButton.textContent = preparedState.standardItem
                ? (preparedState.standardPreviewLoaded ? "重载参考视频" : "载入参考视频")
                : (isDualMode ? "当前无参考视频" : "单视频模式无需参考");
            elements.loadLearnerButton.textContent = preparedState.learnerItem
                ? (preparedState.learnerPreviewLoaded ? "重载当前视频" : "载入当前视频")
                : "当前无视频";
            elements.startAnalysisButton.disabled = !isDualMode || !preparedState.readyToGenerate || state.isCreatingAnalysis;
            elements.startAnalysisButton.textContent = state.isCreatingAnalysis
                ? "正在生成..."
                : (isDualMode ? "生成评分结果" : "双视频模式可生成评分");

            if (state.isCreatingAnalysis || (isDualMode && preparedState.readyToGenerate)) {
                setActiveFlowStep("generate");
            } else if (preparedState.standardPreviewLoaded || preparedState.learnerPreviewLoaded) {
                setActiveFlowStep("workspace");
            } else if (preparedState.standardItem || preparedState.learnerItem) {
                setActiveFlowStep("overview");
            }

            syncPreviewLinks(preparedState);
            syncBackAnalysisLink();
            renderContextChips();
            renderMaterials();
            syncFlowState();
        }

        function renderToggles() {
            const toggleKeys = ["keypoints", "trajectory", "angles"];
            const enabledCount = toggleKeys.filter(function countEnabled(key) {
                return state.toggles[key];
            }).length;
            const modeLabel = state.viewMode === "skeleton"
                ? "火柴人"
                : (state.viewMode === "overlay" ? "视频叠加" : "只看视频");

            toggleKeys.forEach(function syncToggle(key) {
                const input = elements.toggleInputs[key];
                const card = elements.toggleCards[key];
                if (input) {
                    input.checked = state.toggles[key];
                }
                if (card) {
                    card.classList.toggle("is-active", state.toggles[key]);
                }
            });
            elements.viewModeButtons.forEach(function syncViewModeButton(button) {
                const mode = button.getAttribute("data-stage-view-mode");
                const isActive = mode === state.viewMode;
                button.classList.toggle("is-active", isActive);
                button.setAttribute("aria-selected", isActive ? "true" : "false");
            });

            elements.toggleStatus.textContent = modeLabel + (enabledCount ? " · " + enabledCount + " 项图层" : "");
        }

        async function loadSelectionPreviewToStage(playerKey) {
            const video = getSelectedVideo(playerKey);
            const sampleFps = Number(elements.sampleFpsSelect.value || 5);
            state.createError = "";

            if (!video) {
                syncStageState();
                return false;
            }

            try {
                await playback.loadSelectionPreview(playerKey, video.id, sampleFps);
                syncStageState();
                return true;
            } catch (error) {
                syncStageState();
                return false;
            }
        }

        async function runInitialAutoLoad() {
            await autoLoadCurrentSelection(state.pendingPreviewTarget);
        }

        async function loadVideoLibraries() {
            const results = await Promise.all([
                api.fetchJson("/api/videos/standard"),
                api.fetchJson("/api/videos/learner"),
            ]);

            state.standardVideos = Array.isArray(results[0].items) ? results[0].items.slice() : [];
            state.learnerVideos = Array.isArray(results[1].items) ? results[1].items.slice() : [];

            if (state.selectedStandardVideoId && !getSelectedVideo("standard")) {
                state.selectedStandardVideoId = null;
            }
            if (state.learnerVideoId && !getSelectedVideo("learner")) {
                state.learnerVideoId = null;
            }
            syncPageMode();
        }

        elements.playButton.addEventListener("click", function startPlayback() {
            setActiveFlowStep("playback");
            playback.start();
        });
        elements.pauseButton.addEventListener("click", function pausePlayback() {
            setActiveFlowStep("playback");
            playback.stop();
            playback.renderControls();
        });
        elements.speedButton.addEventListener("click", function cycleSpeed() {
            setActiveFlowStep("playback");
            state.speedIndex = (state.speedIndex + 1) % SPEED_OPTIONS.length;
            playback.syncSpeed();
        });
        elements.muteButton.addEventListener("click", function toggleMute() {
            setActiveFlowStep("playback");
            state.isMuted = !state.isMuted;
            playback.syncMute();
        });
        elements.stepBackButton.addEventListener("click", function stepBack() {
            setActiveFlowStep("playback");
            playback.stepBy(-1);
        });
        elements.stepForwardButton.addEventListener("click", function stepForward() {
            setActiveFlowStep("playback");
            playback.stepBy(1);
        });
        elements.progress.addEventListener("input", function syncProgress() {
            setActiveFlowStep("playback");
            playback.setProgress(Number(elements.progress.value));
        });

        Object.keys(elements.toggleInputs).forEach(function bindToggle(key) {
            const input = elements.toggleInputs[key];
            if (!input) {
                return;
            }
            input.addEventListener("change", function handleToggleChange() {
                setActiveFlowStep("overlays");
                state.toggles[key] = input.checked;
                renderToggles();
                playback.renderStage();
            });
        });

        elements.viewModeButtons.forEach(function bindViewModeButton(button) {
            button.addEventListener("click", function handleViewModeClick() {
                const nextMode = button.getAttribute("data-stage-view-mode");
                if (!nextMode || nextMode === state.viewMode) {
                    return;
                }
                setActiveFlowStep("overlays");
                state.viewMode = nextMode;
                renderToggles();
                playback.renderStage();
            });
        });

        elements.loadStandardButton.addEventListener("click", async function loadStandard() {
            setActiveFlowStep("workspace");
            await loadSelectionPreviewToStage("standard");
        });
        elements.loadLearnerButton.addEventListener("click", async function loadLearner() {
            setActiveFlowStep("workspace");
            await loadSelectionPreviewToStage("learner");
        });
        elements.sampleFpsSelect.addEventListener("change", function handleSampleFpsChange() {
            state.createError = "";
            playback.clearPlayerPreview("standard");
            playback.clearPlayerPreview("learner");
            setActiveFlowStep("overview");
            syncStageState();
        });
        elements.startAnalysisButton.addEventListener("click", async function createAnalysis() {
            const preparedState = getPreparedState();
            state.createError = "";

            if (state.mode !== "dual") {
                syncStageState();
                return;
            }
            if (!preparedState.standardItem || !preparedState.learnerItem) {
                syncStageState();
                return;
            }
            if (!preparedState.readyToGenerate) {
                elements.feedback.textContent = "开始前请先把示范和练习都载入主舞台检查完成。";
                return;
            }

            setActiveFlowStep("generate");
            state.isCreatingAnalysis = true;
            syncStageState();

            try {
                const data = await api.postJson("/api/analysis", {
                    standard_video_id: preparedState.standardItem.id,
                    learner_video_id: preparedState.learnerItem.id,
                    sample_fps: Number(elements.sampleFpsSelect.value || 5),
                });
                const analysis = data && data.analysis ? data.analysis : null;
                if (!analysis || !analysis.id) {
                    throw new Error("result id missing");
                }
                window.location.assign("/review?analysis_id=" + encodeURIComponent(analysis.id));
            } catch (error) {
                state.createError = error.message;
                state.isCreatingAnalysis = false;
                syncStageState();
            }
        });

        elements.flowButtons.forEach(function bindFlowButton(button) {
            button.addEventListener("click", function handleFlowButtonClick() {
                const anchorKey = button.getAttribute("data-stage-anchor");
                const target = document.getElementById("practice-stage-anchor-" + anchorKey);
                setActiveFlowStep(anchorKey);
                if (target) {
                    target.scrollIntoView({behavior: "smooth", block: "start"});
                }
            });
        });

        elements.standardPreviewLink.addEventListener("click", function guardStandardPreview(event) {
            if (elements.standardPreviewLink.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
            }
        });
        elements.learnerPreviewLink.addEventListener("click", function guardLearnerPreview(event) {
            if (elements.learnerPreviewLink.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
            }
        });

        syncPageMode();
        renderToggles();
        playback.renderStage();
        playback.renderControls();
        syncStageState();

        loadVideoLibraries()
            .then(runInitialAutoLoad)
            .catch(function handleError(error) {
                elements.commandStatus.textContent = "加载失败";
                elements.actionStatus.textContent = "加载失败";
                elements.previewStatus.textContent = "加载失败";
                elements.feedback.textContent = "素材列表加载失败：" + error.message;
                elements.materialGrid.innerHTML = '<div class="empty-panel"><p>当前无法读取主舞台素材，请稍后重试。</p></div>';
            });

        if (IS_EMBEDDED_IN_ANALYSIS) {
            document.addEventListener("motion:analysis-selection-change", handleExternalSelectionChange);
            document.addEventListener("motion:analysis-open-stage", handleOpenStageRequest);
        }
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
