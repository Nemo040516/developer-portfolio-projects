(function bootstrapAnalysisPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const analysisPlayer = window.MotionAnalysisPlayer;
    const SPEED_OPTIONS = [0.5, 1.0, 1.5, 2.0];
    const BODY_PART_LABELS = {
        neck: "颈部",
        chest: "胸口",
        abdomen: "腰腹",
        left_shoulder: "左肩",
        right_shoulder: "右肩",
        left_elbow: "左肘",
        right_elbow: "右肘",
        left_hand: "左手",
        right_hand: "右手",
        left_hip: "左胯",
        right_hip: "右胯",
        left_knee: "左膝",
        right_knee: "右膝",
        left_foot: "左脚",
        right_foot: "右脚",
    };

    function formatStatusLabel(status) {
        if (status === "success") {
            return "已完成";
        }
        if (status === "running") {
            return "生成中";
        }
        if (status === "failed") {
            return "失败";
        }
        return "等待开始";
    }

    function getQueryAnalysisId() {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get("analysis_id"));
        return Number.isInteger(rawValue) && rawValue > 0 ? rawValue : null;
    }

    function getStandardVideoId() {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get("standard_video_id"));
        return Number.isInteger(rawValue) && rawValue > 0 ? rawValue : null;
    }

    function getLearnerVideoId() {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get("learner_video_id"));
        return Number.isInteger(rawValue) && rawValue > 0 ? rawValue : null;
    }

    function getPreviewTarget() {
        const params = new URLSearchParams(window.location.search);
        const normalizedValue = String(params.get("preview_target") || "").trim().toLowerCase();
        if (normalizedValue === "standard" || normalizedValue === "learner" || normalizedValue === "both") {
            return normalizedValue;
        }
        return "";
    }

    function updateAnalysisUrl(analysisId, standardVideoId, learnerVideoId, transientKeys) {
        const params = new URLSearchParams(window.location.search);
        if (analysisId) {
            params.set("analysis_id", String(analysisId));
        } else {
            params.delete("analysis_id");
        }
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
        (Array.isArray(transientKeys) ? transientKeys : []).forEach(function deleteTransientKey(key) {
            params.delete(String(key));
        });
        const query = params.toString();
        const nextUrl = query ? "/analysis?" + query : "/analysis";
        window.history.replaceState(null, "", nextUrl);
    }

    function renderList(target, items, formatter) {
        if (!target) {
            return;
        }

        if (!items.length) {
            target.innerHTML = "<li>当前暂无可展示数据</li>";
            return;
        }

        target.innerHTML = items.map(formatter).join("");
    }

    function matchesVideoSearch(item, keyword) {
        const normalizedKeyword = String(keyword || "").trim().toLowerCase();
        if (!normalizedKeyword) {
            return true;
        }

        return [
            String(item.id || ""),
            String(item.display_name || ""),
            String(item.original_filename || ""),
            String(item.created_at || ""),
        ].some(function matchPart(part) {
            return part.toLowerCase().indexOf(normalizedKeyword) >= 0;
        });
    }

    function getVideoLabel(item) {
        if (!item) {
            return "视频";
        }
        return item.display_name || item.original_filename || ("视频 #" + item.id);
    }

    function humanizeMetricKey(value) {
        const normalized = String(value || "").trim();
        if (!normalized) {
            return "未命名指标";
        }
        if (normalized.indexOf("trajectory_") === 0) {
            return humanizeMetricKey(normalized.slice("trajectory_".length)) + "轨迹";
        }
        return BODY_PART_LABELS[normalized] || normalized.replace(/_/g, " ");
    }

    function formatTimestamp(timestampMs) {
        if (!Number.isFinite(Number(timestampMs))) {
            return "--";
        }
        return api.formatNumber(Number(timestampMs) / 1000, 2) + "s";
    }

    function getStructuredSuggestions(analysis) {
        const items = analysis && Array.isArray(analysis.structured_suggestions)
            ? analysis.structured_suggestions
            : [];
        return items.filter(function filterSuggestion(item) {
            return item && item.id && item.text;
        });
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

    function parseRecordDateTime(value) {
        const matched = String(value || "").match(
            /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}):(\d{2}))?$/
        );
        if (!matched) {
            return null;
        }

        return new Date(
            Number(matched[1]),
            Number(matched[2]) - 1,
            Number(matched[3]),
            Number(matched[4] || 0),
            Number(matched[5] || 0),
            Number(matched[6] || 0)
        );
    }

    function getUploadBucket(createdAt) {
        const targetDate = parseRecordDateTime(createdAt);
        if (!targetDate) {
            return {
                key: "earlier",
                label: "更早上传",
            };
        }

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const targetDay = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate());
        const diffDays = Math.round((today.getTime() - targetDay.getTime()) / 86400000);

        if (diffDays <= 0) {
            return {
                key: "today",
                label: "今天上传",
            };
        }

        if (diffDays === 1) {
            return {
                key: "yesterday",
                label: "昨天上传",
            };
        }

        return {
            key: "earlier",
            label: "更早上传",
        };
    }

    function buildLearnerVideoGroups(items) {
        const groups = [
            {key: "today", label: "今天上传", items: []},
            {key: "yesterday", label: "昨天上传", items: []},
            {key: "earlier", label: "更早上传", items: []},
        ];
        const groupMap = {};
        groups.forEach(function indexGroup(group) {
            groupMap[group.key] = group;
        });

        items.forEach(function assignItem(item) {
            groupMap[getUploadBucket(item.created_at).key].items.push(item);
        });

        return groups.filter(function filterGroup(group) {
            return group.items.length > 0;
        });
    }

    function renderVideoLibraryCardMarkup(item, activeId) {
        const activeClassName = item.id === activeId ? " is-active" : "";
        return [
            '<button class="compact-library-card', activeClassName, '" type="button" data-video-id="', item.id, '">',
            renderVideoCover(item, "封面待生成", "video-cover--card"),
            '<div class="compact-library-card__body">',
            '<div class="compact-library-card__title"><strong>', api.escapeHtml(getVideoLabel(item)), "</strong>",
            '<span class="badge">#', item.id, "</span></div>",
            '<div class="compact-library-card__meta">',
            "<span>", api.formatNumber(item.duration_sec, 1), "s</span>",
            "<span>", api.escapeHtml(item.width), " x ", api.escapeHtml(item.height), "</span>",
            "<span>", api.formatNumber(item.frame_rate, 1), " FPS</span>",
            "<span>", api.escapeHtml(item.created_at), "</span>",
            "</div></div></button>",
        ].join("");
    }

    function renderVideoSelectionSummary(target, item, emptyText) {
        if (!target) {
            return;
        }

        if (!item) {
            target.innerHTML = "<p>" + api.escapeHtml(emptyText) + "</p>";
            return;
        }

        const uploadBucket = item.video_type === "learner" ? getUploadBucket(item.created_at) : null;
        target.innerHTML = [
            '<div class="selector-summary selector-summary--media">',
            renderVideoCover(item, "封面待生成", "video-cover--summary"),
            '<div class="selector-summary__content">',
            '<div class="selector-summary__head"><strong>', api.escapeHtml(getVideoLabel(item)), "</strong>",
            '<span class="badge">#', item.id, "</span></div>",
            '<div class="selector-summary__meta">',
            "<span>", api.formatNumber(item.duration_sec, 1), "s</span>",
            "<span>", api.escapeHtml(item.width), " x ", api.escapeHtml(item.height), "</span>",
            "<span>", api.formatNumber(item.frame_rate, 1), " FPS</span>",
            uploadBucket ? "<span>" + api.escapeHtml(uploadBucket.label) + "</span>" : "",
            "<span>", api.escapeHtml(item.created_at), "</span>",
            "</div></div></div>",
        ].join("");
    }

    function renderVideoLibraryCards(target, items, activeId, emptyText) {
        if (!target) {
            return;
        }

        if (!items.length) {
            target.innerHTML = '<div class="empty-panel"><p>' + api.escapeHtml(emptyText) + "</p></div>";
            return;
        }

        target.innerHTML = items.map(function renderItem(item) {
            return renderVideoLibraryCardMarkup(item, activeId);
        }).join("");
    }

    function renderGroupedLearnerVideoCards(target, items, activeId, emptyText) {
        if (!target) {
            return;
        }

        if (!items.length) {
            target.innerHTML = '<div class="empty-panel"><p>' + api.escapeHtml(emptyText) + "</p></div>";
            return;
        }

        const groups = buildLearnerVideoGroups(items);
        target.innerHTML = groups.map(function renderGroup(group) {
            return [
                '<div class="compact-library-section__header">',
                "<strong>", api.escapeHtml(group.label), "</strong>",
                "<span>", group.items.length, " 条</span>",
                "</div>",
                group.items.map(function renderGroupItem(item) {
                    return renderVideoLibraryCardMarkup(item, activeId);
                }).join(""),
            ].join("");
        }).join("");
    }

    function truncateLabel(text, limit) {
        const normalizedText = String(text || "").trim();
        if (!normalizedText) {
            return "";
        }

        if (normalizedText.length <= limit) {
            return normalizedText;
        }

        return normalizedText.slice(0, Math.max(0, limit - 1)) + "…";
    }

    function buildPriorityItems(jointEntries, trajectoryEntries, suggestionEntries) {
        const items = [];

        if (suggestionEntries.length) {
            items.push(String(suggestionEntries[0]));
        }

        if (jointEntries.length) {
            const largestJoint = jointEntries.slice().sort(function sortJoint(a, b) {
                return Number(b[1]) - Number(a[1]);
            })[0];
            items.push(
                "优先关注 " + humanizeMetricKey(largestJoint[0]) + "，偏差 " + api.formatNumber(largestJoint[1], 1)
            );
        }

        if (trajectoryEntries.length) {
            const largestTrajectory = trajectoryEntries.slice().sort(function sortTrajectory(a, b) {
                return Number(b[1]) - Number(a[1]);
            })[0];
            items.push(
                "轨迹差异较大：" + humanizeMetricKey(largestTrajectory[0]) + "，差异 " + api.formatNumber(largestTrajectory[1], 2)
            );
        }

        suggestionEntries.slice(1).forEach(function addSuggestion(item) {
            if (items.length >= 3) {
                return;
            }
            items.push(String(item));
        });

        return items.slice(0, 3);
    }

    function sortMetricEntries(entries) {
        return entries.slice().sort(function sortEntry(left, right) {
            return Number(right[1]) - Number(left[1]);
        });
    }

    function syncSuggestionSelection(elements, suggestionId) {
        if (!elements.suggestions) {
            return;
        }
        const buttons = elements.suggestions.querySelectorAll(".result-suggestion-button");
        buttons.forEach(function toggleButton(button) {
            button.classList.toggle("is-active", button.getAttribute("data-suggestion-id") === suggestionId);
        });
    }

    function createState() {
        return {
            analysis: null,
            standardVideos: [],
            learnerVideos: [],
            selectedStandardVideoId: getStandardVideoId(),
            learnerVideoId: null,
            hasAutoCollapsedTask: false,
            shouldAutoCollapseSetupShell: false,
            isCreatingAnalysis: false,
            activeResultTab: "suggestions",
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
            toggles: {
                overlay: false,
                keypoints: true,
                trajectory: false,
                angles: false,
            },
            viewMode: "skeleton",
            focusIssue: null,
            focusedSuggestionId: "",
        };
    }

    function applyStatePill(element, tone, text) {
        if (!element) {
            return;
        }

        element.className = "result-state-pill is-" + tone;
        element.textContent = text;
    }

    function applyScoreTone(element, tone) {
        if (!element) {
            return;
        }

        element.className = "surface-card surface-card--compact result-score-card is-" + tone;
    }

    function resolveScoreTone(status, score) {
        if (status === "failed") {
            return {
                tone: "failed",
                label: "生成失败",
                caption: "这次比对没有成功完成，建议先检查输入视频是否正确。",
            };
        }

        if (status === "running") {
            return {
                tone: "running",
                label: "生成中",
                caption: "结果仍在生成中，可以先等待系统刷新。",
            };
        }

        if (status !== "success" || score === null || score === undefined) {
            return {
                tone: "waiting",
                label: "待出结果",
                caption: "开始比对后，这里会显示本次练习的整体匹配度。",
            };
        }

        if (score >= 90) {
            return {
                tone: "strong",
                label: "高度贴合",
                caption: "整体动作已经非常接近，更适合继续微调局部细节。",
            };
        }

        if (score >= 70) {
            return {
                tone: "steady",
                label: "基本贴合",
                caption: "主要动作已经对上，但局部关节和轨迹仍有优化空间。",
            };
        }

        if (score >= 60) {
            return {
                tone: "caution",
                label: "存在差异",
                caption: "建议优先看差异较大的关节和轨迹，再回到回放区确认原因。",
            };
        }

        return {
            tone: "weak",
            label: "需要纠正",
            caption: "核心动作偏差较大，建议重点回看关键片段并重新练习。",
        };
    }

    function updateInsightCounts(elements, jointCount, trajectoryCount, suggestionCount) {
        if (elements.jointCount) {
            elements.jointCount.textContent = String(jointCount);
        }
        if (elements.trajectoryCount) {
            elements.trajectoryCount.textContent = String(trajectoryCount);
        }
        if (elements.suggestionCount) {
            elements.suggestionCount.textContent = String(suggestionCount);
        }
        if (elements.insightCount) {
            elements.insightCount.textContent = String(jointCount + trajectoryCount + suggestionCount) + " 项";
        }
    }

    function resolvePreferredResultTab(tabCounts) {
        if (tabCounts.suggestions > 0) {
            return "suggestions";
        }
        if (tabCounts.joints > 0) {
            return "joints";
        }
        if (tabCounts.trajectory > 0) {
            return "trajectory";
        }
        return "suggestions";
    }

    function syncResultTabs(elements, state, preferredTab, forcePreferred) {
        if (!elements.resultTabButtons.length || !elements.resultTabPanels.length) {
            return;
        }

        const allowedTabs = ["suggestions", "joints", "trajectory"];
        let nextTab = allowedTabs.indexOf(String(preferredTab || "")) >= 0 ? String(preferredTab) : state.activeResultTab;
        if (forcePreferred || allowedTabs.indexOf(String(nextTab || "")) < 0) {
            nextTab = allowedTabs.indexOf(String(preferredTab || "")) >= 0 ? String(preferredTab) : "suggestions";
        }

        state.activeResultTab = nextTab;

        elements.resultTabButtons.forEach(function syncTabButton(button) {
            const tabName = button.getAttribute("data-result-tab");
            const isActive = tabName === nextTab;
            button.classList.toggle("is-active", isActive);
            button.setAttribute("aria-selected", isActive ? "true" : "false");
        });

        elements.resultTabPanels.forEach(function syncTabPanel(panel) {
            const panelName = panel.getAttribute("data-result-panel");
            const isActive = panelName === nextTab;
            panel.classList.toggle("is-active", isActive);
            panel.hidden = !isActive;
        });
    }

    function renderEmptyAnalysisState(elements, state, syncTaskShell) {
        elements.pageRoot.classList.remove("analysis-layout--result");
        elements.pageRoot.classList.add("analysis-layout--setup");
        elements.workflowBadge.textContent = "还未开始";
        elements.workflowText.textContent = "先锁定示范，再挑一条练习；当前组合会固定在右侧，确认后直接进检查台。";
        elements.inputStatus.textContent = "等待开始";
        state.viewMode = "skeleton";
        state.toggles.keypoints = true;
        state.toggles.trajectory = false;
        state.toggles.angles = false;
        state.focusedSuggestionId = "";
        state.focusIssue = null;
        state.activeResultTab = "suggestions";
        elements.standardBadge.textContent = state.selectedStandardVideoId ? "示范 #" + state.selectedStandardVideoId : "等待数据";
        elements.learnerBadge.textContent = state.learnerVideoId ? "练习 #" + state.learnerVideoId : "等待数据";
        state.hasAutoCollapsedTask = false;
        syncTaskShell();
    }

    function initializePage() {
        const elements = {
            standardBadge: document.getElementById("analysis-standard-badge"),
            learnerBadge: document.getElementById("analysis-learner-badge"),
            pageRoot: document.getElementById("analysis-page"),
            workflowBadge: document.getElementById("analysis-workflow-badge"),
            workflowText: document.getElementById("analysis-workflow-text"),
            inputStatus: document.getElementById("analysis-input-status"),
            inputFeedback: document.getElementById("analysis-input-feedback"),
            taskShell: document.getElementById("analysis-task-shell"),
            taskSummaryText: document.getElementById("analysis-task-summary-text"),
            taskSummaryStandard: document.getElementById("analysis-task-summary-standard"),
            taskSummaryLearner: document.getElementById("analysis-task-summary-learner"),
            taskToggleLabel: document.getElementById("analysis-task-toggle-label"),
            taskQuickHint: document.getElementById("analysis-task-quick-hint"),
            taskQuickStartButton: document.getElementById("analysis-task-quick-start"),
            commitSummaryText: document.getElementById("analysis-commit-summary-text"),
            previewStatus: document.getElementById("analysis-preview-status"),
            previewStandardStatus: document.getElementById("analysis-preview-standard-status"),
            previewLearnerStatus: document.getElementById("analysis-preview-learner-status"),
            previewMode: document.getElementById("analysis-preview-mode"),
            standardPreviewPageLink: document.getElementById("analysis-standard-preview-page-link"),
            learnerPreviewPageLink: document.getElementById("analysis-learner-preview-page-link"),
            createForm: document.getElementById("analysis-create-form"),
            standardSearchInput: document.getElementById("analysis-standard-search"),
            standardSelect: document.getElementById("analysis-standard-select"),
            standardSelection: document.getElementById("analysis-standard-selection"),
            standardLibrary: document.getElementById("analysis-standard-library"),
            openStandardPreviewButton: document.getElementById("analysis-open-standard-preview"),
            sampleFpsSelect: document.getElementById("analysis-sample-fps"),
            learnerSearchInput: document.getElementById("analysis-learner-search"),
            learnerSelect: document.getElementById("analysis-learner-select"),
            learnerSelection: document.getElementById("analysis-learner-selection"),
            learnerLibrary: document.getElementById("analysis-learner-library"),
            learnerFileInput: document.getElementById("analysis-learner-file"),
            uploadLearnerButton: document.getElementById("analysis-upload-learner"),
            openLearnerPreviewButton: document.getElementById("analysis-open-learner-preview"),
            submitButton: document.getElementById("analysis-submit-button"),
            playbackStatus: document.getElementById("analysis-playback-status"),
            stepLabel: document.getElementById("analysis-step-label"),
            speedLabel: document.getElementById("analysis-speed-label"),
            progress: document.getElementById("analysis-progress"),
            playButton: document.getElementById("analysis-play"),
            pauseButton: document.getElementById("analysis-pause"),
            speedButton: document.getElementById("analysis-speed"),
            muteButton: document.getElementById("analysis-mute"),
            stepBackButton: document.getElementById("analysis-step-back"),
            stepForwardButton: document.getElementById("analysis-step-forward"),
            toggleStatus: document.getElementById("analysis-toggle-status"),
            toggleInputs: {
                keypoints: document.getElementById("toggle-keypoints"),
                trajectory: document.getElementById("toggle-trajectory"),
                angles: document.getElementById("toggle-angles"),
            },
            toggleCards: {
                keypoints: document.getElementById("toggle-keypoints-card"),
                trajectory: document.getElementById("toggle-trajectory-card"),
                angles: document.getElementById("toggle-angles-card"),
            },
            feedbackStatus: document.getElementById("result-feedback-status"),
            statePill: document.getElementById("result-state-pill"),
            scoreCard: document.getElementById("result-score-card"),
            scoreBand: document.getElementById("result-score-band"),
            score: document.getElementById("result-score"),
            scoreCaption: document.getElementById("result-score-caption"),
            resultStatus: document.getElementById("result-status"),
            sampleFps: document.getElementById("result-sample-fps"),
            summary: document.getElementById("result-summary"),
            priorityList: document.getElementById("result-priority-list"),
            insightCount: document.getElementById("result-insight-count"),
            jointCount: document.getElementById("result-joint-count"),
            trajectoryCount: document.getElementById("result-trajectory-count"),
            suggestionCount: document.getElementById("result-suggestion-count"),
            resultTabButtons: Array.from(document.querySelectorAll("[data-result-tab]")),
            resultTabPanels: Array.from(document.querySelectorAll("[data-result-panel]")),
            jointDiffs: document.getElementById("result-joint-diffs"),
            trajectoryDiffs: document.getElementById("result-trajectory-diffs"),
            suggestions: document.getElementById("result-suggestions"),
            players: {
                standard: {
                    mediaStage: document.querySelector("#analysis-standard-stage .analysis-video-stage"),
                    video: document.getElementById("analysis-standard-player"),
                    overlay: document.getElementById("analysis-standard-overlay"),
                    empty: document.getElementById("analysis-standard-empty"),
                    title: document.getElementById("analysis-standard-title"),
                    caption: document.getElementById("analysis-standard-caption"),
                    frameMeta: document.getElementById("analysis-standard-frame-meta"),
                    timeMeta: document.getElementById("analysis-standard-time-meta"),
                    chips: document.getElementById("analysis-standard-overlays"),
                },
                learner: {
                    mediaStage: document.querySelector("#analysis-learner-stage .analysis-video-stage"),
                    video: document.getElementById("analysis-learner-player"),
                    overlay: document.getElementById("analysis-learner-overlay"),
                    empty: document.getElementById("analysis-learner-empty"),
                    title: document.getElementById("analysis-learner-title"),
                    caption: document.getElementById("analysis-learner-caption"),
                    frameMeta: document.getElementById("analysis-learner-frame-meta"),
                    timeMeta: document.getElementById("analysis-learner-time-meta"),
                    chips: document.getElementById("analysis-learner-overlays"),
                },
            },
        };

        if (!api || !analysisPlayer || !elements.progress || !elements.standardSelect || !elements.learnerSelect) {
            return;
        }

        const state = createState();
        state.learnerVideoId = getLearnerVideoId();
        const initialAnalysisId = null;
        let pendingPreviewTarget = getPreviewTarget();

        function getVideoById(items, videoId) {
            return items.find(function findItem(item) {
                return item.id === videoId;
            }) || null;
        }

        function upsertVideo(items, nextVideo) {
            const nextItems = items.filter(function keepItem(item) {
                return item.id !== nextVideo.id;
            });
            nextItems.unshift(nextVideo);
            return nextItems;
        }

        function getAnalysisVideoItem(playerKey) {
            if (!state.analysis) {
                return playerKey === "standard"
                    ? getVideoById(state.standardVideos, state.selectedStandardVideoId)
                    : getVideoById(state.learnerVideos, state.learnerVideoId);
            }

            return playerKey === "standard"
                ? getVideoById(state.standardVideos, state.analysis.standard_video_id)
                : getVideoById(state.learnerVideos, state.analysis.learner_video_id);
        }

        const playback = analysisPlayer.createRuntime({
            api: api,
            elements: elements,
            state: state,
            getAnalysisVideoItem: getAnalysisVideoItem,
            getVideoLabel: getVideoLabel,
            formatTimestamp: formatTimestamp,
            onPreviewError: function handlePreviewError() {
                syncPreviewWorkspace();
            },
        });

        function syncPageUrl(transientKeys) {
            updateAnalysisUrl(
                state.analysis ? state.analysis.id : null,
                state.selectedStandardVideoId,
                state.learnerVideoId,
                transientKeys
            );
        }

        function getSetupPreviewState() {
            const standardItem = getVideoById(state.standardVideos, state.selectedStandardVideoId);
            const learnerItem = getVideoById(state.learnerVideos, state.learnerVideoId);
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
                bothPreviewLoaded: standardPreviewLoaded && learnerPreviewLoaded,
                readyToGenerate: Boolean(standardItem && learnerItem && standardPreviewLoaded && learnerPreviewLoaded),
            };
        }

        function setPreviewPageLink(element, href, enabled) {
            if (!element) {
                return;
            }
            element.href = enabled ? href : "#";
            element.classList.toggle("is-disabled", !enabled);
            element.setAttribute("aria-disabled", enabled ? "false" : "true");
        }

        function syncPreviewWorkspace() {
            const previewState = state.analysis
                ? {
                    standardItem: getAnalysisVideoItem("standard"),
                    learnerItem: getAnalysisVideoItem("learner"),
                    standardPreviewLoaded: Boolean(
                        state.previews.standard &&
                        getAnalysisVideoItem("standard") &&
                        state.previews.standard.video &&
                        state.previews.standard.video.id === getAnalysisVideoItem("standard").id
                    ),
                    learnerPreviewLoaded: Boolean(
                        state.previews.learner &&
                        getAnalysisVideoItem("learner") &&
                        state.previews.learner.video &&
                        state.previews.learner.video.id === getAnalysisVideoItem("learner").id
                    ),
                }
                : getSetupPreviewState();
            const standardItem = previewState.standardItem;
            const learnerItem = previewState.learnerItem;
            const sampleFps = Number(elements.sampleFpsSelect.value || (state.analysis && state.analysis.sample_fps) || 5);
            const standardPreviewLoaded = previewState.standardPreviewLoaded;
            const learnerPreviewLoaded = previewState.learnerPreviewLoaded;
            let previewStatusText = "等待素材";
            let previewModeText = "当前以分析页内联检查为主";

            if (state.previewLoading || state.previewLoadingKey) {
                previewStatusText = "主舞台装配中";
            } else if (state.analysis && state.analysis.status === "success") {
                previewStatusText = "结果回看中";
                previewModeText = "当前以结果回看为主";
            } else if (state.analysis && state.analysis.status === "running") {
                previewStatusText = "结果生成中";
                previewModeText = "当前会先保留主舞台，等待结果接管回放";
            } else if (previewState.readyToGenerate) {
                previewStatusText = "主舞台已就绪";
                previewModeText = "示范和练习都已检查，可直接开始比对";
            } else if (standardPreviewLoaded && learnerPreviewLoaded) {
                previewStatusText = "双素材已检查";
                previewModeText = "当前已完成双侧检查，可继续调整或直接开始";
            } else if (standardPreviewLoaded && learnerItem && !learnerPreviewLoaded) {
                previewStatusText = "示范已就绪";
                previewModeText = "示范已检查，建议再把练习载入主舞台确认一次";
            } else if (learnerPreviewLoaded && standardItem && !standardPreviewLoaded) {
                previewStatusText = "练习已就绪";
                previewModeText = "练习已检查，建议再把示范载入主舞台确认一次";
            } else if (standardPreviewLoaded) {
                previewStatusText = "示范已就绪";
                previewModeText = "当前先确认示范动作，下一步补练习视频";
            } else if (learnerPreviewLoaded) {
                previewStatusText = "练习已就绪";
                previewModeText = "当前先确认练习动作，下一步补示范视频";
            } else if (standardItem || learnerItem) {
                previewStatusText = "可在本页检查";
            }

            if (elements.previewStatus) {
                elements.previewStatus.textContent = previewStatusText;
            }
            if (elements.previewMode) {
                elements.previewMode.textContent = previewModeText;
            }
            if (elements.previewStandardStatus) {
                elements.previewStandardStatus.textContent = !standardItem
                    ? "示范待选择"
                    : (standardPreviewLoaded ? "示范已就绪" : "示范待检查");
            }
            if (elements.previewLearnerStatus) {
                elements.previewLearnerStatus.textContent = !learnerItem
                    ? "练习待选择"
                    : (learnerPreviewLoaded ? "练习已就绪" : "练习待检查");
            }

            setPreviewPageLink(
                elements.standardPreviewPageLink,
                standardItem
                    ? "/standard-preview?video_id=" + encodeURIComponent(standardItem.id) + "&sample_fps=" + encodeURIComponent(sampleFps)
                    : "",
                Boolean(standardItem)
            );
            setPreviewPageLink(
                elements.learnerPreviewPageLink,
                learnerItem
                    ? "/learner-preview?video_id=" + encodeURIComponent(learnerItem.id) + "&sample_fps=" + encodeURIComponent(sampleFps)
                    : "",
                Boolean(learnerItem)
            );
        }

        function syncTaskShell() {
            if (!elements.taskShell) {
                return;
            }

            const previewState = getSetupPreviewState();
            const standardItem = previewState.standardItem;
            const learnerItem = previewState.learnerItem;
            const selectedSampleFps = Number(elements.sampleFpsSelect.value || 5);
            const standardPreviewLoaded = previewState.standardPreviewLoaded;
            const learnerPreviewLoaded = previewState.learnerPreviewLoaded;

            if (!state.analysis && state.shouldAutoCollapseSetupShell && previewState.readyToGenerate && elements.taskShell.open) {
                elements.taskShell.open = false;
                state.shouldAutoCollapseSetupShell = false;
            }

            if (elements.taskSummaryStandard) {
                elements.taskSummaryStandard.textContent = standardItem
                    ? "示范：" + truncateLabel(getVideoLabel(standardItem), 12)
                    : "示范待选";
            }

            if (elements.taskSummaryLearner) {
                elements.taskSummaryLearner.textContent = learnerItem
                    ? "练习：" + truncateLabel(getVideoLabel(learnerItem), 12)
                    : "练习待选";
            }

            if (elements.taskToggleLabel) {
                elements.taskToggleLabel.textContent = elements.taskShell.open ? "收起设置" : "展开设置";
            }

            if (elements.openStandardPreviewButton) {
                elements.openStandardPreviewButton.disabled = !standardItem;
                elements.openStandardPreviewButton.textContent = standardItem
                    ? (standardPreviewLoaded ? "重载示范" : "载入示范")
                    : "先选示范";
            }

            if (elements.openLearnerPreviewButton) {
                elements.openLearnerPreviewButton.disabled = !learnerItem;
                elements.openLearnerPreviewButton.textContent = learnerItem
                    ? (learnerPreviewLoaded ? "重载练习" : "载入练习")
                    : "先选练习";
            }

            if (elements.taskShell) {
                elements.taskShell.dataset.stageState = state.analysis
                    ? "result"
                    : (previewState.readyToGenerate
                        ? "ready"
                        : ((standardPreviewLoaded || learnerPreviewLoaded) ? "checked" : "setup"));
            }

            if (elements.submitButton) {
                elements.submitButton.disabled = state.isCreatingAnalysis;
                elements.submitButton.textContent = state.isCreatingAnalysis ? "正在生成..." : "生成结果";
            }

            if (elements.taskQuickStartButton || elements.taskQuickHint || elements.taskShell) {
                let quickActionTone = "waiting";
                let quickActionText = "先完成准备";
                let quickActionHint = "先选示范，再选练习；右侧会一直固定当前组合。";
                let quickActionDisabled = true;

                if (state.analysis) {
                    quickActionTone = "result";
                    quickActionText = "结果已生成";
                    quickActionHint = "需要时直接展开设置更换素材，当前先在主舞台或结果侧栏回看。";
                } else if (state.isCreatingAnalysis) {
                    quickActionTone = "busy";
                    quickActionText = "正在生成结果";
                    quickActionHint = "系统正在创建这次比对，请等待结果返回。";
                } else if (previewState.readyToGenerate) {
                    quickActionTone = "ready";
                    quickActionText = selectedSampleFps + " FPS 开始";
                    quickActionHint = "主舞台已经就绪。现在可以不展开设置，直接开始本次比对。";
                    quickActionDisabled = false;
                } else if (standardPreviewLoaded && learnerItem && !learnerPreviewLoaded) {
                    quickActionTone = "checked";
                    quickActionText = "补练习检查";
                    quickActionHint = "示范已经确认。再把练习载入主舞台，就能直接开始。";
                } else if (learnerPreviewLoaded && standardItem && !standardPreviewLoaded) {
                    quickActionTone = "checked";
                    quickActionText = "补示范检查";
                    quickActionHint = "练习已经确认。再把示范载入主舞台，就能直接开始。";
                } else if (standardItem && learnerItem) {
                    quickActionTone = "checked";
                    quickActionText = "先去主舞台";
                    quickActionHint = "素材已选好。建议先在主舞台确认一次，再开始本次比对。";
                } else if (standardItem || learnerItem) {
                    quickActionText = "补齐素材";
                    quickActionHint = standardItem
                        ? "示范已经锁定。接下来只要补一条练习视频。"
                        : "练习已经锁定。接下来只要补一条示范视频。";
                }

                if (elements.taskShell) {
                    elements.taskShell.dataset.quickState = quickActionTone;
                }
                if (elements.taskQuickStartButton) {
                    elements.taskQuickStartButton.textContent = quickActionText;
                    elements.taskQuickStartButton.disabled = quickActionDisabled;
                    elements.taskQuickStartButton.dataset.tone = quickActionTone;
                }
                if (elements.taskQuickHint) {
                    elements.taskQuickHint.textContent = quickActionHint;
                }
            }

            if (!elements.taskSummaryText) {
                return;
            }

            if (!state.analysis) {
                if (previewState.readyToGenerate) {
                    elements.taskSummaryText.textContent = "双素材已在主舞台就绪。现在可以直接快速开始，或展开设置更换素材。";
                } else if (standardPreviewLoaded && learnerItem && !learnerPreviewLoaded) {
                    elements.taskSummaryText.textContent = "示范已就绪，练习还没进主舞台。补完练习检查后，就可以直接开始本次比对。";
                } else if (learnerPreviewLoaded && standardItem && !standardPreviewLoaded) {
                    elements.taskSummaryText.textContent = "练习已就绪，示范还没进主舞台。补完示范检查后，就可以直接开始本次比对。";
                } else if (standardPreviewLoaded) {
                    elements.taskSummaryText.textContent = "示范已在主舞台就绪。接下来只要补练习视频，就能继续本次比对。";
                } else if (learnerPreviewLoaded) {
                    elements.taskSummaryText.textContent = "练习已在主舞台就绪。接下来只要补示范视频，就能继续本次比对。";
                } else if (learnerItem) {
                    elements.taskSummaryText.textContent = "素材已选好。可先加载到主舞台检查，再按 " + selectedSampleFps + " FPS 开始比对。";
                } else {
                    elements.taskSummaryText.textContent = "当前只做 3 件事：选示范、选练习、进检查台。";
                }
                if (elements.commitSummaryText) {
                    if (previewState.readyToGenerate) {
                        elements.commitSummaryText.textContent = "示范与练习都已完成主舞台检查；可按 " + selectedSampleFps + " FPS 直接生成结果，或用上方快速开始入口。";
                    } else if (standardPreviewLoaded && learnerItem && !learnerPreviewLoaded) {
                        elements.commitSummaryText.textContent = "示范已经检查完成；建议先把练习载入主舞台确认，再开始本次比对。";
                    } else if (learnerPreviewLoaded && standardItem && !standardPreviewLoaded) {
                        elements.commitSummaryText.textContent = "练习已经检查完成；建议先把示范载入主舞台确认，再开始本次比对。";
                    } else if (standardPreviewLoaded) {
                        elements.commitSummaryText.textContent = "示范已检查；接下来请选择或上传一条练习视频。";
                    } else if (learnerPreviewLoaded) {
                        elements.commitSummaryText.textContent = "练习已检查；接下来请选择一条示范视频。";
                    } else if (standardItem && learnerItem) {
                        elements.commitSummaryText.textContent = [
                            "示范：",
                            truncateLabel(getVideoLabel(standardItem), 18),
                            "；练习：",
                            truncateLabel(getVideoLabel(learnerItem), 18),
                            "；可先直接在主舞台检查，再按 ",
                            selectedSampleFps,
                            " FPS 生成结果。"
                        ].join("");
                    } else if (standardItem) {
                        elements.commitSummaryText.textContent = "示范视频已选好，接下来只要再选一条练习视频，就可以在主舞台检查后开始本次比对。";
                    } else {
                        elements.commitSummaryText.textContent = "先把示范和练习固定好；当前组合会一直保留在右侧。";
                    }
                }
                syncPreviewWorkspace();
                return;
            }

            elements.taskSummaryText.textContent = [
                "当前结果使用 ",
                standardItem ? truncateLabel(getVideoLabel(standardItem), 16) : "示范视频",
                " 与 ",
                learnerItem ? truncateLabel(getVideoLabel(learnerItem), 16) : "练习视频",
                " 进行对照，采样速度 ",
                state.analysis.sample_fps,
                " FPS；需要时可展开更换素材。"
            ].join("");

            if (elements.commitSummaryText) {
                elements.commitSummaryText.textContent = [
                    "当前结果基于 ",
                    standardItem ? truncateLabel(getVideoLabel(standardItem), 18) : "示范视频",
                    " 和 ",
                    learnerItem ? truncateLabel(getVideoLabel(learnerItem), 18) : "练习视频",
                    " 生成；需要时可展开设置重新选择素材。"
                ].join("");
            }
            syncPreviewWorkspace();
        }

        function requestCreateAnalysisFromDrawer() {
            if (!elements.createForm || state.isCreatingAnalysis) {
                return;
            }

            if (typeof elements.createForm.requestSubmit === "function") {
                elements.createForm.requestSubmit();
                return;
            }

            const submitEvent = new Event("submit", {
                bubbles: true,
                cancelable: true,
            });
            elements.createForm.dispatchEvent(submitEvent);
        }

        function syncVideoSelect(selectElement, items, selectedId, emptyLabel) {
            const optionItems = items.slice();
            const selectedItem = getVideoById(
                selectElement === elements.standardSelect ? state.standardVideos : state.learnerVideos,
                selectedId
            );

            if (selectedItem && !optionItems.some(function hasSelected(item) { return item.id === selectedItem.id; })) {
                optionItems.unshift(selectedItem);
            }

            if (!optionItems.length) {
                selectElement.innerHTML = '<option value="">' + api.escapeHtml(emptyLabel) + "</option>";
                selectElement.disabled = true;
                selectElement.value = "";
                return null;
            }

            selectElement.disabled = false;
            selectElement.innerHTML = optionItems.map(function renderOption(item) {
                return '<option value="' + item.id + '">#' + item.id + " " + api.escapeHtml(getVideoLabel(item)) + "</option>";
            }).join("");

            if (!selectedItem) {
                selectedId = optionItems[0].id;
            }

            if (selectedId) {
                selectElement.value = String(selectedId);
            }
            return selectedId;
        }

        function renderStandardPicker() {
            const filteredItems = state.standardVideos.filter(function filterItem(item) {
                return matchesVideoSearch(item, elements.standardSearchInput.value);
            });

            state.selectedStandardVideoId = syncVideoSelect(
                elements.standardSelect,
                filteredItems,
                state.selectedStandardVideoId,
                "当前暂无可选示范视频"
            );

            renderVideoSelectionSummary(
                elements.standardSelection,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                "当前还没有选中示范视频。"
            );

            renderVideoLibraryCards(
                elements.standardLibrary,
                filteredItems,
                state.selectedStandardVideoId,
                "当前筛选条件下没有匹配的示范视频。"
            );
        }

        function renderLearnerPicker() {
            const filteredItems = state.learnerVideos.filter(function filterItem(item) {
                return matchesVideoSearch(item, elements.learnerSearchInput.value);
            });

            state.learnerVideoId = syncVideoSelect(
                elements.learnerSelect,
                filteredItems,
                state.learnerVideoId,
                "当前暂无可选练习视频"
            );

            renderVideoSelectionSummary(
                elements.learnerSelection,
                getVideoById(state.learnerVideos, state.learnerVideoId),
                "当前还没有选中练习视频。你可以先从最近练习里选一条，或者直接上传新的练习视频。"
            );

            renderGroupedLearnerVideoCards(
                elements.learnerLibrary,
                filteredItems,
                state.learnerVideoId,
                "当前筛选条件下没有匹配的练习视频。"
            );
        }

        function renderVideoPickers() {
            renderStandardPicker();
            renderLearnerPicker();
            syncTaskShell();
        }

        function renderAnalysisWaitingState() {
            playback.reset();
            renderEmptyAnalysisState(elements, state, syncTaskShell);
            playback.renderStage();
            playback.renderControls();
            syncPreviewWorkspace();
        }

        async function loadVideoLibraries() {
            const [standardData, learnerData] = await Promise.all([
                api.fetchJson("/api/videos/standard"),
                api.fetchJson("/api/videos/learner"),
            ]);

            state.standardVideos = Array.isArray(standardData.items) ? standardData.items.slice() : [];
            state.learnerVideos = Array.isArray(learnerData.items) ? learnerData.items.slice() : [];

            if (!state.selectedStandardVideoId || !getVideoById(state.standardVideos, state.selectedStandardVideoId)) {
                state.selectedStandardVideoId = state.standardVideos.length ? state.standardVideos[0].id : null;
            }

            if (state.learnerVideoId && !getVideoById(state.learnerVideos, state.learnerVideoId)) {
                state.learnerVideoId = null;
            }

            renderVideoPickers();
        }

        async function loadSelectionPreviewToStage(playerKey, origin) {
            const isStandard = playerKey === "standard";
            const videoId = isStandard ? state.selectedStandardVideoId : state.learnerVideoId;
            const sampleFps = Number(elements.sampleFpsSelect.value) || 5;
            const missingStatusText = isStandard ? "缺少示范视频" : "缺少练习视频";
            const missingFeedbackText = isStandard
                ? "请先选择一条示范视频，再在本页检查素材。"
                : "请先从最近练习里选择一条视频，或上传新的练习视频，再在本页检查素材。";
            const loadingFeedbackText = origin === "deep-link"
                ? (isStandard ? "正在按回流入口自动载入示范素材。" : "正在按回流入口自动载入练习素材。")
                : (isStandard ? "正在在本页检查示范素材。" : "正在在本页检查练习素材。");
            const successStatusText = isStandard ? "已检查示范" : "已检查练习";
            const successFeedbackText = origin === "deep-link"
                ? (isStandard
                    ? "已按回流入口自动载入示范素材。现在可以直接在主舞台确认动作是否正确。"
                    : "已按回流入口自动载入练习素材。现在可以直接在主舞台确认动作走势。")
                : (isStandard
                    ? "示范素材已在本页载入。确认动作和骨骼稳定后，继续下一步即可。"
                    : "练习素材已在本页载入。确认上传内容和骨骼走势没问题后，继续开始比对即可。");

            if (!Number.isInteger(Number(videoId)) || Number(videoId) <= 0) {
                elements.inputStatus.textContent = missingStatusText;
                elements.inputFeedback.textContent = missingFeedbackText;
                return false;
            }

            elements.inputStatus.textContent = "检查中";
            elements.inputFeedback.textContent = loadingFeedbackText;

            try {
                await playback.loadSelectionPreview(playerKey, Number(videoId), sampleFps);
                elements.inputStatus.textContent = successStatusText;
                elements.inputFeedback.textContent = successFeedbackText;
                syncTaskShell();
                syncPreviewWorkspace();
                return true;
            } catch (error) {
                elements.inputStatus.textContent = "检查失败";
                elements.inputFeedback.textContent = (isStandard ? "示范素材检查失败：" : "练习素材检查失败：") + error.message;
                syncPreviewWorkspace();
                return false;
            }
        }

        async function runPendingPreviewTarget() {
            const previewTarget = pendingPreviewTarget;
            let loadedStandard = false;
            let loadedLearner = false;

            if (!previewTarget || state.analysis) {
                pendingPreviewTarget = "";
                if (previewTarget) {
                    syncPageUrl(["preview_target"]);
                }
                return false;
            }

            if (previewTarget === "standard" || previewTarget === "both") {
                loadedStandard = await loadSelectionPreviewToStage("standard", "deep-link");
            }

            if (previewTarget === "learner" || previewTarget === "both") {
                loadedLearner = await loadSelectionPreviewToStage("learner", "deep-link");
            }

            if (loadedStandard && loadedLearner) {
                state.shouldAutoCollapseSetupShell = true;
                elements.inputStatus.textContent = "已检查双素材";
                elements.inputFeedback.textContent = "已按回流入口自动载入示范和练习素材。现在可以直接在主舞台确认两侧画面，再继续本次练习。";
                syncTaskShell();
                syncPreviewWorkspace();
            }

            pendingPreviewTarget = "";
            syncPageUrl(["preview_target"]);
            return loadedStandard || loadedLearner;
        }

        function stopPlayback() {
            playback.stop();
        }

        function renderControls() {
            playback.renderControls();
        }

        function renderToggles() {
            const toggleKeys = Object.keys(state.toggles);
            const overlayEnabled = state.toggles.overlay !== false;
            const enabledCount = toggleKeys.filter(function countEnabled(key) {
                return key !== "overlay" && state.toggles[key];
            }).length;
            const overlayCount = toggleKeys.filter(function countOverlayEnabled(key) {
                return state.toggles[key];
            }).length;

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

            elements.toggleStatus.textContent = overlayEnabled
                ? "叠加已开 · " + enabledCount + " 项图层开启"
                : "叠加已关 · " + overlayCount + " 项预设保留";
        }

        function renderResult() {
            const analysis = state.analysis;
            if (!analysis) {
                return;
            }

            let feedbackText = "已加载本次练习结果。";
            let inputStatusText = "结果已创建";
            let workflowBadgeText = "正在回看";
            let workflowText = "当前已经进入结果查看阶段。建议先看总分和“先看这几项”，再回放确认问题片段。";

            if (analysis.status === "success") {
                feedbackText = "本次练习结果已生成，你现在可以直接看相似度、差异和建议。";
                inputStatusText = "已生成结果";
                workflowBadgeText = "查看结果";
                workflowText = "这次练习已经完成。建议先看整体匹配度和优先建议，再回放确认具体问题出在哪个动作片段。";
            } else if (analysis.status === "failed") {
                feedbackText = "这次比对失败了，请先检查摘要中的说明，再决定是否重新上传视频。";
                inputStatusText = "生成失败";
                workflowBadgeText = "需要重试";
                workflowText = "当前这次比对没有成功完成。建议先检查输入视频是否正确，再重新开始。";
            } else if (analysis.status === "running") {
                feedbackText = "系统正在生成本次练习结果，结果区会在完成后自动更新。";
                inputStatusText = "结果生成中";
                workflowBadgeText = "生成中";
                workflowText = "当前正在处理这次比对。你可以先停留在本页，等待结果和建议刷新出来。";
            }

            elements.pageRoot.classList.remove("analysis-layout--setup");
            elements.pageRoot.classList.add("analysis-layout--result");
            elements.workflowBadge.textContent = workflowBadgeText;
            elements.workflowText.textContent = workflowText;
            elements.feedbackStatus.textContent = feedbackText;
            elements.score.textContent = analysis.score === null ? "--" : api.formatNumber(analysis.score, 1);
            elements.resultStatus.textContent = formatStatusLabel(analysis.status);
            elements.sampleFps.textContent = analysis.sample_fps + " FPS";
            elements.summary.textContent = analysis.summary_text || "当前暂无摘要。";
            state.selectedStandardVideoId = analysis.standard_video_id;
            state.learnerVideoId = analysis.learner_video_id;
            elements.sampleFpsSelect.value = String(analysis.sample_fps);
            renderVideoPickers();
            if (elements.taskShell && !state.hasAutoCollapsedTask) {
                elements.taskShell.open = false;
                state.hasAutoCollapsedTask = true;
            }
            syncTaskShell();

            applyStatePill(
                elements.statePill,
                analysis.status === "success" ? "success" : analysis.status === "failed" ? "failed" : analysis.status === "running" ? "running" : "waiting",
                analysis.status === "success" ? "已完成" : analysis.status === "failed" ? "失败" : analysis.status === "running" ? "生成中" : "等待中"
            );

            const jointEntries = sortMetricEntries(Object.entries(analysis.joint_diffs || {}));
            const trajectoryEntries = sortMetricEntries(Object.entries(analysis.trajectory_diffs || {}));
            const suggestionEntries = analysis.suggestions || [];
            const structuredSuggestions = getStructuredSuggestions(analysis);
            if (!structuredSuggestions.some(function hasFocusedSuggestion(item) {
                return item.id === state.focusedSuggestionId;
            })) {
                state.focusedSuggestionId = "";
            }
            const priorityItems = buildPriorityItems(jointEntries, trajectoryEntries, suggestionEntries);
            const scoreTone = resolveScoreTone(analysis.status, analysis.score);
            applyStatePill(elements.scoreBand, scoreTone.tone, scoreTone.label);
            applyScoreTone(elements.scoreCard, scoreTone.tone);
            elements.scoreCaption.textContent = scoreTone.caption;
            updateInsightCounts(elements, jointEntries.length, trajectoryEntries.length, suggestionEntries.length);
            const tabCounts = {
                suggestions: suggestionEntries.length,
                joints: jointEntries.length,
                trajectory: trajectoryEntries.length,
            };
            const preferredResultTab = resolvePreferredResultTab(tabCounts);
            const activeResultTab = tabCounts[state.activeResultTab] > 0
                ? state.activeResultTab
                : preferredResultTab;
            syncResultTabs(elements, state, activeResultTab, true);

            renderList(elements.priorityList, priorityItems, function formatPriorityItem(item) {
                return "<li>" + api.escapeHtml(item) + "</li>";
            });

            renderList(elements.jointDiffs, jointEntries, function formatJointDiff(item) {
                return "<li>" + api.escapeHtml(humanizeMetricKey(item[0])) + "：偏差 " + api.formatNumber(item[1], 1) + "</li>";
            });
            renderList(elements.trajectoryDiffs, trajectoryEntries, function formatTrajectoryDiff(item) {
                return "<li>" + api.escapeHtml(humanizeMetricKey(item[0])) + "：差异 " + api.formatNumber(item[1], 2) + "</li>";
            });
            if (structuredSuggestions.length) {
                elements.suggestions.innerHTML = structuredSuggestions.map(function formatSuggestion(item) {
                    return [
                        '<li class="result-suggestion-item">',
                        '<button class="result-suggestion-button',
                        item.id === state.focusedSuggestionId ? " is-active" : "",
                        '" type="button" data-suggestion-id="', api.escapeHtml(item.id), '">',
                        '<span class="result-suggestion-button__text">', api.escapeHtml(item.text), "</span>",
                        '<span class="result-suggestion-button__meta">',
                        '<span class="result-suggestion-button__label">', api.escapeHtml(item.issue_label || "重点问题"), "</span>",
                        '<span class="result-suggestion-button__jump">', api.escapeHtml(item.segment_label || "定位片段"), "</span>",
                        "</span></button></li>",
                    ].join("");
                }).join("");
            } else {
                renderList(elements.suggestions, suggestionEntries, function formatSuggestion(item) {
                    return "<li>" + api.escapeHtml(item) + "</li>";
                });
            }

            elements.standardBadge.textContent = "示范 #" + analysis.standard_video_id;
            elements.learnerBadge.textContent = "练习 #" + analysis.learner_video_id;
            elements.inputStatus.textContent = inputStatusText;
            updateAnalysisUrl(analysis.id, analysis.standard_video_id, analysis.learner_video_id);
            playback.renderStage();
            playback.renderControls();
            syncPreviewWorkspace();
        }

        async function loadAnalysis(analysisId) {
            if (!analysisId) {
                state.analysis = null;
                renderAnalysisWaitingState();
                return null;
            }

            elements.feedbackStatus.textContent = "正在加载本次练习详情。";
            const data = await api.fetchJson("/api/analysis/" + analysisId);
            state.analysis = data.analysis;
            state.viewMode = "skeleton";
            state.toggles.keypoints = true;
            state.toggles.trajectory = false;
            state.toggles.angles = false;
            state.focusedSuggestionId = "";
            state.focusIssue = null;
            state.previewLoading = true;
            renderResult();
            await playback.loadPreviews();
            syncPreviewWorkspace();
            return state.analysis;
        }

        function startPlayback() {
            playback.start();
        }

        function focusSuggestion(suggestion) {
            if (!suggestion) {
                return;
            }

            state.focusedSuggestionId = String(suggestion.id || "");
            state.focusIssue = suggestion;
            state.viewMode = "overlay";
            state.toggles.overlay = true;
            state.toggles.keypoints = true;
            if (suggestion.issue_type === "joint") {
                state.toggles.angles = true;
            }
            if (suggestion.issue_type === "trajectory") {
                state.toggles.trajectory = true;
            }
            renderToggles();
            syncSuggestionSelection(elements, state.focusedSuggestionId);

            const focused = playback.focusIssue(suggestion);
            if (focused) {
                elements.feedbackStatus.textContent = "已定位 " + (suggestion.issue_label || "重点问题") + " 对应片段，可直接回看。";
                return;
            }

            elements.feedbackStatus.textContent = "回放数据仍在装配，稍后可点击建议直接定位对应片段。";
        }

        elements.playButton.addEventListener("click", startPlayback);
        elements.pauseButton.addEventListener("click", function pausePlayback() {
            stopPlayback();
            renderControls();
        });
        elements.speedButton.addEventListener("click", function cycleSpeed() {
            state.speedIndex = (state.speedIndex + 1) % SPEED_OPTIONS.length;
            playback.syncSpeed();
        });
        elements.muteButton.addEventListener("click", function toggleMute() {
            state.isMuted = !state.isMuted;
            playback.syncMute();
        });
        elements.stepBackButton.addEventListener("click", function stepBack() {
            playback.stepBy(-1);
        });
        elements.stepForwardButton.addEventListener("click", function stepForward() {
            playback.stepBy(1);
        });
        elements.progress.addEventListener("input", function syncProgress() {
            playback.setProgress(Number(elements.progress.value));
        });
        if (elements.suggestions) {
            elements.suggestions.addEventListener("click", function handleSuggestionClick(event) {
                const button = event.target.closest(".result-suggestion-button");
                if (!button || !state.analysis) {
                    return;
                }
                const structuredSuggestions = getStructuredSuggestions(state.analysis);
                const suggestion = structuredSuggestions.find(function findSuggestion(item) {
                    return item.id === button.getAttribute("data-suggestion-id");
                });
                focusSuggestion(suggestion || null);
            });
        }

        if (elements.resultTabButtons.length) {
            elements.resultTabButtons.forEach(function bindResultTab(button) {
                button.addEventListener("click", function handleResultTabClick() {
                    state.activeResultTab = button.getAttribute("data-result-tab") || "suggestions";
                    syncResultTabs(elements, state, state.activeResultTab, true);
                });
            });
        }

        if (elements.taskShell) {
            elements.taskShell.addEventListener("toggle", function handleTaskShellToggle() {
                syncTaskShell();
            });
        }

        if (elements.taskQuickStartButton) {
            elements.taskQuickStartButton.addEventListener("click", function handleTaskQuickStart(event) {
                event.preventDefault();
                event.stopPropagation();
                requestCreateAnalysisFromDrawer();
            });
        }

        Object.keys(elements.toggleInputs).forEach(function bindToggle(key) {
            const input = elements.toggleInputs[key];
            input.addEventListener("change", function handleToggleChange() {
                state.toggles[key] = input.checked;
                renderToggles();
                playback.renderStage();
            });
        });

            elements.standardSearchInput.addEventListener("input", function handleStandardSearch() {
            renderStandardPicker();
        });
        elements.sampleFpsSelect.addEventListener("change", function handleSampleFpsChange() {
            syncTaskShell();
            syncPreviewWorkspace();
        });
        elements.standardSelect.addEventListener("change", function handleStandardSelect() {
            const nextVideoId = Number(elements.standardSelect.value);
            if (!Number.isInteger(nextVideoId) || nextVideoId <= 0) {
                return;
            }
            state.selectedStandardVideoId = nextVideoId;
            if (!state.analysis) {
                playback.clearPlayerPreview("standard");
            }
            renderStandardPicker();
            syncTaskShell();
            syncPageUrl();
        });
        elements.standardLibrary.addEventListener("click", function handleStandardLibraryClick(event) {
            const button = event.target.closest("[data-video-id]");
            if (!button) {
                return;
            }
            state.selectedStandardVideoId = Number(button.getAttribute("data-video-id"));
            if (!state.analysis) {
                playback.clearPlayerPreview("standard");
            }
            renderStandardPicker();
            syncTaskShell();
            syncPageUrl();
        });

        elements.openStandardPreviewButton.addEventListener("click", async function openStandardPreview() {
            await loadSelectionPreviewToStage("standard", "manual");
        });

        elements.learnerSearchInput.addEventListener("input", function handleLearnerSearch() {
            renderLearnerPicker();
        });
        elements.learnerSelect.addEventListener("change", function handleLearnerSelect() {
            const nextVideoId = Number(elements.learnerSelect.value);
            if (!Number.isInteger(nextVideoId) || nextVideoId <= 0) {
                return;
            }
            state.learnerVideoId = nextVideoId;
            if (!state.analysis) {
                playback.clearPlayerPreview("learner");
            }
            renderLearnerPicker();
            syncTaskShell();
            syncPageUrl();
        });
        elements.learnerLibrary.addEventListener("click", function handleLearnerLibraryClick(event) {
            const button = event.target.closest("[data-video-id]");
            if (!button) {
                return;
            }
            state.learnerVideoId = Number(button.getAttribute("data-video-id"));
            if (!state.analysis) {
                playback.clearPlayerPreview("learner");
            }
            renderLearnerPicker();
            syncTaskShell();
            syncPageUrl();
        });

        elements.openLearnerPreviewButton.addEventListener("click", async function openLearnerPreview() {
            await loadSelectionPreviewToStage("learner", "manual");
        });

        elements.uploadLearnerButton.addEventListener("click", async function uploadLearnerVideo() {
            const file = elements.learnerFileInput.files && elements.learnerFileInput.files[0];
            if (!file) {
                elements.inputStatus.textContent = "缺少文件";
                elements.inputFeedback.textContent = "请先选择一段练习视频。";
                return;
            }

            elements.inputStatus.textContent = "上传中";
            elements.inputFeedback.textContent = "正在上传练习视频并保存本次练习素材。";

            const formData = new FormData();
            formData.append("file", file);

            try {
                const data = await api.postForm("/api/videos/learner", formData);
                elements.inputStatus.textContent = "上传成功";
                elements.inputFeedback.textContent = "练习视频已上传并自动选中。确认没选错后，现在可以直接开始本次比对。";
                state.learnerVideos = upsertVideo(state.learnerVideos, data.video);
                state.learnerVideoId = data.video.id;
                if (!state.analysis) {
                    playback.clearPlayerPreview("learner");
                }
                elements.learnerBadge.textContent = "练习 #" + data.video.id;
                elements.learnerSearchInput.value = "";
                elements.learnerFileInput.value = "";
                renderLearnerPicker();
                syncTaskShell();
                syncPageUrl();
            } catch (error) {
                elements.inputStatus.textContent = "上传失败";
                elements.inputFeedback.textContent = "练习视频上传失败：" + error.message;
            }
        });

        elements.createForm.addEventListener("submit", async function createAnalysisTask(event) {
            event.preventDefault();

            const standardVideoId = state.selectedStandardVideoId;
            const sampleFps = Number(elements.sampleFpsSelect.value);
            const learnerVideoId = state.learnerVideoId;
            const previewState = getSetupPreviewState();

            if (!Number.isInteger(standardVideoId) || standardVideoId <= 0) {
                elements.inputStatus.textContent = "示范视频无效";
                elements.inputFeedback.textContent = "请先从示范视频库里选择一条有效的视频。";
                return;
            }

            if (!learnerVideoId) {
                elements.inputStatus.textContent = "缺少练习视频";
                elements.inputFeedback.textContent = "请先从最近练习里选择一条视频，或先上传新的练习视频。";
                return;
            }

            if (!previewState.readyToGenerate) {
                elements.inputStatus.textContent = "主舞台待确认";
                elements.inputFeedback.textContent = "开始前请先把示范和练习都载入主舞台检查完成，再创建结果。";
                return;
            }

            elements.inputStatus.textContent = "生成中";
            elements.inputFeedback.textContent = "正在生成本次练习结果。";
            state.hasAutoCollapsedTask = false;
            state.isCreatingAnalysis = true;
            syncTaskShell();

            try {
                const data = await api.postJson("/api/analysis", {
                    standard_video_id: standardVideoId,
                    learner_video_id: learnerVideoId,
                    sample_fps: sampleFps,
                });
                elements.inputStatus.textContent = "结果已创建";
                elements.inputFeedback.textContent = "本次练习结果 #" + data.analysis.id + " 已创建，正在进入结果复盘页。";
                window.location.assign("/review?analysis_id=" + encodeURIComponent(data.analysis.id));
                return;
            } catch (error) {
                elements.inputStatus.textContent = "创建失败";
                elements.inputFeedback.textContent = "本次比对生成失败：" + error.message;
            } finally {
                state.isCreatingAnalysis = false;
                syncTaskShell();
            }
        });

        renderControls();
        renderToggles();
        playback.renderStage();

        loadVideoLibraries()
            .then(function handleVideoLibrariesLoaded() {
                renderAnalysisWaitingState();

                if (state.learnerVideoId) {
                    elements.inputFeedback.textContent = "当前已读取一条练习视频。确认示范和采样速度后，可以直接开始本次比对。";
                } else {
                    elements.inputFeedback.textContent = "先选示范视频，再从最近练习里挑一条，或者上传新的练习视频。";
                }
                syncPageUrl();
                syncTaskShell();

                return null;
            })
            .then(async function syncInitialAnalysis(analysis) {
                if (!analysis) {
                    await runPendingPreviewTarget();
                    return;
                }
                playback.renderControls();
                renderToggles();
            })
            .catch(function handleError(error) {
                elements.inputStatus.textContent = "加载失败";
                elements.inputFeedback.textContent = "视频库加载失败：" + error.message;
                playback.renderControls();
            });
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
