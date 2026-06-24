(function bootstrapReviewPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const analysisPlayer = window.MotionAnalysisPlayer;
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

    function getVideoLabel(item) {
        if (!item) {
            return "视频";
        }
        return item.display_name || item.original_filename || ("视频 #" + item.id);
    }

    function getAnalysisVideoLabel(analysis, video, key) {
        if (video) {
            return getVideoLabel(video);
        }
        if (analysis && analysis[key]) {
            return String(analysis[key]);
        }
        return key === "standard_video_label" ? "参考视频" : "当前视频";
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

    function formatCompactDateTime(value) {
        const normalized = String(value || "").replace(" ", "T");
        const parsed = Date.parse(normalized);
        if (Number.isNaN(parsed)) {
            return "--";
        }

        const date = new Date(parsed);
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        const hours = String(date.getHours()).padStart(2, "0");
        const minutes = String(date.getMinutes()).padStart(2, "0");
        return month + "-" + day + " " + hours + ":" + minutes;
    }

    function getStructuredSuggestions(analysis) {
        const items = analysis && Array.isArray(analysis.structured_suggestions)
            ? analysis.structured_suggestions
            : [];
        return items.filter(function filterSuggestion(item) {
            return item && item.id && item.text;
        });
    }

    function buildMediaMetaParts(video) {
        return {
            primary: [
                video && video.duration_sec ? api.formatNumber(video.duration_sec, 1) + "s" : "",
                video && video.frame_rate ? api.formatNumber(video.frame_rate, 1) + " FPS" : "",
            ].filter(Boolean),
            secondary: [
                video && video.width && video.height ? String(video.width) + " x " + String(video.height) : "",
                video && video.created_at ? formatCompactDateTime(video.created_at) : "",
            ].filter(Boolean),
        };
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
        const metaParts = buildMediaMetaParts(video);
        return [
            '<div class="selector-summary selector-summary--media review-material-card">',
            renderVideoCover(video, "封面待生成", "video-cover--summary"),
            '<div class="selector-summary__content">',
            '<span class="review-chip"><strong>', api.escapeHtml(eyebrow), "</strong></span>",
            '<div class="selector-summary__head"><strong>', api.escapeHtml(label), "</strong>",
            '<span class="badge">#', api.escapeHtml(videoId || "--"), "</span></div>",
            '<div class="selector-summary__meta">',
            metaParts.primary.map(function renderPrimaryPart(part) {
                return "<span>" + api.escapeHtml(part) + "</span>";
            }).join(""),
            "</div>",
            metaParts.secondary.length
                ? '<div class="review-material-card__submeta">' + metaParts.secondary.map(function renderSecondaryPart(part) {
                    return "<span>" + api.escapeHtml(part) + "</span>";
                }).join("") + "</div>"
                : "",
            "</div></div>",
        ].join("");
    }

    function buildPriorityItems(jointEntries, trajectoryEntries, suggestionEntries) {
        const items = [];

        if (suggestionEntries.length) {
            items.push(String(suggestionEntries[0]));
        }

        if (jointEntries.length) {
            const largestJoint = jointEntries.slice().sort(function sortJoint(left, right) {
                return Number(right[1]) - Number(left[1]);
            })[0];
            items.push("优先关注 " + humanizeMetricKey(largestJoint[0]) + "，偏差 " + api.formatNumber(largestJoint[1], 1));
        }

        if (trajectoryEntries.length) {
            const largestTrajectory = trajectoryEntries.slice().sort(function sortTrajectory(left, right) {
                return Number(right[1]) - Number(left[1]);
            })[0];
            items.push("轨迹差异较大：" + humanizeMetricKey(largestTrajectory[0]) + "，差异 " + api.formatNumber(largestTrajectory[1], 2));
        }

        suggestionEntries.slice(1).forEach(function addSuggestion(item) {
            if (items.length >= 3) {
                return;
            }
            items.push(String(item));
        });

        return items.slice(0, 3);
    }

    function formatIssueTypeLabel(issueType) {
        if (issueType === "trajectory") {
            return "轨迹差异";
        }
        if (issueType === "joint") {
            return "关节问题";
        }
        return "重点问题";
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
            activeFlowStep: "overview",
            toggles: {
                keypoints: true,
                trajectory: false,
                angles: false,
            },
            viewMode: "skeleton",
            focusIssue: null,
            focusedSuggestionId: "",
            isSaving: false,
        };
    }

    function getRecordStateLabel(analysis) {
        return analysis && analysis.is_saved ? "已保存" : "未保存";
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
                caption: "结果仍在生成中，可以先回看素材，稍后再刷新结果。",
            };
        }

        if (status !== "success" || score === null || score === undefined) {
            return {
                tone: "waiting",
                label: "待出结果",
                caption: "结果加载后，这里会显示本次练习的整体匹配度。",
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

    function buildChip(label, value) {
        return '<span class="review-chip"><strong>' + api.escapeHtml(label) + "</strong>" + api.escapeHtml(value) + "</span>";
    }

    function resolvePrimaryActionLabel(structuredSuggestions, jointEntries, trajectoryEntries) {
        if (structuredSuggestions.length) {
            return structuredSuggestions[0].issue_label || "先看第一条建议";
        }
        if (jointEntries.length) {
            return "先看 " + humanizeMetricKey(jointEntries[0][0]);
        }
        if (trajectoryEntries.length) {
            return "先看 " + humanizeMetricKey(trajectoryEntries[0][0]) + "轨迹";
        }
        return "先看主舞台";
    }

    function resolveReviewRouteLabel(analysis, scoreTone) {
        if (!analysis) {
            return "等待结果";
        }
        if (analysis.status === "failed") {
            return "回分析页重试";
        }
        if (analysis.status === "running") {
            return "先看主舞台";
        }
        if (analysis.score !== null && analysis.score !== undefined && Number(analysis.score) < 90) {
            return "建议继续分析";
        }
        return scoreTone.label;
    }

    function resolveFocusStageContent(analysis, jointEntries, trajectoryEntries, structuredSuggestions, focusedSuggestion) {
        if (!analysis) {
            return {
                status: "等待结果",
                copy: "结果加载后，这里会先给出当前最值得看的建议。",
                title: "还没有定位到本次重点",
                summary: "结果加载后，这里会先提示最值得看的部位或节奏。",
                chips: [buildChip("当前状态", "等待结果")],
            };
        }

        if (analysis.status === "failed") {
            return {
                status: "生成失败",
                copy: "这次比对没有成功完成，报告页先只保留失败说明与回流动作。",
                title: "先确认这次结果是否有效",
                summary: "当前更适合回分析页检查素材，或重新生成一次结果，而不是继续解释细项差异。",
                chips: [
                    buildChip("结果状态", formatStatusLabel(analysis.status)),
                    buildChip("当前建议", "先回分析页重试"),
                ],
            };
        }

        if (analysis.status === "running") {
            return {
                status: "生成中",
                copy: "建议还在生成，当前可以先回主舞台看动作和节奏。",
                title: "先回看素材，等待完整建议生成",
                summary: "双视频已经可以先看，但更具体的建议和差异仍在生成中。",
                chips: [
                    buildChip("结果状态", formatStatusLabel(analysis.status)),
                    buildChip("当前可做", "先看主舞台"),
                ],
            };
        }

        if (focusedSuggestion) {
            return {
                status: "已定位",
                copy: "当前重点已经置顶，可以直接回主舞台确认对应片段。",
                title: focusedSuggestion.issue_label || "已定位本次重点",
                summary: focusedSuggestion.text,
                chips: [
                    buildChip("问题类型", formatIssueTypeLabel(focusedSuggestion.issue_type)),
                    buildChip("定位片段", focusedSuggestion.segment_label || "建议片段"),
                    buildChip("下一步", "带着这条建议回看"),
                ],
            };
        }

        if (structuredSuggestions.length) {
            const suggestion = structuredSuggestions[0];
            return {
                status: structuredSuggestions.length + " 条建议",
                copy: "先点第一条建议定位，再决定是否继续展开关节和轨迹细项。",
                title: "先从第一条建议开始看",
                summary: suggestion.text,
                chips: [
                    buildChip("首要问题", suggestion.issue_label || "重点问题"),
                    buildChip("定位片段", suggestion.segment_label || "点击定位"),
                    buildChip("建议数量", structuredSuggestions.length + " 条"),
                ],
            };
        }

        if (jointEntries.length) {
            return {
                status: "先看关节",
                copy: "当前还没有结构化建议时，先从偏差最大的关节开始看。",
                title: "先看 " + humanizeMetricKey(jointEntries[0][0]),
                summary: "这次最明显的关节偏差是 " + humanizeMetricKey(jointEntries[0][0]) + "，偏差 " + api.formatNumber(jointEntries[0][1], 1) + "。",
                chips: [
                    buildChip("优先维度", "关节偏差"),
                    buildChip("最大偏差", api.formatNumber(jointEntries[0][1], 1)),
                ],
            };
        }

        if (trajectoryEntries.length) {
            return {
                status: "先看轨迹",
                copy: "当前还没有结构化建议时，先从差异最大的轨迹开始看。",
                title: "先看 " + humanizeMetricKey(trajectoryEntries[0][0]) + "轨迹",
                summary: "这次最明显的轨迹差异在 " + humanizeMetricKey(trajectoryEntries[0][0]) + "，差异 " + api.formatNumber(trajectoryEntries[0][1], 2) + "。",
                chips: [
                    buildChip("优先维度", "轨迹差异"),
                    buildChip("最大差异", api.formatNumber(trajectoryEntries[0][1], 2)),
                ],
            };
        }

            return {
                status: "结果已就绪",
                copy: "当前没有更多细项，直接看主舞台再决定是否继续分析。",
                title: "当前没有明显重点需要单独展开",
                summary: analysis.summary_text || "本次结果已经生成，但暂时没有更多可细看的重点差异。",
            chips: [
                buildChip("结果状态", formatStatusLabel(analysis.status)),
                buildChip("当前建议", "看完主舞台再决定"),
            ],
        };
    }

    function buildActionCopy(analysis, scoreTone) {
        if (!analysis) {
            return "还没打开结果时，先回结果记录选一条报告。";
        }
        if (analysis.status === "failed") {
            return "这次结果没有成功完成，更适合回分析页检查当前视频，或重新导入一组素材再开始。";
        }
        if (analysis.status === "running") {
            return "这次结果仍在生成中。你可以先回看双视频，稍后刷新再看完整结论。";
        }
        if (!analysis.is_saved) {
            return "当前结果还没有保存。保存前它只算临时分析，关闭浏览器或中断进程后会被清空。";
        }
        if (analysis.score !== null && analysis.score !== undefined && Number(analysis.score) < 90) {
            return "这次结果更适合带着同一组视频继续分析，先对着建议回看关键片段，再决定是否重新开始。";
        }
        return scoreTone.caption;
    }

        function initializePage() {
        const elements = {
            flowButtons: Array.from(document.querySelectorAll("[data-review-anchor]")),
            sessionTitle: document.getElementById("review-session-title"),
            sessionCopy: document.getElementById("review-session-copy"),
            sessionBadge: document.getElementById("review-session-badge"),
            contextChips: document.getElementById("review-context-chips"),
            materialGrid: document.getElementById("review-material-grid"),
            actionStatus: document.getElementById("review-action-status"),
            actionCopy: document.getElementById("review-action-copy"),
            actionMeta: document.getElementById("review-action-meta"),
            saveResultButton: document.getElementById("review-save-result"),
            replayLink: document.getElementById("review-replay-link"),
            practiceLink: document.getElementById("review-practice-link"),
            openHistoryLink: document.getElementById("review-open-history"),
            focusStatus: document.getElementById("review-focus-status"),
            focusCopy: document.getElementById("review-focus-copy"),
            focusTitle: document.getElementById("review-focus-title"),
            focusSummary: document.getElementById("review-focus-summary"),
            focusMeta: document.getElementById("review-focus-meta"),
            previewStatus: document.getElementById("review-preview-status"),
            previewStandardStatus: document.getElementById("review-preview-standard-status"),
            previewLearnerStatus: document.getElementById("review-preview-learner-status"),
            previewMode: document.getElementById("review-preview-mode"),
            playbackStatus: document.getElementById("review-playback-status"),
            stepLabel: document.getElementById("review-step-label"),
            speedLabel: document.getElementById("review-speed-label"),
            progress: document.getElementById("review-progress"),
            playButton: document.getElementById("review-play"),
            pauseButton: document.getElementById("review-pause"),
            speedButton: document.getElementById("review-speed"),
            muteButton: document.getElementById("review-mute"),
            stepBackButton: document.getElementById("review-step-back"),
            stepForwardButton: document.getElementById("review-step-forward"),
            toggleStatus: document.getElementById("review-toggle-status"),
            viewModeButtons: Array.from(document.querySelectorAll("[data-stage-view-mode]")),
            toggleInputs: {
                keypoints: document.getElementById("review-toggle-keypoints"),
                trajectory: document.getElementById("review-toggle-trajectory"),
                angles: document.getElementById("review-toggle-angles"),
            },
            toggleCards: {
                keypoints: document.getElementById("review-toggle-keypoints-card"),
                trajectory: document.getElementById("review-toggle-trajectory-card"),
                angles: document.getElementById("review-toggle-angles-card"),
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
                    mediaStage: document.querySelector("#review-standard-stage .analysis-video-stage"),
                    video: document.getElementById("review-standard-player"),
                    overlay: document.getElementById("review-standard-overlay"),
                    empty: document.getElementById("review-standard-empty"),
                    title: document.getElementById("review-standard-title"),
                    caption: document.getElementById("review-standard-caption"),
                    frameMeta: document.getElementById("review-standard-frame-meta"),
                    timeMeta: document.getElementById("review-standard-time-meta"),
                    chips: document.getElementById("review-standard-overlays"),
                    readout: document.getElementById("review-standard-angle-readout"),
                },
                learner: {
                    mediaStage: document.querySelector("#review-learner-stage .analysis-video-stage"),
                    video: document.getElementById("review-learner-player"),
                    overlay: document.getElementById("review-learner-overlay"),
                    empty: document.getElementById("review-learner-empty"),
                    title: document.getElementById("review-learner-title"),
                    caption: document.getElementById("review-learner-caption"),
                    frameMeta: document.getElementById("review-learner-frame-meta"),
                    timeMeta: document.getElementById("review-learner-time-meta"),
                    chips: document.getElementById("review-learner-overlays"),
                    readout: document.getElementById("review-learner-angle-readout"),
                },
            },
        };

        if (!api || !analysisPlayer || !elements.progress) {
            return;
        }

        const state = createState();
        const analysisId = getQueryAnalysisId();

        function getVideoById(items, videoId) {
            return items.find(function findItem(item) {
                return item.id === videoId;
            }) || null;
        }

        function getAnalysisVideoItem(playerKey) {
            if (!state.analysis) {
                return null;
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
                renderWorkspaceStatus();
            },
        });

        function syncFlowState() {
            const readiness = {
                overview: Boolean(state.analysis),
                stage: playback.hasPreviewData(),
                controls: playback.hasPreviewData(),
                focus: Boolean(state.analysis && state.analysis.status === "success"),
                next: Boolean(state.analysis),
            };

            elements.flowButtons.forEach(function syncFlowButton(button) {
                const anchorKey = button.getAttribute("data-review-anchor");
                button.classList.toggle("is-active", anchorKey === state.activeFlowStep);
                button.classList.toggle("is-done", readiness[anchorKey] && anchorKey !== state.activeFlowStep);
                button.classList.toggle("is-disabled", !readiness[anchorKey] && anchorKey !== "overview");
            });
        }

        function setActiveFlowStep(anchorKey) {
            state.activeFlowStep = String(anchorKey || "overview");
            syncFlowState();
        }

        function renderWorkspaceStatus() {
            if (!state.analysis) {
                elements.previewStatus.textContent = "等待结果";
                elements.previewStandardStatus.textContent = "示范待装配";
                elements.previewLearnerStatus.textContent = "练习待装配";
                elements.previewMode.textContent = "当前以单次结果复盘为主";
                syncFlowState();
                return;
            }

            const standardReady = Boolean(state.previews.standard);
            const learnerReady = Boolean(state.previews.learner);
            const hasError = Boolean(state.previewErrors.standard || state.previewErrors.learner);

            if (state.previewLoading) {
                elements.previewStatus.textContent = "正在装配回看";
                elements.previewMode.textContent = "正在读取双视频回放与叠加层";
            } else if (hasError) {
                elements.previewStatus.textContent = "回看部分失败";
                elements.previewMode.textContent = "可先看已成功装配的一侧，再决定是否回练习台重练";
            } else if (playback.hasPreviewData()) {
                elements.previewStatus.textContent = state.analysis.status === "success" ? "结果回看就绪" : "素材回看就绪";
                elements.previewMode.textContent = "当前以单次结果复盘为主";
            } else {
                elements.previewStatus.textContent = "等待装配";
                elements.previewMode.textContent = "正在准备本次回放";
            }

            elements.previewStandardStatus.textContent = state.previewErrors.standard
                ? "示范装配失败"
                : (standardReady ? "示范可回看" : "示范待装配");
            elements.previewLearnerStatus.textContent = state.previewErrors.learner
                ? "练习装配失败"
                : (learnerReady ? "练习可回看" : "练习待装配");
            syncFlowState();
        }

        function renderSaveAction() {
            if (!elements.saveResultButton) {
                return;
            }

            if (!state.analysis) {
                elements.saveResultButton.disabled = true;
                elements.saveResultButton.textContent = "结果生成后可保存";
                return;
            }

            if (state.isSaving) {
                elements.saveResultButton.disabled = true;
                elements.saveResultButton.textContent = "正在保存结果...";
                return;
            }

            if (state.analysis.is_saved) {
                elements.saveResultButton.disabled = true;
                elements.saveResultButton.textContent = "结果已保存";
                return;
            }

            const canSave = state.analysis.status === "success";
            elements.saveResultButton.disabled = !canSave;
            elements.saveResultButton.textContent = canSave ? "保存结果" : "结果生成后可保存";
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

        function renderFocusStage() {
            const analysis = state.analysis;
            const jointEntries = analysis ? sortMetricEntries(Object.entries(analysis.joint_diffs || {})) : [];
            const trajectoryEntries = analysis ? sortMetricEntries(Object.entries(analysis.trajectory_diffs || {})) : [];
            const structuredSuggestions = analysis ? getStructuredSuggestions(analysis) : [];
            const focusedSuggestion = state.focusIssue || null;
            const focusContent = resolveFocusStageContent(
                analysis,
                jointEntries,
                trajectoryEntries,
                structuredSuggestions,
                focusedSuggestion
            );

            elements.focusStatus.textContent = focusContent.status;
            elements.focusCopy.textContent = focusContent.copy;
            elements.focusTitle.textContent = focusContent.title;
            elements.focusSummary.textContent = focusContent.summary;
            elements.focusMeta.innerHTML = focusContent.chips.join("");
        }

        function renderEmptyState(message) {
            state.analysis = null;
            state.focusedSuggestionId = "";
            state.focusIssue = null;
            state.activeResultTab = "suggestions";
            state.viewMode = "skeleton";
            state.toggles.keypoints = true;
            state.toggles.trajectory = false;
            state.toggles.angles = false;
            renderToggles();

            elements.sessionTitle.textContent = "还没有打开某次结果";
            elements.sessionCopy.textContent = message || "先从结果记录里选一条报告，再进入本页查看结论和建议。";
            elements.sessionBadge.textContent = "等待结果";
            elements.contextChips.innerHTML = '<span class="review-chip">从结果记录进入</span>';
            elements.materialGrid.innerHTML = '<div class="empty-panel"><p>当前还没有可展示的素材摘要。</p></div>';
            elements.actionStatus.textContent = "等待结果";
            elements.actionCopy.textContent = "先回到结果记录挑一条报告，再决定是否继续分析当前素材。";
            elements.actionMeta.innerHTML = '<span class="review-chip">等待时间信息</span>';
            elements.openHistoryLink.href = "/history";
            elements.replayLink.href = "/analysis";
            elements.practiceLink.href = "/session";
            renderSaveAction();
            elements.feedbackStatus.textContent = "选中一条结果记录后，这里会显示状态、差异和建议。";
            elements.score.textContent = "--";
            elements.resultStatus.textContent = formatStatusLabel("waiting");
            elements.sampleFps.textContent = "--";
            elements.summary.textContent = "结果加载后，这里会显示本次结论。";
            elements.scoreCaption.textContent = "等待你打开某次结果。";
            elements.priorityList.innerHTML = "<li>结果加载后，这里会先整理最值得看的 3 项。</li>";
            elements.jointDiffs.innerHTML = "<li>等待数据</li>";
            elements.trajectoryDiffs.innerHTML = "<li>等待数据</li>";
            elements.suggestions.innerHTML = "<li>等待数据</li>";
            applyStatePill(elements.statePill, "waiting", "等待中");
            applyStatePill(elements.scoreBand, "waiting", "待出结果");
            applyScoreTone(elements.scoreCard, "waiting");
            updateInsightCounts(elements, 0, 0, 0);
            syncResultTabs(elements, state, "suggestions", true);
            playback.reset();
            playback.renderStage();
            playback.renderControls();
            renderFocusStage();
            setActiveFlowStep("overview");
            renderWorkspaceStatus();
        }

        function renderReviewShell() {
            const analysis = state.analysis;
            if (!analysis) {
                renderWorkspaceStatus();
                return;
            }

            const standardVideo = getAnalysisVideoItem("standard");
            const learnerVideo = getAnalysisVideoItem("learner");
            const scoreTone = resolveScoreTone(analysis.status, analysis.score);
            const jointEntries = sortMetricEntries(Object.entries(analysis.joint_diffs || {}));
            const trajectoryEntries = sortMetricEntries(Object.entries(analysis.trajectory_diffs || {}));
            const structuredSuggestions = getStructuredSuggestions(analysis);
            const scoreText = analysis.score === null || analysis.score === undefined
                ? "暂无分数"
                : api.formatNumber(analysis.score, 1) + " 分";
            const replayLink = "/analysis?standard_video_id="
                + encodeURIComponent(analysis.standard_video_id)
                + "&learner_video_id="
                + encodeURIComponent(analysis.learner_video_id)
                + "&preview_target=both";
            const practiceLink = "/session";

            elements.sessionTitle.textContent = "分析结果报告 #" + analysis.id;
            elements.sessionCopy.textContent = analysis.summary_text || scoreTone.caption;
            elements.sessionBadge.textContent = formatStatusLabel(analysis.status);
            elements.contextChips.innerHTML = [
                buildChip("整体匹配度", scoreText),
                buildChip("采样", String(analysis.sample_fps) + " FPS"),
                buildChip("记录状态", getRecordStateLabel(analysis)),
                buildChip("参考", getAnalysisVideoLabel(analysis, standardVideo, "standard_video_label")),
                buildChip("评分视频", getAnalysisVideoLabel(analysis, learnerVideo, "learner_video_label")),
            ].join("");
            elements.materialGrid.innerHTML = [
                buildMaterialCard(
                    standardVideo && Object.keys(standardVideo).length
                        ? standardVideo
                        : {display_name: getAnalysisVideoLabel(analysis, null, "standard_video_label")},
                    "参考视频",
                    "参考视频",
                    analysis.standard_video_id
                ),
                buildMaterialCard(
                    learnerVideo && Object.keys(learnerVideo).length
                        ? learnerVideo
                        : {display_name: getAnalysisVideoLabel(analysis, null, "learner_video_label")},
                    "当前视频",
                    "当前视频",
                    analysis.learner_video_id
                ),
            ].join("");
            elements.actionStatus.textContent = scoreTone.label;
            elements.actionCopy.textContent = buildActionCopy(analysis, scoreTone);
            elements.actionMeta.innerHTML = [
                buildChip("结果记录", "#" + String(analysis.id)),
                buildChip("整体匹配度", scoreText),
                buildChip("记录状态", getRecordStateLabel(analysis)),
                buildChip("首要问题", resolvePrimaryActionLabel(structuredSuggestions, jointEntries, trajectoryEntries)),
                buildChip("当前判断", resolveReviewRouteLabel(analysis, scoreTone)),
                buildChip("最后更新", analysis.updated_at || "--"),
            ].join("");
            elements.replayLink.href = replayLink;
            elements.practiceLink.href = practiceLink;
            elements.openHistoryLink.href = analysis.is_saved
                ? "/history?history_id=" + encodeURIComponent(analysis.id)
                : "/history";
            renderSaveAction();
            renderWorkspaceStatus();
        }

        function renderResult() {
            const analysis = state.analysis;
            if (!analysis) {
                return;
            }

            const jointEntries = sortMetricEntries(Object.entries(analysis.joint_diffs || {}));
            const trajectoryEntries = sortMetricEntries(Object.entries(analysis.trajectory_diffs || {}));
            const suggestionEntries = Array.isArray(analysis.suggestions) ? analysis.suggestions : [];
            const structuredSuggestions = getStructuredSuggestions(analysis);
            const priorityItems = buildPriorityItems(jointEntries, trajectoryEntries, suggestionEntries);
            const scoreTone = resolveScoreTone(analysis.status, analysis.score);
            const feedbackText = analysis.status === "success"
                ? "本次分析报告已生成，你现在可以直接看相似度、差异和建议。"
                : analysis.status === "failed"
                    ? "这次比对失败了，请先检查结果说明，再决定是否回分析页继续处理。"
                    : analysis.status === "running"
                        ? "系统正在生成本次分析报告，完成后可继续刷新查看。"
                        : "已加载本次分析报告。";

            if (!structuredSuggestions.some(function hasFocusedSuggestion(item) {
                return item.id === state.focusedSuggestionId;
            })) {
                state.focusedSuggestionId = "";
            }

            elements.feedbackStatus.textContent = feedbackText;
            elements.score.textContent = analysis.score === null || analysis.score === undefined
                ? "--"
                : api.formatNumber(analysis.score, 1);
            elements.resultStatus.textContent = formatStatusLabel(analysis.status);
            elements.sampleFps.textContent = analysis.sample_fps + " FPS";
            elements.summary.textContent = analysis.summary_text || "当前暂无摘要。";
            applyStatePill(
                elements.statePill,
                analysis.status === "success" ? "success" : analysis.status === "failed" ? "failed" : analysis.status === "running" ? "running" : "waiting",
                analysis.status === "success" ? "已完成" : analysis.status === "failed" ? "失败" : analysis.status === "running" ? "生成中" : "等待中"
            );
            applyStatePill(elements.scoreBand, scoreTone.tone, scoreTone.label);
            applyScoreTone(elements.scoreCard, scoreTone.tone);
            elements.scoreCaption.textContent = scoreTone.caption;
            updateInsightCounts(elements, jointEntries.length, trajectoryEntries.length, suggestionEntries.length);

            syncResultTabs(elements, state, resolvePreferredResultTab({
                suggestions: suggestionEntries.length,
                joints: jointEntries.length,
                trajectory: trajectoryEntries.length,
            }), true);

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

            renderFocusStage();
            renderReviewShell();
            playback.renderStage();
            playback.renderControls();
        }

        function focusSuggestion(suggestion) {
            if (!suggestion) {
                return;
            }

            state.focusedSuggestionId = String(suggestion.id || "");
            state.focusIssue = suggestion;
            state.viewMode = "overlay";
            state.toggles.keypoints = true;
            if (suggestion.issue_type === "joint") {
                state.toggles.angles = true;
            }
            if (suggestion.issue_type === "trajectory") {
                state.toggles.trajectory = true;
            }
            renderToggles();
            syncSuggestionSelection(elements, state.focusedSuggestionId);
            renderFocusStage();
            setActiveFlowStep("focus");

            const focused = playback.focusIssue(suggestion);
            if (focused) {
                elements.feedbackStatus.textContent = "已定位 " + (suggestion.issue_label || "重点问题") + " 对应片段，可直接回看。";
                return;
            }

            elements.feedbackStatus.textContent = "回放数据仍在装配，稍后可点击建议直接定位对应片段。";
        }

        async function loadAnalysis(analysisIdValue) {
            if (!analysisIdValue) {
                renderEmptyState();
                return null;
            }

            elements.sessionBadge.textContent = "加载中";
            elements.actionStatus.textContent = "加载中";
            elements.feedbackStatus.textContent = "正在加载本次练习详情。";

            const results = await Promise.all([
                api.fetchJson("/api/analysis/" + analysisIdValue),
                api.fetchJson("/api/videos/standard"),
                api.fetchJson("/api/videos/learner"),
            ]);

            state.analysis = results[0].analysis;
            state.standardVideos = Array.isArray(results[1].items) ? results[1].items.slice() : [];
            state.learnerVideos = Array.isArray(results[2].items) ? results[2].items.slice() : [];
            state.viewMode = "skeleton";
            state.toggles.keypoints = true;
            state.toggles.trajectory = false;
            state.toggles.angles = false;
            state.focusedSuggestionId = "";
            state.focusIssue = null;
            state.previewLoading = true;

            renderResult();
            setActiveFlowStep("overview");
            renderWorkspaceStatus();
            await playback.loadPreviews();
            renderReviewShell();
            playback.renderControls();
            return state.analysis;
        }

        elements.playButton.addEventListener("click", function startPlayback() {
            setActiveFlowStep("controls");
            playback.start();
        });
        elements.pauseButton.addEventListener("click", function pausePlayback() {
            setActiveFlowStep("controls");
            playback.stop();
            playback.renderControls();
        });
        elements.speedButton.addEventListener("click", function cycleSpeed() {
            state.speedIndex = (state.speedIndex + 1) % 4;
            setActiveFlowStep("controls");
            playback.syncSpeed();
        });
        elements.muteButton.addEventListener("click", function toggleMute() {
            state.isMuted = !state.isMuted;
            setActiveFlowStep("controls");
            playback.syncMute();
        });
        elements.stepBackButton.addEventListener("click", function stepBack() {
            setActiveFlowStep("controls");
            playback.stepBy(-1);
        });
        elements.stepForwardButton.addEventListener("click", function stepForward() {
            setActiveFlowStep("controls");
            playback.stepBy(1);
        });
        elements.progress.addEventListener("input", function syncProgress() {
            setActiveFlowStep("controls");
            playback.setProgress(Number(elements.progress.value));
        });

        Object.keys(elements.toggleInputs).forEach(function bindToggle(key) {
            const input = elements.toggleInputs[key];
            if (!input) {
                return;
            }
            input.addEventListener("change", function handleToggleChange() {
                state.toggles[key] = input.checked;
                renderToggles();
                setActiveFlowStep("controls");
                playback.renderStage();
            });
        });

        elements.viewModeButtons.forEach(function bindViewModeButton(button) {
            button.addEventListener("click", function handleViewModeClick() {
                const nextMode = button.getAttribute("data-stage-view-mode");
                if (!nextMode || nextMode === state.viewMode) {
                    return;
                }
                state.viewMode = nextMode;
                renderToggles();
                setActiveFlowStep("controls");
                playback.renderStage();
            });
        });

        if (elements.resultTabButtons.length) {
            elements.resultTabButtons.forEach(function bindResultTab(button) {
                button.addEventListener("click", function handleTabClick() {
                    const tabName = button.getAttribute("data-result-tab");
                    setActiveFlowStep("focus");
                    syncResultTabs(elements, state, tabName, true);
                });
            });
        }

        elements.flowButtons.forEach(function bindFlowButton(button) {
            button.addEventListener("click", function handleFlowButtonClick() {
                const anchorKey = button.getAttribute("data-review-anchor");
                const target = document.getElementById("review-anchor-" + anchorKey);
                setActiveFlowStep(anchorKey);
                if (target) {
                    target.scrollIntoView({behavior: "smooth", block: "start"});
                }
            });
        });

        [elements.replayLink, elements.practiceLink, elements.openHistoryLink].forEach(function bindActionLink(link) {
            link.addEventListener("click", function handleActionClick() {
                setActiveFlowStep("next");
            });
        });

        if (elements.saveResultButton) {
            elements.saveResultButton.addEventListener("click", async function handleSaveClick() {
                if (!state.analysis || state.analysis.is_saved || state.analysis.status !== "success" || state.isSaving) {
                    renderSaveAction();
                    return;
                }

                state.isSaving = true;
                renderSaveAction();
                elements.feedbackStatus.textContent = "正在保存本次结果，保存后会进入结果记录。";
                setActiveFlowStep("next");

                try {
                    const data = await api.postJson("/api/analysis/" + encodeURIComponent(state.analysis.id) + "/save", {});
                    state.analysis = data.analysis || state.analysis;
                    elements.feedbackStatus.textContent = "本次结果已保存，现在可以回结果记录继续比较。";
                    renderResult();
                } catch (error) {
                    elements.feedbackStatus.textContent = "结果保存失败：" + error.message;
                } finally {
                    state.isSaving = false;
                    renderSaveAction();
                }
            });
        }

        elements.suggestions.addEventListener("click", function handleSuggestionClick(event) {
            const button = event.target.closest("[data-suggestion-id]");
            if (!button || !state.analysis) {
                return;
            }
            const suggestion = getStructuredSuggestions(state.analysis).find(function findSuggestion(item) {
                return item.id === button.getAttribute("data-suggestion-id");
            });
            focusSuggestion(suggestion || null);
        });

        renderToggles();
        playback.renderStage();
        playback.renderControls();
        renderEmptyState();

        loadAnalysis(analysisId).catch(function handleError(error) {
            renderEmptyState("结果加载失败：" + error.message);
            elements.feedbackStatus.textContent = "当前无法装配本次结果，请先确认记录是否存在。";
        });
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
