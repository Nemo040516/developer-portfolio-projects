(function bootstrapAnalysisSetupPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const PANEL_META = {
        standard: {
            title: "先从示范库里挑动作",
            copy: "当前只做 3 件事：选示范、选练习、进主舞台。",
            hint: "先锁定示范动作，右侧会固定当前组合。",
        },
        learner: {
            title: "再从最近练习里选一条",
            copy: "优先沿用最近练习，没有合适的再补传。",
            hint: "先从最近练习里继续；不合适时再补传。",
        },
        upload: {
            title: "没有合适素材时，直接补传新的练习",
            copy: "上传成功后会自动成为当前练习，不需要再重复选择。",
            hint: "上传完成后，右侧组合和主舞台会一起刷新。",
        },
    };

    function getPositiveQueryParam(name) {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get(name));
        return Number.isInteger(rawValue) && rawValue > 0 ? rawValue : null;
    }

    function updatePageUrl(standardVideoId, learnerVideoId) {
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
        window.history.replaceState(null, "", query ? "/analysis?" + query : "/analysis");
    }

    function dispatchSelectionChange(standardVideoId, learnerVideoId, standardVideo, learnerVideo) {
        document.dispatchEvent(new window.CustomEvent("motion:analysis-selection-change", {
            detail: {
                standardVideoId: standardVideoId || null,
                learnerVideoId: learnerVideoId || null,
                standardVideo: standardVideo || null,
                learnerVideo: learnerVideo || null,
            },
        }));
    }

    function getStagePreviewTarget(standardVideoId, learnerVideoId) {
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

    function dispatchStageOpen(standardVideoId, learnerVideoId) {
        const previewTarget = getStagePreviewTarget(standardVideoId, learnerVideoId);
        if (!previewTarget) {
            return;
        }
        document.dispatchEvent(new window.CustomEvent("motion:analysis-open-stage", {
            detail: {
                standardVideoId: standardVideoId || null,
                learnerVideoId: learnerVideoId || null,
                previewTarget: previewTarget,
            },
        }));
    }

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
            return {key: "earlier", label: "更早上传"};
        }
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const targetDay = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate());
        const diffDays = Math.round((today.getTime() - targetDay.getTime()) / 86400000);
        if (diffDays <= 0) {
            return {key: "today", label: "今天上传"};
        }
        if (diffDays === 1) {
            return {key: "yesterday", label: "昨天上传"};
        }
        return {key: "earlier", label: "更早上传"};
    }

    function buildLearnerVideoGroups(items) {
        const groups = [
            {key: "today", label: "今天上传", items: []},
            {key: "yesterday", label: "昨天上传", items: []},
            {key: "earlier", label: "更早上传", items: []},
        ];
        const groupMap = {};
        groups.forEach(function assignGroup(group) {
            groupMap[group.key] = group;
        });
        items.forEach(function assignItem(item) {
            groupMap[getUploadBucket(item.created_at).key].items.push(item);
        });
        return groups.filter(function filterGroup(group) {
            return group.items.length > 0;
        });
    }

    function renderVideoLibraryCard(item, activeId) {
        const activeClassName = item.id === activeId ? " is-active" : "";
        return [
            '<button class="compact-library-card compact-library-card--shelf', activeClassName, '" type="button" data-video-id="', item.id, '">',
            renderVideoCover(item, "封面待生成", "video-cover--card"),
            '<div class="compact-library-card__body">',
            '<div class="compact-library-card__status-row">',
            item.id === activeId
                ? '<span class="compact-library-card__eyebrow compact-library-card__eyebrow--active">当前已选</span>'
                : '<span class="compact-library-card__eyebrow">候选</span>',
            '<span class="badge">#', item.id, "</span></div>",
            '<div class="compact-library-card__title"><strong>', api.escapeHtml(getVideoLabel(item)), "</strong></div>",
            '<div class="compact-library-card__meta">',
            "<span>", api.formatNumber(item.duration_sec, 1), "s</span>",
            "<span>", api.formatNumber(item.frame_rate, 1), " FPS</span>",
            "<span>", api.escapeHtml(item.created_at), "</span>",
            "</div></div></button>",
        ].join("");
    }

    function buildVideoSelectionSummaryMarkup(item, emptyText, options) {
        if (!item) {
            return "<p>" + api.escapeHtml(emptyText) + "</p>";
        }
        const resolvedOptions = options || {};
        const isCompact = Boolean(resolvedOptions.compact);
        const uploadBucket = item.video_type === "learner" ? getUploadBucket(item.created_at) : null;
        const summaryClassNames = ["selector-summary", "selector-summary--media"];
        const metaParts = [
            "<span>" + api.formatNumber(item.duration_sec, 1) + "s</span>",
            isCompact ? "" : "<span>" + api.escapeHtml(item.width) + " x " + api.escapeHtml(item.height) + "</span>",
            "<span>" + api.formatNumber(item.frame_rate, 1) + " FPS</span>",
            "<span>" + api.escapeHtml(item.created_at) + "</span>",
        ].filter(Boolean);

        if (isCompact) {
            summaryClassNames.push("selector-summary--compact");
        }
        return [
            '<div class="', summaryClassNames.join(" "), '">',
            renderVideoCover(item, "封面待生成", "video-cover--summary"),
            '<div class="selector-summary__content">',
            '<div class="selector-summary__status-row">',
            '<span class="analysis-selection-state">当前已锁定</span>',
            uploadBucket ? '<span class="analysis-selection-state analysis-selection-state--muted">' + api.escapeHtml(uploadBucket.label) + "</span>" : "",
            "</div>",
            '<div class="selector-summary__head"><strong>', api.escapeHtml(getVideoLabel(item)), "</strong>",
            '<span class="badge">#', item.id, "</span></div>",
            '<div class="selector-summary__meta">', metaParts.join(""), "</div>",
            isCompact ? "" : '<p class="analysis-selection-note">已加入当前组合，可直接带去主舞台检查。</p>',
            "</div></div>",
        ].join("");
    }

    function renderVideoSelectionSummary(targets, item, emptyText, options) {
        const markup = buildVideoSelectionSummaryMarkup(item, emptyText, options);
        targets.forEach(function renderIntoTarget(target) {
            if (target) {
                target.innerHTML = markup;
            }
        });
    }

    function renderVideoLibraryShelf(target, items, activeId, emptyText) {
        if (!target) {
            return;
        }
        if (!items.length) {
            target.innerHTML = '<div class="empty-panel"><p>' + api.escapeHtml(emptyText) + "</p></div>";
            return;
        }
        target.innerHTML = [
            '<div class="analysis-video-shelf">',
            items.map(function renderItem(item) {
                return renderVideoLibraryCard(item, activeId);
            }).join(""),
            "</div>",
        ].join("");
    }

    function renderGroupedLearnerVideoShelves(target, items, activeId, emptyText) {
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
                '<section class="analysis-video-shelf-group">',
                '<div class="analysis-video-shelf-group__head">',
                "<strong>", api.escapeHtml(group.label), "</strong>",
                "<span>", group.items.length, " 条</span>",
                "</div>",
                '<div class="analysis-video-shelf">',
                group.items.map(function renderItem(item) {
                    return renderVideoLibraryCard(item, activeId);
                }).join(""),
                "</div>",
                "</section>",
            ].join("");
        }).join("");
    }

    function getVideoById(items, videoId) {
        return (Array.isArray(items) ? items : []).find(function findItem(item) {
            return item.id === videoId;
        }) || null;
    }

    function upsertVideo(items, nextVideo) {
        const filteredItems = (Array.isArray(items) ? items : []).filter(function filterItem(item) {
            return item.id !== nextVideo.id;
        });
        filteredItems.unshift(nextVideo);
        return filteredItems;
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
            workflowBadge: document.getElementById("analysis-workflow-badge"),
            workflowText: document.getElementById("analysis-workflow-text"),
            standardBadge: document.getElementById("analysis-standard-badge"),
            learnerBadge: document.getElementById("analysis-learner-badge"),
            inputStatus: document.getElementById("analysis-input-status"),
            stationTitle: document.getElementById("analysis-station-title"),
            taskShell: document.getElementById("analysis-task-shell"),
            taskSummaryText: document.getElementById("analysis-task-summary-text"),
            taskSummaryStandard: document.getElementById("analysis-task-summary-standard"),
            taskSummaryLearner: document.getElementById("analysis-task-summary-learner"),
            taskQuickHint: document.getElementById("analysis-task-quick-hint"),
            standardSearchInput: document.getElementById("analysis-standard-search"),
            standardSelect: document.getElementById("analysis-standard-select"),
            standardSpotlight: document.getElementById("analysis-standard-spotlight"),
            standardSpotlightStatus: document.getElementById("analysis-standard-spotlight-status"),
            standardLibrary: document.getElementById("analysis-standard-library"),
            standardCount: document.getElementById("analysis-standard-count"),
            learnerSearchInput: document.getElementById("analysis-learner-search"),
            learnerSelect: document.getElementById("analysis-learner-select"),
            learnerSpotlight: document.getElementById("analysis-learner-spotlight"),
            learnerSpotlightStatus: document.getElementById("analysis-learner-spotlight-status"),
            learnerLibrary: document.getElementById("analysis-learner-library"),
            learnerCount: document.getElementById("analysis-learner-count"),
            learnerFileInput: document.getElementById("analysis-learner-file"),
            uploadLearnerButton: document.getElementById("analysis-upload-learner"),
            switchLearnerPanelButton: document.getElementById("analysis-switch-learner-panel"),
            inputFeedback: document.getElementById("analysis-input-feedback"),
            stageEntryStatus: document.getElementById("analysis-stage-entry-status"),
            stageEntryCopy: document.getElementById("analysis-stage-entry-copy"),
            openStageLink: document.getElementById("analysis-open-stage-link"),
            currentStandard: document.getElementById("analysis-current-standard"),
            currentStandardTag: document.getElementById("analysis-current-standard-tag"),
            currentLearner: document.getElementById("analysis-current-learner"),
            currentLearnerTag: document.getElementById("analysis-current-learner-tag"),
            journeyStageButton: document.getElementById("analysis-journey-stage"),
            handoffCard: document.getElementById("analysis-handoff-card"),
            panelButtons: Array.from(document.querySelectorAll("[data-analysis-panel]")),
            panelContents: Array.from(document.querySelectorAll("[data-analysis-panel-content]")),
        };

        if (
            !api ||
            [
                elements.workflowBadge,
                elements.workflowText,
                elements.standardBadge,
                elements.learnerBadge,
                elements.inputStatus,
                elements.stationTitle,
                elements.taskShell,
                elements.taskSummaryText,
                elements.taskSummaryStandard,
                elements.taskSummaryLearner,
                elements.taskQuickHint,
                elements.standardSearchInput,
                elements.standardSelect,
                elements.standardSpotlight,
                elements.standardSpotlightStatus,
                elements.standardLibrary,
                elements.standardCount,
                elements.learnerSearchInput,
                elements.learnerSelect,
                elements.learnerSpotlight,
                elements.learnerSpotlightStatus,
                elements.learnerLibrary,
                elements.learnerCount,
                elements.learnerFileInput,
                elements.uploadLearnerButton,
                elements.switchLearnerPanelButton,
                elements.inputFeedback,
                elements.stageEntryStatus,
                elements.stageEntryCopy,
                elements.openStageLink,
                elements.currentStandard,
                elements.currentStandardTag,
                elements.currentLearner,
                elements.currentLearnerTag,
                elements.journeyStageButton,
                elements.handoffCard,
            ].some(function hasMissingElement(value) {
                return !value;
            })
        ) {
            return;
        }

        const state = {
            standardVideos: [],
            learnerVideos: [],
            selectedStandardVideoId: getPositiveQueryParam("standard_video_id"),
            learnerVideoId: getPositiveQueryParam("learner_video_id"),
            activePanel: "standard",
        };

        function syncSelectOptions(selectElement, items, selectedId, placeholderLabel) {
            if (!items.length) {
                selectElement.innerHTML = '<option value="">' + api.escapeHtml(placeholderLabel) + "</option>";
                selectElement.disabled = true;
                selectElement.value = "";
                return null;
            }

            selectElement.disabled = false;
            selectElement.innerHTML = [
                '<option value="">', api.escapeHtml(placeholderLabel), "</option>",
                items.map(function renderOption(item) {
                    return '<option value="' + item.id + '">#' + item.id + " " + api.escapeHtml(getVideoLabel(item)) + "</option>";
                }).join(""),
            ].join("");

            if (selectedId && items.some(function hasSelected(item) { return item.id === selectedId; })) {
                selectElement.value = String(selectedId);
                return selectedId;
            }

            selectElement.value = "";
            return null;
        }

        function buildPracticeStageLink() {
            const params = new URLSearchParams();
            if (state.selectedStandardVideoId) {
                params.set("standard_video_id", String(state.selectedStandardVideoId));
            }
            if (state.learnerVideoId) {
                params.set("learner_video_id", String(state.learnerVideoId));
            }
            if (state.selectedStandardVideoId && state.learnerVideoId) {
                params.set("preview_target", "both");
            } else if (state.selectedStandardVideoId) {
                params.set("preview_target", "standard");
            } else if (state.learnerVideoId) {
                params.set("preview_target", "learner");
            }
            const query = params.toString();
            return query
                ? ("/analysis?" + query + "#analysis-workspace-section")
                : "/analysis#analysis-workspace-section";
        }

        function getRecommendedPanel() {
            if (!state.selectedStandardVideoId) {
                return "standard";
            }
            if (!state.learnerVideoId) {
                return "learner";
            }
            return state.activePanel === "upload" ? "upload" : "learner";
        }

        function setActivePanel(panelKey) {
            if (!PANEL_META[panelKey]) {
                return;
            }
            state.activePanel = panelKey;

            elements.panelButtons.forEach(function syncPanelButton(button) {
                const buttonPanel = button.getAttribute("data-analysis-panel");
                button.classList.toggle("is-active", buttonPanel === panelKey);
            });

            elements.panelContents.forEach(function syncPanelContent(panel) {
                const contentPanel = panel.getAttribute("data-analysis-panel-content");
                const isActive = contentPanel === panelKey;
                panel.classList.toggle("is-active", isActive);
                panel.hidden = !isActive;
            });

            elements.stationTitle.textContent = PANEL_META[panelKey].title;
            elements.taskQuickHint.textContent = PANEL_META[panelKey].hint;
            elements.taskShell.setAttribute("data-active-panel", panelKey);
        }

        function syncJourneyState() {
            const hasStandard = Boolean(state.selectedStandardVideoId);
            const hasLearner = Boolean(state.learnerVideoId);
            const recommendedPanel = getRecommendedPanel();

            elements.panelButtons.forEach(function syncPanelButton(button) {
                const buttonPanel = button.getAttribute("data-analysis-panel");
                const isActive = buttonPanel === state.activePanel;
                const isDone = (buttonPanel === "standard" && hasStandard)
                    || (buttonPanel === "learner" && hasLearner);
                const isReady = buttonPanel === "standard"
                    || buttonPanel === "upload"
                    || (buttonPanel === "learner" && hasStandard);

                button.classList.toggle("is-active", isActive);
                button.classList.toggle("is-current", !isActive && buttonPanel === recommendedPanel);
                button.classList.toggle("is-done", isDone);
                button.classList.toggle("is-ready", isReady);
                button.classList.toggle("is-disabled", !isReady && buttonPanel !== "standard" && buttonPanel !== "upload");
            });

            const stageReady = hasStandard || hasLearner;
            const stageDone = hasStandard && hasLearner;
            elements.journeyStageButton.classList.toggle("is-ready", stageReady);
            elements.journeyStageButton.classList.toggle("is-done", stageDone);
            elements.journeyStageButton.classList.toggle("is-disabled", !stageReady);
        }

        function syncPageState() {
            const standardItem = getVideoById(state.standardVideos, state.selectedStandardVideoId);
            const learnerItem = getVideoById(state.learnerVideos, state.learnerVideoId);
            const hasStandard = Boolean(standardItem);
            const hasLearner = Boolean(learnerItem);
            const workbenchReady = hasStandard && hasLearner;
            let workflowBadgeText = "还未开始";
            let workflowText = "先锁定示范，再选或补传练习；准备好后直接进主舞台。";
            let inputStatusText = "等待准备";
            let inputFeedbackText = "其他页面带来的素材，会自动接到这里。";
            let stageEntryStatusText = "等待准备";
            let stageEntryCopyText = "先补齐示范和练习素材，再进入当前页主舞台。";

            if (workbenchReady) {
                workflowBadgeText = "准备完成";
                workflowText = "双素材都已固定，直接进主舞台。";
                inputStatusText = "可进主舞台";
                inputFeedbackText = "当前已带齐双素材，主舞台会自动载入。";
                stageEntryStatusText = "双素材已备齐";
                stageEntryCopyText = "双素材已带齐，可直接进主舞台。";
            } else if (hasStandard) {
                workflowBadgeText = "待补练习";
                workflowText = "示范动作已经锁定，接下来补一条练习视频。";
                inputStatusText = "缺少练习";
                inputFeedbackText = "示范已就绪，继续选一条练习或直接补传。";
                stageEntryStatusText = "仅示范已备齐";
                stageEntryCopyText = "可以先检查示范，但更建议补齐练习后一起看。";
            } else if (hasLearner) {
                workflowBadgeText = "待补示范";
                workflowText = "练习视频已经锁定，接下来补一条示范动作。";
                inputStatusText = "缺少示范";
                inputFeedbackText = "练习已就绪，继续选一条示范动作。";
                stageEntryStatusText = "仅练习已备齐";
                stageEntryCopyText = "可以先检查练习，但更建议补齐示范后一起看。";
            }

            elements.workflowBadge.textContent = workflowBadgeText;
            elements.workflowText.textContent = workflowText;
            elements.inputStatus.textContent = inputStatusText;
            elements.inputFeedback.textContent = inputFeedbackText;
            elements.stageEntryStatus.textContent = stageEntryStatusText;
            elements.stageEntryCopy.textContent = stageEntryCopyText;
            elements.standardBadge.textContent = hasStandard ? getVideoLabel(standardItem) : "等待数据";
            elements.learnerBadge.textContent = hasLearner ? getVideoLabel(learnerItem) : "等待数据";
            elements.taskSummaryStandard.textContent = hasStandard ? "示范：" + getVideoLabel(standardItem) : "示范待选";
            elements.taskSummaryLearner.textContent = hasLearner ? "练习：" + getVideoLabel(learnerItem) : "练习待选";
            elements.taskSummaryText.textContent = PANEL_META[state.activePanel].copy;
            elements.standardSpotlightStatus.textContent = hasStandard ? "已锁定示范" : "示范待选";
            elements.learnerSpotlightStatus.textContent = hasLearner ? "已锁定练习" : "练习待选";
            elements.currentStandardTag.textContent = hasStandard ? "已锁定" : "待选";
            elements.currentLearnerTag.textContent = hasLearner ? "已锁定" : "待选";
            if (elements.page) {
                elements.page.setAttribute("data-workbench-ready", workbenchReady ? "true" : "false");
            }

            renderVideoSelectionSummary(
                [elements.standardSpotlight],
                standardItem,
                "选中一条示范视频后，这里会固定显示当前摘要。"
            );
            renderVideoSelectionSummary(
                [elements.currentStandard],
                standardItem,
                "选中一条示范视频后，这里会固定显示当前摘要。",
                {compact: true}
            );
            renderVideoSelectionSummary(
                [elements.learnerSpotlight],
                learnerItem,
                "当前还没有选中练习视频。你可以先从最近练习里挑一条，或者切到“补传练习”。"
            );
            renderVideoSelectionSummary(
                [elements.currentLearner],
                learnerItem,
                "当前还没有选中练习视频。你可以先从最近练习里挑一条，或者切到“补传练习”。",
                {compact: true}
            );

            setActionLinkState(elements.openStageLink, buildPracticeStageLink(), hasStandard || hasLearner);
            syncJourneyState();
        }

        function renderStandardPicker() {
            const filteredItems = state.standardVideos.filter(function filterItem(item) {
                return matchesVideoSearch(item, elements.standardSearchInput.value);
            });
            const optionItems = filteredItems.slice();
            const shelfItems = filteredItems.slice();
            const selectedItem = getVideoById(state.standardVideos, state.selectedStandardVideoId);

            if (selectedItem && !optionItems.some(function hasSelected(item) { return item.id === selectedItem.id; })) {
                optionItems.unshift(selectedItem);
            }
            if (selectedItem && !shelfItems.some(function hasSelected(item) { return item.id === selectedItem.id; })) {
                shelfItems.unshift(selectedItem);
            }

            state.selectedStandardVideoId = syncSelectOptions(
                elements.standardSelect,
                optionItems,
                state.selectedStandardVideoId,
                "请选择示范视频"
            );

            elements.standardCount.textContent = filteredItems.length + " 条";
            renderVideoLibraryShelf(
                elements.standardLibrary,
                shelfItems,
                state.selectedStandardVideoId,
                "当前筛选条件下没有匹配的示范视频。"
            );
        }

        function renderLearnerPicker() {
            const filteredItems = state.learnerVideos.filter(function filterItem(item) {
                return matchesVideoSearch(item, elements.learnerSearchInput.value);
            });
            const optionItems = filteredItems.slice();
            const shelfItems = filteredItems.slice();
            const selectedItem = getVideoById(state.learnerVideos, state.learnerVideoId);

            if (selectedItem && !optionItems.some(function hasSelected(item) { return item.id === selectedItem.id; })) {
                optionItems.unshift(selectedItem);
            }
            if (selectedItem && !shelfItems.some(function hasSelected(item) { return item.id === selectedItem.id; })) {
                shelfItems.unshift(selectedItem);
            }

            state.learnerVideoId = syncSelectOptions(
                elements.learnerSelect,
                optionItems,
                state.learnerVideoId,
                "请选择练习视频"
            );

            elements.learnerCount.textContent = filteredItems.length + " 条";
            renderGroupedLearnerVideoShelves(
                elements.learnerLibrary,
                shelfItems,
                state.learnerVideoId,
                "当前筛选条件下没有匹配的练习视频。"
            );
        }

        function renderVideoPickers() {
            renderStandardPicker();
            renderLearnerPicker();
            syncPageState();
        }

        function jumpToHandoff() {
            elements.handoffCard.scrollIntoView({behavior: "smooth", block: "start"});
        }

        function jumpToWorkspace() {
            const workspaceSection = document.getElementById("analysis-workspace-section");
            if (workspaceSection) {
                workspaceSection.scrollIntoView({behavior: "smooth", block: "start"});
                return;
            }
            jumpToHandoff();
        }

        async function loadVideoLibraries() {
            const results = await Promise.all([
                api.fetchJson("/api/videos/standard"),
                api.fetchJson("/api/videos/learner"),
            ]);

            state.standardVideos = Array.isArray(results[0].items) ? results[0].items.slice() : [];
            state.learnerVideos = Array.isArray(results[1].items) ? results[1].items.slice() : [];

            if (state.selectedStandardVideoId && !getVideoById(state.standardVideos, state.selectedStandardVideoId)) {
                state.selectedStandardVideoId = null;
            }
            if (state.learnerVideoId && !getVideoById(state.learnerVideos, state.learnerVideoId)) {
                state.learnerVideoId = null;
            }

            renderVideoPickers();
            updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            dispatchSelectionChange(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                getVideoById(state.learnerVideos, state.learnerVideoId)
            );
            setActivePanel(getRecommendedPanel());
        }

        elements.panelButtons.forEach(function bindPanelButton(button) {
            button.addEventListener("click", function handlePanelButtonClick() {
                setActivePanel(button.getAttribute("data-analysis-panel"));
            });
        });

        elements.journeyStageButton.addEventListener("click", function jumpStage() {
            if (!state.selectedStandardVideoId && !state.learnerVideoId) {
                return;
            }
            dispatchStageOpen(state.selectedStandardVideoId, state.learnerVideoId);
            jumpToWorkspace();
        });

        elements.switchLearnerPanelButton.addEventListener("click", function openLearnerPanel() {
            setActivePanel("learner");
        });

        elements.openStageLink.addEventListener("click", function guardDisabledStageLink(event) {
            if (elements.openStageLink.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }
            event.preventDefault();
            dispatchStageOpen(state.selectedStandardVideoId, state.learnerVideoId);
            jumpToWorkspace();
        });

        elements.standardSearchInput.addEventListener("input", function handleStandardSearch() {
            renderStandardPicker();
            syncPageState();
        });
        elements.standardSelect.addEventListener("change", function handleStandardSelect() {
            const nextVideoId = Number(elements.standardSelect.value);
            state.selectedStandardVideoId = Number.isInteger(nextVideoId) && nextVideoId > 0 ? nextVideoId : null;
            renderStandardPicker();
            syncPageState();
            updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            dispatchSelectionChange(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                getVideoById(state.learnerVideos, state.learnerVideoId)
            );
            if (state.selectedStandardVideoId && !state.learnerVideoId) {
                setActivePanel("learner");
            }
        });
        elements.standardLibrary.addEventListener("click", function handleStandardLibraryClick(event) {
            const button = event.target.closest("[data-video-id]");
            if (!button) {
                return;
            }
            state.selectedStandardVideoId = Number(button.getAttribute("data-video-id"));
            renderStandardPicker();
            syncPageState();
            updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            dispatchSelectionChange(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                getVideoById(state.learnerVideos, state.learnerVideoId)
            );
            if (state.selectedStandardVideoId && !state.learnerVideoId) {
                setActivePanel("learner");
            }
        });

        elements.learnerSearchInput.addEventListener("input", function handleLearnerSearch() {
            renderLearnerPicker();
            syncPageState();
        });
        elements.learnerSelect.addEventListener("change", function handleLearnerSelect() {
            const nextVideoId = Number(elements.learnerSelect.value);
            state.learnerVideoId = Number.isInteger(nextVideoId) && nextVideoId > 0 ? nextVideoId : null;
            renderLearnerPicker();
            syncPageState();
            updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            dispatchSelectionChange(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                getVideoById(state.learnerVideos, state.learnerVideoId)
            );
            if (state.learnerVideoId) {
                setActivePanel("learner");
            }
        });
        elements.learnerLibrary.addEventListener("click", function handleLearnerLibraryClick(event) {
            const button = event.target.closest("[data-video-id]");
            if (!button) {
                return;
            }
            state.learnerVideoId = Number(button.getAttribute("data-video-id"));
            renderLearnerPicker();
            syncPageState();
            updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
            dispatchSelectionChange(
                state.selectedStandardVideoId,
                state.learnerVideoId,
                getVideoById(state.standardVideos, state.selectedStandardVideoId),
                getVideoById(state.learnerVideos, state.learnerVideoId)
            );
            if (state.learnerVideoId) {
                setActivePanel("learner");
            }
        });

        elements.uploadLearnerButton.addEventListener("click", async function uploadLearnerVideo() {
            const file = elements.learnerFileInput.files && elements.learnerFileInput.files[0];
            if (!file) {
                elements.inputStatus.textContent = "缺少文件";
                elements.inputFeedback.textContent = "请先选择一段练习视频。";
                return;
            }

            elements.inputStatus.textContent = "上传中";
            elements.inputFeedback.textContent = "正在上传练习视频并把它加入当前素材。";

            const formData = new FormData();
            formData.append("file", file);

            try {
                const data = await api.postForm("/api/videos/learner", formData);
                state.learnerVideos = upsertVideo(state.learnerVideos, data.video);
                state.learnerVideoId = data.video.id;
                elements.learnerSearchInput.value = "";
                elements.learnerFileInput.value = "";
                renderLearnerPicker();
                syncPageState();
                updatePageUrl(state.selectedStandardVideoId, state.learnerVideoId);
                dispatchSelectionChange(
                    state.selectedStandardVideoId,
                    state.learnerVideoId,
                    getVideoById(state.standardVideos, state.selectedStandardVideoId),
                    data.video
                );
                dispatchStageOpen(state.selectedStandardVideoId, state.learnerVideoId);
                elements.inputStatus.textContent = "上传成功";
                elements.inputFeedback.textContent = "练习视频已上传并自动选中。确认示范也没问题后，就可以直接在当前页主舞台检查。";
                setActivePanel("upload");
            } catch (error) {
                elements.inputStatus.textContent = "上传失败";
                elements.inputFeedback.textContent = "练习视频上传失败：" + error.message;
            }
        });

        setActivePanel(state.activePanel);
        syncPageState();
        loadVideoLibraries().catch(function handleError(error) {
            elements.inputStatus.textContent = "加载失败";
            elements.inputFeedback.textContent = "素材列表加载失败：" + error.message;
            elements.standardLibrary.innerHTML = '<div class="empty-panel"><p>示范视频列表加载失败，请稍后重试。</p></div>';
            elements.learnerLibrary.innerHTML = '<div class="empty-panel"><p>练习视频列表加载失败，请稍后重试。</p></div>';
        });
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
