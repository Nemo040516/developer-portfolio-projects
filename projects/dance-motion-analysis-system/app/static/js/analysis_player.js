(function bootstrapAnalysisPlayer(window) {
    "use strict";

    const SPEED_OPTIONS = [0.5, 1.0, 1.5, 2.0];
    const TRAJECTORY_WINDOW = 8;
    const PREVIEW_API_PATHS = {
        standard: "/api/videos/standard/",
        learner: "/api/videos/learner/",
    };
    const OVERLAY_LIMBS = [
        {pair: ["neck", "left_shoulder"], className: "analysis-overlay-line analysis-overlay-line--left-arm"},
        {pair: ["neck", "right_shoulder"], className: "analysis-overlay-line analysis-overlay-line--right-arm"},
        {pair: ["left_shoulder", "left_elbow"], className: "analysis-overlay-line analysis-overlay-line--left-arm"},
        {pair: ["left_elbow", "left_hand"], className: "analysis-overlay-line analysis-overlay-line--left-arm"},
        {pair: ["right_shoulder", "right_elbow"], className: "analysis-overlay-line analysis-overlay-line--right-arm"},
        {pair: ["right_elbow", "right_hand"], className: "analysis-overlay-line analysis-overlay-line--right-arm"},
        {pair: ["neck", "chest"], className: "analysis-overlay-line analysis-overlay-line--torso"},
        {pair: ["chest", "abdomen"], className: "analysis-overlay-line analysis-overlay-line--torso"},
        {pair: ["abdomen", "left_hip"], className: "analysis-overlay-line analysis-overlay-line--torso"},
        {pair: ["abdomen", "right_hip"], className: "analysis-overlay-line analysis-overlay-line--torso"},
        {pair: ["left_hip", "left_knee"], className: "analysis-overlay-line analysis-overlay-line--left-leg"},
        {pair: ["left_knee", "left_foot"], className: "analysis-overlay-line analysis-overlay-line--left-leg"},
        {pair: ["right_hip", "right_knee"], className: "analysis-overlay-line analysis-overlay-line--right-leg"},
        {pair: ["right_knee", "right_foot"], className: "analysis-overlay-line analysis-overlay-line--right-leg"},
    ];
    const TRAJECTORY_NODE_NAMES = ["left_hand", "right_hand", "left_foot", "right_foot"];
    const ANGLE_DEFINITIONS = {
        left_elbow: {label: "左肘", points: ["left_shoulder", "left_elbow", "left_hand"], className: "analysis-angle analysis-angle--left-arm"},
        right_elbow: {label: "右肘", points: ["right_shoulder", "right_elbow", "right_hand"], className: "analysis-angle analysis-angle--right-arm"},
        left_knee: {label: "左膝", points: ["left_hip", "left_knee", "left_foot"], className: "analysis-angle analysis-angle--left-leg"},
        right_knee: {label: "右膝", points: ["right_hip", "right_knee", "right_foot"], className: "analysis-angle analysis-angle--right-leg"},
    };
    const DEFAULT_PLAYER_COPY = {
        standard: {title: "示范动作尚未载入", emptyTitle: "等待本次回看", emptyText: "结果加载后，这里会显示示范动作的真实视频与叠加信息。"},
        learner: {title: "练习动作尚未载入", emptyTitle: "等待本次回看", emptyText: "开始比对并生成结果后，这里会显示练习动作的真实视频与叠加信息。"},
    };

    function clamp(value, minValue, maxValue) {
        return Math.min(maxValue, Math.max(minValue, Number(value)));
    }

    function formatSvgNumber(value) {
        return String(Math.round(Number(value) * 10000) / 10000);
    }

    function distance(pointA, pointB) {
        return Math.hypot(Number(pointA.x) - Number(pointB.x), Number(pointA.y) - Number(pointB.y));
    }

    function calculateAngle(pointA, pointB, pointC) {
        if (!pointA || !pointB || !pointC) {
            return null;
        }
        const lengthAB = distance(pointA, pointB);
        const lengthCB = distance(pointC, pointB);
        if (lengthAB <= 0 || lengthCB <= 0) {
            return null;
        }
        const vectorBA = {x: Number(pointA.x) - Number(pointB.x), y: Number(pointA.y) - Number(pointB.y)};
        const vectorBC = {x: Number(pointC.x) - Number(pointB.x), y: Number(pointC.y) - Number(pointB.y)};
        const dotProduct = (vectorBA.x * vectorBC.x) + (vectorBA.y * vectorBC.y);
        return Math.acos(clamp(dotProduct / (lengthAB * lengthCB), -1, 1)) * 180 / Math.PI;
    }

    function resolveNodeClassName(nodeKey) {
        if (nodeKey.indexOf("left_") === 0) {
            return nodeKey.indexOf("_hip") > 0 || nodeKey.indexOf("_knee") > 0 || nodeKey.indexOf("_foot") > 0
                ? "analysis-overlay-node analysis-overlay-node--left-leg"
                : "analysis-overlay-node analysis-overlay-node--left-arm";
        }
        if (nodeKey.indexOf("right_") === 0) {
            return nodeKey.indexOf("_hip") > 0 || nodeKey.indexOf("_knee") > 0 || nodeKey.indexOf("_foot") > 0
                ? "analysis-overlay-node analysis-overlay-node--right-leg"
                : "analysis-overlay-node analysis-overlay-node--right-arm";
        }
        return "analysis-overlay-node analysis-overlay-node--torso";
    }

    function resolveTrajectoryClassName(nodeName) {
        if (nodeName.indexOf("left_") === 0) {
            return nodeName.indexOf("_foot") > 0
                ? "analysis-trajectory analysis-trajectory--left-leg"
                : "analysis-trajectory analysis-trajectory--left-arm";
        }
        return nodeName.indexOf("_foot") > 0
            ? "analysis-trajectory analysis-trajectory--right-leg"
            : "analysis-trajectory analysis-trajectory--right-arm";
    }

    function matchesFocusRegion(className, focusIssue) {
        return Boolean(
            focusIssue &&
            focusIssue.target_region &&
            className.indexOf("--" + focusIssue.target_region) >= 0
        );
    }

    function appendFocusClass(className, focusIssue) {
        return matchesFocusRegion(className, focusIssue) ? className + " is-focus" : className;
    }

    function resolveViewModeLabel(viewMode) {
        if (viewMode === "skeleton") {
            return "火柴人";
        }
        if (viewMode === "overlay") {
            return "视频叠加";
        }
        return "只看视频";
    }

    function resolveViewMode(state) {
        if (state && (state.viewMode === "video" || state.viewMode === "overlay" || state.viewMode === "skeleton")) {
            return state.viewMode;
        }
        return state && state.toggles && state.toggles.overlay === false ? "video" : "overlay";
    }

    function resolveSourceUrls(preview) {
        const primaryUrl = preview && preview.source_video_url ? String(preview.source_video_url) : "";
        const fallbackUrl = preview && preview.source_video_fallback_url ? String(preview.source_video_fallback_url) : "";
        return {
            primary: primaryUrl || fallbackUrl,
            fallback: primaryUrl && fallbackUrl && primaryUrl !== fallbackUrl ? fallbackUrl : "",
        };
    }

    function renderToggleChips(target, api, toggleState, viewMode) {
        if (!target) {
            return;
        }
        const chips = [
            [resolveViewModeLabel(viewMode), true, true],
            ["关键点", toggleState.keypoints],
            ["轨迹", toggleState.trajectory],
            ["角度", toggleState.angles],
        ];
        target.innerHTML = chips.map(function renderChip(item) {
            if (item[2]) {
                return '<span class="frame-chip is-on">' + api.escapeHtml(item[0]) + "</span>";
            }
            return '<span class="' + (item[1] ? "frame-chip is-on" : "frame-chip") + '">' + api.escapeHtml(item[0]) + " " + (item[1] ? "开" : "关") + "</span>";
        }).join("");
    }

    function buildSkeletonFigureParts(nodes, focusIssue) {
        const parts = [];
        OVERLAY_LIMBS.forEach(function renderLimb(limb) {
            const start = nodes[limb.pair[0]];
            const end = nodes[limb.pair[1]];
            if (!start || !end || start.cx || end.cx) {
                return;
            }
            parts.push('<line class="' + appendFocusClass(limb.className, focusIssue) + '" x1="' + formatSvgNumber(start.x) + '" y1="' + formatSvgNumber(start.y) + '" x2="' + formatSvgNumber(end.x) + '" y2="' + formatSvgNumber(end.y) + '"></line>');
        });
        Object.keys(nodes).forEach(function renderNode(key) {
            const node = nodes[key];
            if (!node) {
                return;
            }
            if (key === "head_circle") {
                parts.push('<circle class="analysis-overlay-head" cx="' + formatSvgNumber(node.cx) + '" cy="' + formatSvgNumber(node.cy) + '" r="' + formatSvgNumber(node.r) + '"></circle>');
                return;
            }
        });
        return parts;
    }

    function buildKeypointParts(nodes, focusIssue) {
        const parts = [];
        Object.keys(nodes).forEach(function renderNode(key) {
            const node = nodes[key];
            if (!node || key === "head_circle") {
                return;
            }
            parts.push('<circle class="' + appendFocusClass(resolveNodeClassName(key), focusIssue) + '" cx="' + formatSvgNumber(node.x) + '" cy="' + formatSvgNumber(node.y) + '" r="0.011"></circle>');
        });
        return parts;
    }

    function buildTrajectoryParts(frames, currentOffset, focusIssue) {
        const parts = [];
        const startOffset = Math.max(0, currentOffset - TRAJECTORY_WINDOW + 1);
        TRAJECTORY_NODE_NAMES.forEach(function renderTrajectory(nodeName) {
            const points = [];
            for (let offset = startOffset; offset <= currentOffset; offset += 1) {
                const frame = frames[offset];
                const node = frame && frame.nodes ? frame.nodes[nodeName] : null;
                if (!node || node.cx) {
                    continue;
                }
                points.push(formatSvgNumber(node.x) + "," + formatSvgNumber(node.y));
            }
            if (points.length < 2) {
                return;
            }
            const currentFrame = frames[currentOffset];
            const currentNode = currentFrame && currentFrame.nodes ? currentFrame.nodes[nodeName] : null;
            const className = appendFocusClass(resolveTrajectoryClassName(nodeName), focusIssue);
            parts.push('<polyline class="' + className + '" points="' + points.join(" ") + '"></polyline>');
            if (currentNode && !currentNode.cx) {
                parts.push('<circle class="' + className.replace("analysis-trajectory", "analysis-trajectory-dot") + '" cx="' + formatSvgNumber(currentNode.x) + '" cy="' + formatSvgNumber(currentNode.y) + '" r="0.013"></circle>');
            }
        });
        return parts;
    }

    function buildAngleParts(nodes, focusIssue) {
        return Object.keys(ANGLE_DEFINITIONS).map(function renderAngle(key) {
            const definition = ANGLE_DEFINITIONS[key];
            const pointA = nodes[definition.points[0]];
            const pointB = nodes[definition.points[1]];
            const pointC = nodes[definition.points[2]];
            const angleValue = calculateAngle(pointA, pointB, pointC);
            if (!pointB || pointB.cx || angleValue === null) {
                return "";
            }
            return '<g class="' + appendFocusClass(definition.className, focusIssue) + '"><circle class="analysis-angle-anchor" cx="' + formatSvgNumber(pointB.x) + '" cy="' + formatSvgNumber(pointB.y) + '" r="0.015"></circle></g>';
        }).filter(Boolean);
    }

    function buildAngleReadoutParts(nodes, focusIssue, api, anglesEnabled) {
        return Object.keys(ANGLE_DEFINITIONS).map(function renderAngleReadout(key) {
            const definition = ANGLE_DEFINITIONS[key];
            const pointA = nodes[definition.points[0]];
            const pointB = nodes[definition.points[1]];
            const pointC = nodes[definition.points[2]];
            const angleValue = calculateAngle(pointA, pointB, pointC);
            const isFocus = matchesFocusRegion(definition.className, focusIssue);
            const classes = ["analysis-angle-pill"];
            if (anglesEnabled) {
                classes.push("is-active");
            }
            if (isFocus) {
                classes.push("is-focus");
            }
            return [
                '<span class="', classes.join(" "), '">',
                '<strong>', api.escapeHtml(definition.label), "</strong>",
                "<span>", angleValue === null ? "--" : (Math.round(angleValue) + "°"), "</span>",
                "</span>",
            ].join("");
        }).join("");
    }

    function createRuntime(options) {
        const api = options.api;
        const elements = options.elements;
        const state = options.state;
        const getAnalysisVideoItem = options.getAnalysisVideoItem;
        const formatTimestamp = options.formatTimestamp;
        const emptyCopy = options.emptyCopy || DEFAULT_PLAYER_COPY;
        const onPreviewError = typeof options.onPreviewError === "function" ? options.onPreviewError : null;
        let playTimer = null;
        let focusTimer = null;

        function buildSourceErrorMessage(playerKey, videoElement) {
            const prefix = playerKey === "standard" ? "示范" : "练习";
            const mediaError = videoElement && videoElement.error;
            if (!mediaError) {
                return prefix + "视频源加载失败。";
            }
            if (mediaError.code === 4) {
                return prefix + "视频源编码不受当前浏览器支持，当前无法直接播放当前视频。";
            }
            if (mediaError.code === 3) {
                return prefix + "视频源解码失败，当前无法正常播放。";
            }
            if (mediaError.code === 2) {
                return prefix + "视频源网络读取失败，请稍后重试。";
            }
            if (mediaError.code === 1) {
                return prefix + "视频源加载已被中止。";
            }
            return prefix + "视频源加载失败。";
        }

        function updateProgressBounds() {
            const frameCounts = [getPreviewFrameCount(state.previews.standard), getPreviewFrameCount(state.previews.learner)].filter(function filterCount(value) {
                return value > 0;
            });
            state.progressMax = frameCounts.length ? Math.max.apply(null, frameCounts) - 1 : 0;
            state.progress = clamp(state.progress, 0, state.progressMax);
        }

        function clearVideoSource(video) {
            if (!video) {
                return;
            }
            video.pause();
            video.removeAttribute("src");
            video.removeAttribute("data-source-url");
            video.load();
        }

        function getPreviewFrameCount(preview) {
            return preview && Array.isArray(preview.frames) ? preview.frames.length : 0;
        }

        function hasPreviewData() {
            return getPreviewFrameCount(state.previews.standard) > 0 || getPreviewFrameCount(state.previews.learner) > 0;
        }

        function getPlaybackRatio() {
            if (!hasPreviewData() || state.progressMax <= 0) {
                return 0;
            }
            return clamp(state.progress / state.progressMax, 0, 1);
        }

        function getPreviewFrameInfo(preview) {
            const frames = preview && Array.isArray(preview.frames) ? preview.frames : [];
            if (!frames.length) {
                return {offset: 0, frame: null};
            }
            if (frames.length === 1) {
                return {offset: 0, frame: frames[0]};
            }
            const rawOffset = Math.round(getPlaybackRatio() * (frames.length - 1));
            const offset = clamp(rawOffset, 0, frames.length - 1);
            return {offset: offset, frame: frames[offset]};
        }

        function setPlayerEmptyState(playerKey, title, description, frameMetaText, timeMetaText) {
            const player = elements.players[playerKey];
            const copy = emptyCopy[playerKey] || DEFAULT_PLAYER_COPY[playerKey];
            player.title.textContent = title || copy.title;
            player.caption.textContent = description || copy.emptyText;
            player.frameMeta.textContent = frameMetaText || "等待数据";
            player.timeMeta.textContent = timeMetaText || "等待数据";
            player.overlay.innerHTML = "";
            player.overlay.hidden = true;
            player.video.hidden = true;
            player.empty.hidden = false;
            player.empty.innerHTML = "<strong>" + api.escapeHtml(copy.emptyTitle) + "</strong><p>" + api.escapeHtml(description || copy.emptyText) + "</p>";
            if (player.readout) {
                player.readout.innerHTML = '<span class="analysis-angle-pill">等待数据</span>';
            }
        }

        function syncVideoSource(playerKey) {
            const player = elements.players[playerKey];
            const preview = state.previews[playerKey];
            const sourceUrls = resolveSourceUrls(preview);
            const nextSourceUrl = sourceUrls.primary;
            if (!nextSourceUrl) {
                clearVideoSource(player.video);
                if (player.mediaStage) {
                    player.mediaStage.style.removeProperty("aspect-ratio");
                }
                player.video.removeAttribute("data-fallback-source-url");
                player.video.removeAttribute("data-source-stage");
                return;
            }
            if (player.mediaStage && preview.video && preview.video.width && preview.video.height) {
                player.mediaStage.style.aspectRatio = preview.video.width + " / " + preview.video.height;
            }
            if (player.video.getAttribute("data-source-url") !== nextSourceUrl) {
                player.video.pause();
                player.video.setAttribute("data-source-url", nextSourceUrl);
                player.video.setAttribute("data-fallback-source-url", sourceUrls.fallback);
                player.video.setAttribute("data-source-stage", "primary");
                player.video.src = nextSourceUrl;
                player.video.load();
            }
            player.video.muted = state.isMuted;
            player.video.defaultMuted = state.isMuted;
            player.video.playbackRate = SPEED_OPTIONS[state.speedIndex];
        }

        function syncVideoFrame(video, frame) {
            if (!video || !frame) {
                return;
            }
            const nextTimeSec = Number(frame.timestamp_ms || 0) / 1000;
            if (!Number.isFinite(nextTimeSec)) {
                return;
            }
            try {
                if (Math.abs(Number(video.currentTime || 0) - nextTimeSec) > 0.04) {
                    video.currentTime = nextTimeSec;
                }
            } catch (error) {
                return;
            }
            video.pause();
        }

        function renderOverlay(target, preview, frameOffset, viewMode) {
            if (!target) {
                return;
            }
            const frames = preview && Array.isArray(preview.frames) ? preview.frames : [];
            const frame = frames[frameOffset] || null;
            if (!frame || !frame.nodes) {
                target.innerHTML = "";
                return;
            }
            if (viewMode === "video") {
                target.innerHTML = "";
                return;
            }
            const parts = [];
            parts.push.apply(parts, buildSkeletonFigureParts(frame.nodes, state.focusIssue));
            if (state.toggles.keypoints) {
                parts.push.apply(parts, buildKeypointParts(frame.nodes, state.focusIssue));
            }
            if (state.toggles.trajectory) {
                parts.push.apply(parts, buildTrajectoryParts(frames, frameOffset, state.focusIssue));
            }
            if (state.toggles.angles) {
                parts.push.apply(parts, buildAngleParts(frame.nodes, state.focusIssue));
            }
            target.innerHTML = parts.join("");
        }

        function renderAngleReadout(target, preview, frameOffset) {
            if (!target) {
                return;
            }
            const frames = preview && Array.isArray(preview.frames) ? preview.frames : [];
            const frame = frames[frameOffset] || null;
            if (!frame || !frame.nodes) {
                target.innerHTML = '<span class="analysis-angle-pill">等待数据</span>';
                return;
            }
            target.innerHTML = buildAngleReadoutParts(frame.nodes, state.focusIssue, api, Boolean(state.toggles.angles));
        }

        function renderSinglePlayer(playerKey) {
            const player = elements.players[playerKey];
            const preview = state.previews[playerKey];
            const previewError = state.previewErrors[playerKey];
            const sourceVideo = getAnalysisVideoItem(playerKey);
            const fallbackLabel = sourceVideo ? options.getVideoLabel(sourceVideo) : DEFAULT_PLAYER_COPY[playerKey].title;
            const viewMode = resolveViewMode(state);
            renderToggleChips(player.chips, api, state.toggles, viewMode);
            if (player.mediaStage) {
                player.mediaStage.dataset.viewMode = viewMode;
            }

            if (state.previewLoading || state.previewLoadingKey === playerKey) {
                setPlayerEmptyState(playerKey, fallbackLabel, "正在读取本次回看需要的预览帧和源视频。", "装配中", "等待数据");
                return;
            }
            if (previewError) {
                setPlayerEmptyState(playerKey, fallbackLabel, previewError, "加载失败", "等待数据");
                return;
            }
            const frameInfo = getPreviewFrameInfo(preview);
            if (!state.analysis && preview && frameInfo.frame) {
                syncVideoSource(playerKey);
                syncVideoFrame(player.video, frameInfo.frame);
                renderOverlay(player.overlay, preview, frameInfo.offset, viewMode);
                renderAngleReadout(player.readout, preview, frameInfo.offset);
                player.video.hidden = viewMode === "skeleton";
                player.overlay.hidden = viewMode === "video" || !player.overlay.innerHTML;
                player.empty.hidden = true;
                player.title.textContent = options.getVideoLabel(preview.video || sourceVideo);
                player.caption.textContent = (playerKey === "standard" ? "示范素材检查，共 " : "练习素材检查，共 ") + preview.frames.length + " 帧，当前按 " + preview.sample_fps + " FPS 查看。";
                player.frameMeta.textContent = "第 " + frameInfo.frame.frame_index + " 帧 / " + (frameInfo.offset + 1) + " / " + preview.frames.length;
                player.timeMeta.textContent = "视频时间 " + formatTimestamp(frameInfo.frame.timestamp_ms);
                return;
            }
            if (!state.analysis) {
                setPlayerEmptyState(
                    playerKey,
                    fallbackLabel,
                    sourceVideo
                        ? "已选中这条素材。如需确认动作和骨骼是否稳定，可点上方按钮在本页检查。"
                        : DEFAULT_PLAYER_COPY[playerKey].emptyText
                );
                return;
            }
            if (!preview || !frameInfo.frame) {
                setPlayerEmptyState(playerKey, fallbackLabel, "当前结果已加载，但没有拿到可回看的预览帧。", "无可用帧", "等待数据");
                return;
            }

            syncVideoSource(playerKey);
            syncVideoFrame(player.video, frameInfo.frame);
            renderOverlay(player.overlay, preview, frameInfo.offset, viewMode);
            renderAngleReadout(player.readout, preview, frameInfo.offset);
            player.video.hidden = viewMode === "skeleton";
            player.overlay.hidden = viewMode === "video" || !player.overlay.innerHTML;
            player.empty.hidden = true;
            player.title.textContent = options.getVideoLabel(preview.video || sourceVideo);
            player.caption.textContent = (playerKey === "standard" ? "示范回放，共 " : "练习回放，共 ") + preview.frames.length + " 帧，当前按 " + preview.sample_fps + " FPS 对齐本次结果。";
            player.frameMeta.textContent = "第 " + frameInfo.frame.frame_index + " 帧 / " + (frameInfo.offset + 1) + " / " + preview.frames.length;
            player.timeMeta.textContent = "视频时间 " + formatTimestamp(frameInfo.frame.timestamp_ms);
        }

        function renderStage() {
            renderSinglePlayer("standard");
            renderSinglePlayer("learner");
        }

        function renderControls() {
            const speed = SPEED_OPTIONS[state.speedIndex];
            const totalFrames = hasPreviewData() ? state.progressMax + 1 : 0;
            const currentFrame = totalFrames ? clamp(state.progress, 0, state.progressMax) + 1 : 0;
            const isDisabled = state.previewLoading || Boolean(state.previewLoadingKey) || !hasPreviewData();
            let playbackStatusText = state.analysis ? "等待结果" : "等待检查";
            if (state.previewLoading || state.previewLoadingKey) {
                playbackStatusText = "装配中";
            } else if (hasPreviewData()) {
                playbackStatusText = state.isPlaying ? "播放中" : "已暂停";
            } else if (state.analysis) {
                playbackStatusText = "回放未就绪";
            } else {
                playbackStatusText = "可检查素材";
            }
            if (state.focusIssue && !state.isPlaying && hasPreviewData()) {
                playbackStatusText = "已定位 " + state.focusIssue.issue_label;
            }
            elements.playbackStatus.textContent = playbackStatusText;
            elements.stepLabel.textContent = state.focusIssue && hasPreviewData()
                ? "对比帧：" + currentFrame + " / " + totalFrames + " · 已定位 " + state.focusIssue.issue_label
                : "对比帧：" + currentFrame + " / " + totalFrames;
            elements.speedLabel.textContent = speed.toFixed(1) + "x";
            elements.progress.max = String(Math.max(0, state.progressMax));
            elements.progress.value = String(clamp(state.progress, 0, Math.max(0, state.progressMax)));
            elements.progress.disabled = isDisabled;
            elements.playButton.disabled = isDisabled;
            elements.pauseButton.disabled = isDisabled;
            elements.stepBackButton.disabled = isDisabled;
            elements.stepForwardButton.disabled = isDisabled;
            elements.speedButton.disabled = isDisabled;
            elements.muteButton.disabled = !hasPreviewData();
            elements.muteButton.classList.toggle("is-active", state.isMuted);
            elements.playButton.classList.toggle("is-active", state.isPlaying);
            elements.pauseButton.classList.toggle("is-active", !state.isPlaying && hasPreviewData());
            elements.speedButton.textContent = "倍速 " + speed.toFixed(1) + "x";
            elements.muteButton.textContent = state.isMuted ? "已静音" : "声音开";
        }

        function stop() {
            state.isPlaying = false;
            if (playTimer) {
                window.clearInterval(playTimer);
                playTimer = null;
            }
            Object.keys(elements.players).forEach(function pausePlayer(key) {
                const player = elements.players[key];
                if (player && player.video) {
                    player.video.pause();
                }
            });
        }

        function reset() {
            state.previewLoading = false;
            state.previewLoadingKey = "";
            state.previews.standard = null;
            state.previews.learner = null;
            state.previewErrors.standard = "";
            state.previewErrors.learner = "";
            state.focusIssue = null;
            if (focusTimer) {
                window.clearTimeout(focusTimer);
                focusTimer = null;
            }
            state.progress = 0;
            state.progressMax = 0;
            stop();
            clearVideoSource(elements.players.standard.video);
            clearVideoSource(elements.players.learner.video);
            if (elements.players.standard.mediaStage) {
                elements.players.standard.mediaStage.style.removeProperty("aspect-ratio");
            }
            if (elements.players.learner.mediaStage) {
                elements.players.learner.mediaStage.style.removeProperty("aspect-ratio");
            }
        }

        function clearPlayerPreview(playerKey) {
            state.previewErrors[playerKey] = "";
            state.previews[playerKey] = null;
            if (!state.previews.standard && !state.previews.learner) {
                state.focusIssue = null;
                stop();
            }
            clearVideoSource(elements.players[playerKey].video);
            if (elements.players[playerKey].mediaStage) {
                elements.players[playerKey].mediaStage.style.removeProperty("aspect-ratio");
            }
            updateProgressBounds();
            syncRender();
        }

        function syncRender() {
            renderStage();
            renderControls();
        }

        function focusIssue(issue) {
            if (!hasPreviewData() || !issue) {
                return false;
            }
            stop();
            state.focusIssue = {
                issue_type: String(issue.issue_type || ""),
                issue_key: String(issue.issue_key || ""),
                issue_label: String(issue.issue_label || "重点问题"),
                target_region: String(issue.target_region || ""),
            };
            if (Number.isFinite(Number(issue.center_offset))) {
                state.progress = clamp(Number(issue.center_offset), 0, state.progressMax);
            }
            syncRender();
            if (focusTimer) {
                window.clearTimeout(focusTimer);
            }
            focusTimer = window.setTimeout(function releaseFocusIssue() {
                state.focusIssue = null;
                focusTimer = null;
                syncRender();
            }, 2600);
            return true;
        }

        function tickPlayback() {
            if (state.progress >= state.progressMax) {
                stop();
                syncRender();
                return;
            }
            state.progress = Math.min(state.progressMax, state.progress + 1);
            syncRender();
        }

        function start() {
            if (!hasPreviewData()) {
                return;
            }
            if (state.progress >= state.progressMax) {
                state.progress = 0;
            }
            stop();
            state.isPlaying = true;
            const speed = SPEED_OPTIONS[state.speedIndex];
            const sampleFps = Number(
                (state.analysis && state.analysis.sample_fps)
                || (state.previews.standard && state.previews.standard.sample_fps)
                || (state.previews.learner && state.previews.learner.sample_fps)
                || 5
            );
            playTimer = window.setInterval(tickPlayback, Math.max(80, Math.round(1000 / Math.max(1, sampleFps * speed))));
            syncRender();
        }

        function stepBy(delta) {
            if (!hasPreviewData()) {
                return;
            }
            stop();
            state.progress = clamp(state.progress + delta, 0, state.progressMax);
            syncRender();
        }

        function setProgress(value) {
            if (!hasPreviewData()) {
                return;
            }
            stop();
            state.progress = clamp(value, 0, state.progressMax);
            syncRender();
        }

        function syncMute() {
            Object.keys(elements.players).forEach(function syncPlayerMute(key) {
                const player = elements.players[key];
                if (player && player.video) {
                    player.video.muted = state.isMuted;
                    player.video.defaultMuted = state.isMuted;
                }
            });
            renderControls();
        }

        function syncSpeed() {
            Object.keys(elements.players).forEach(function syncPlayerSpeed(key) {
                const player = elements.players[key];
                if (player && player.video) {
                    player.video.playbackRate = SPEED_OPTIONS[state.speedIndex];
                }
            });
            if (state.isPlaying) {
                start();
            } else {
                renderControls();
            }
        }

        async function loadPreviews() {
            if (!state.analysis) {
                reset();
                renderStage();
                renderControls();
                return;
            }
            stop();
            state.focusIssue = null;
            if (focusTimer) {
                window.clearTimeout(focusTimer);
                focusTimer = null;
            }
            state.previewLoading = true;
            state.previewLoadingKey = "";
            state.previews.standard = null;
            state.previews.learner = null;
            state.previewErrors.standard = "";
            state.previewErrors.learner = "";
            state.progress = 0;
            state.progressMax = 0;
            renderStage();
            renderControls();

            const sampleFps = Number(state.analysis.sample_fps || 5);
            const results = await Promise.allSettled([
                api.fetchJson(PREVIEW_API_PATHS.standard + state.analysis.standard_video_id + "/preview?sample_fps=" + encodeURIComponent(sampleFps)),
                api.fetchJson(PREVIEW_API_PATHS.learner + state.analysis.learner_video_id + "/preview?sample_fps=" + encodeURIComponent(sampleFps)),
            ]);

            state.previewLoading = false;
            if (results[0].status === "fulfilled") {
                state.previews.standard = results[0].value.preview;
            } else {
                state.previewErrors.standard = "示范视频回放加载失败：" + results[0].reason.message;
                clearVideoSource(elements.players.standard.video);
            }
            if (results[1].status === "fulfilled") {
                state.previews.learner = results[1].value.preview;
            } else {
                state.previewErrors.learner = "练习视频回放加载失败：" + results[1].reason.message;
                clearVideoSource(elements.players.learner.video);
            }

            updateProgressBounds();
            state.progress = 0;
            renderStage();
            renderControls();
        }

        async function loadSelectionPreview(playerKey, videoId, sampleFps) {
            stop();
            state.focusIssue = null;
            state.previewLoadingKey = playerKey;
            state.previewErrors[playerKey] = "";
            state.previews[playerKey] = null;
            updateProgressBounds();
            renderStage();
            renderControls();

            try {
                const data = await api.fetchJson(PREVIEW_API_PATHS[playerKey] + videoId + "/preview?sample_fps=" + encodeURIComponent(sampleFps));
                state.previews[playerKey] = data.preview;
                state.progress = 0;
                updateProgressBounds();
                renderStage();
                renderControls();
                return data.preview;
            } catch (error) {
                state.previewErrors[playerKey] = (playerKey === "standard" ? "示范" : "练习") + "素材检查失败：" + error.message;
                clearVideoSource(elements.players[playerKey].video);
                renderStage();
                renderControls();
                throw error;
            } finally {
                state.previewLoadingKey = "";
                renderStage();
                renderControls();
            }
        }

        Object.keys(elements.players).forEach(function bindPlayerEvents(key) {
            const player = elements.players[key];
            if (!player || !player.video) {
                return;
            }
            player.video.addEventListener("loadedmetadata", renderStage);
            player.video.addEventListener("loadedmetadata", function handleLoadedMetadata() {
                state.previewErrors[key] = "";
            });
            player.video.addEventListener("error", function handleVideoError() {
                const fallbackUrl = String(player.video.getAttribute("data-fallback-source-url") || "").trim();
                const currentSourceUrl = String(player.video.getAttribute("data-source-url") || "").trim();
                const sourceStage = String(player.video.getAttribute("data-source-stage") || "primary");
                if (fallbackUrl && currentSourceUrl !== fallbackUrl && sourceStage !== "fallback") {
                    player.video.pause();
                    player.video.setAttribute("data-source-url", fallbackUrl);
                    player.video.setAttribute("data-source-stage", "fallback");
                    player.video.src = fallbackUrl;
                    player.video.load();
                    renderStage();
                    renderControls();
                    return;
                }
                state.previewErrors[key] = buildSourceErrorMessage(key, player.video);
                if (onPreviewError) {
                    onPreviewError(key, state.previewErrors[key]);
                }
                renderStage();
                renderControls();
            });
        });

        return {
            reset: reset,
            stop: stop,
            start: start,
            stepBy: stepBy,
            setProgress: setProgress,
            syncMute: syncMute,
            syncSpeed: syncSpeed,
            focusIssue: focusIssue,
            clearPlayerPreview: clearPlayerPreview,
            loadSelectionPreview: loadSelectionPreview,
            loadPreviews: loadPreviews,
            renderStage: renderStage,
            renderControls: renderControls,
            hasPreviewData: hasPreviewData,
        };
    }

    window.MotionAnalysisPlayer = {
        createRuntime: createRuntime,
    };
})(window);
