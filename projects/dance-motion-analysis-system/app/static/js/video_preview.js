(function bootstrapVideoPreviewPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const settings = Object.assign({
        pagePath: "/standard-preview",
        apiPathPrefix: "/api/videos/standard/",
        listApiPath: "/api/videos/standard",
        videoTypeLabel: "视频",
        invalidIdText: "请输入有效的视频 ID。",
        loadingText: "正在生成或读取视频预览结果。",
        successStatusText: "已完成",
        successFeedbackLines: [
            "视频预览结果已就绪。",
            "当前页面会同页播放视频源回放和火柴人回放，用于确认两者是否同步。",
            "如果这里仍然出现明显不自然角度，优先说明问题在姿态提取或节点映射，而不是展示层。"
        ],
        errorPrefix: "视频预览加载失败：",
        metaEmptyText: "当前无法展示视频预览结果。",
        selectionEmptyText: "当前还没有可用视频。",
        filterEmptyText: "当前筛选条件下没有匹配的视频。",
        nextStepText: "确认骨骼播放稳定后，再继续后续流程。",
        actionLinks: [],
        enable3dPreview: false,
        playerAriaLabel: "视频原始骨骼预览",
    }, window.MotionVideoPreviewConfig || {});

    const PREVIEW_FILE_LABELS = {
        keypoints_path: "关键点结果",
        skeleton_path: "骨骼结果",
        template_path: "动作模板",
        angles_path: "角度结果",
        normalized_data_path: "归一化结果",
    };
    const VIEW_BOX = 100;
    const VIEW_PADDING = 10;
    const PROPORTION_FACTORS = {
        shoulderHalf: 0.34,
        hipHalf: 0.22,
        chestOffset: 0.42,
        upperArm: 0.78,
        forearm: 0.74,
        thigh: 1.02,
        calf: 0.96,
        headRadius: 0.24,
        headOffset: 0.34,
    };
    const LIMBS = [
        {pair: ["neck", "left_shoulder"], className: "stickman-limb stickman-limb--left-arm"},
        {pair: ["neck", "right_shoulder"], className: "stickman-limb stickman-limb--right-arm"},
        {pair: ["left_shoulder", "left_elbow"], className: "stickman-limb stickman-limb--left-arm"},
        {pair: ["left_elbow", "left_hand"], className: "stickman-limb stickman-limb--left-arm"},
        {pair: ["right_shoulder", "right_elbow"], className: "stickman-limb stickman-limb--right-arm"},
        {pair: ["right_elbow", "right_hand"], className: "stickman-limb stickman-limb--right-arm"},
        {pair: ["neck", "chest"], className: "stickman-limb stickman-limb--torso"},
        {pair: ["chest", "abdomen"], className: "stickman-limb stickman-limb--torso"},
        {pair: ["abdomen", "left_hip"], className: "stickman-limb stickman-limb--torso"},
        {pair: ["abdomen", "right_hip"], className: "stickman-limb stickman-limb--torso"},
        {pair: ["left_hip", "left_knee"], className: "stickman-limb stickman-limb--left-leg"},
        {pair: ["left_knee", "left_foot"], className: "stickman-limb stickman-limb--left-leg"},
        {pair: ["right_hip", "right_knee"], className: "stickman-limb stickman-limb--right-leg"},
        {pair: ["right_knee", "right_foot"], className: "stickman-limb stickman-limb--right-leg"},
    ];
    const THREE_D_LIMBS = [
        {pair: ["head", "neck"], colorKey: "torso"},
        {pair: ["neck", "left_shoulder"], colorKey: "leftArm"},
        {pair: ["neck", "right_shoulder"], colorKey: "rightArm"},
        {pair: ["left_shoulder", "left_elbow"], colorKey: "leftArm"},
        {pair: ["left_elbow", "left_hand"], colorKey: "leftArm"},
        {pair: ["right_shoulder", "right_elbow"], colorKey: "rightArm"},
        {pair: ["right_elbow", "right_hand"], colorKey: "rightArm"},
        {pair: ["neck", "chest"], colorKey: "torso"},
        {pair: ["chest", "abdomen"], colorKey: "torso"},
        {pair: ["abdomen", "left_hip"], colorKey: "torso"},
        {pair: ["abdomen", "right_hip"], colorKey: "torso"},
        {pair: ["left_hip", "left_knee"], colorKey: "leftLeg"},
        {pair: ["left_knee", "left_foot"], colorKey: "leftLeg"},
        {pair: ["right_hip", "right_knee"], colorKey: "rightLeg"},
        {pair: ["right_knee", "right_foot"], colorKey: "rightLeg"},
    ];
    const THREE_D_COLORS = {
        torso: "84, 70, 61",
        leftArm: "23, 107, 99",
        rightArm: "198, 91, 61",
        leftLeg: "50, 108, 168",
        rightLeg: "183, 110, 39",
    };

    function getVideoLabel(item) {
        if (!item) {
            return "视频";
        }
        return item.display_name || item.original_filename || ("视频 #" + item.id);
    }

    function getQueryVideoId() {
        const params = new URLSearchParams(window.location.search);
        const value = Number(params.get("video_id"));
        return Number.isInteger(value) && value > 0 ? value : null;
    }

    function getQuerySampleFps() {
        const params = new URLSearchParams(window.location.search);
        const value = Number(params.get("sample_fps"));
        return Number.isInteger(value) && value > 0 ? value : 5;
    }

    function updateUrl(videoId, sampleFps) {
        const params = new URLSearchParams(window.location.search);
        if (videoId) {
            params.set("video_id", String(videoId));
        } else {
            params.delete("video_id");
        }
        if (sampleFps) {
            params.set("sample_fps", String(sampleFps));
        } else {
            params.delete("sample_fps");
        }
        const nextUrl = params.toString() ? settings.pagePath + "?" + params.toString() : settings.pagePath;
        window.history.replaceState(null, "", nextUrl);
    }

    function formatTimestamp(timestampMs) {
        return api.formatNumber(timestampMs / 1000, 2) + "s";
    }

    function resolveSourceUrls(preview) {
        const primaryUrl = preview && preview.source_video_url ? String(preview.source_video_url) : "";
        const fallbackUrl = preview && preview.source_video_fallback_url ? String(preview.source_video_fallback_url) : "";
        return {
            primary: primaryUrl || fallbackUrl,
            fallback: primaryUrl && fallbackUrl && primaryUrl !== fallbackUrl ? fallbackUrl : "",
        };
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

    function cloneNodes(nodes) {
        const nextNodes = {};
        Object.keys(nodes || {}).forEach(function cloneNode(key) {
            nextNodes[key] = Object.assign({}, nodes[key]);
        });
        return nextNodes;
    }

    function vectorBetween(from, to) {
        return {
            x: Number(to.x) - Number(from.x),
            y: Number(to.y) - Number(from.y),
        };
    }

    function vectorLength(vector) {
        return Math.hypot(Number(vector.x), Number(vector.y));
    }

    function normalizeVector(vector) {
        const length = vectorLength(vector);
        if (length <= 0.000001) {
            return null;
        }
        return {
            x: Number(vector.x) / length,
            y: Number(vector.y) / length,
        };
    }

    function dotProduct(a, b) {
        return (Number(a.x) * Number(b.x)) + (Number(a.y) * Number(b.y));
    }

    function scaleVector(vector, length) {
        return {
            x: Number(vector.x) * length,
            y: Number(vector.y) * length,
        };
    }

    function addPoint(origin, vector) {
        return {
            x: Number(origin.x) + Number(vector.x),
            y: Number(origin.y) + Number(vector.y),
        };
    }

    function perpendicular(vector) {
        return {
            x: -Number(vector.y),
            y: Number(vector.x),
        };
    }

    function directionOrFallback(from, to, fallback) {
        const direct = from && to ? normalizeVector(vectorBetween(from, to)) : null;
        return direct || fallback;
    }

    function buildRenderableFrame(frame) {
        if (!frame || !frame.nodes || !Object.keys(frame.nodes).length) {
            return frame;
        }

        const rawNodes = cloneNodes(frame.nodes);
        const rawNeck = rawNodes.neck;
        const rawAbdomen = rawNodes.abdomen;
        const rawLeftShoulder = rawNodes.left_shoulder;
        const rawRightShoulder = rawNodes.right_shoulder;
        const rawLeftHip = rawNodes.left_hip;
        const rawRightHip = rawNodes.right_hip;

        if (!rawNeck || !rawAbdomen || !rawLeftShoulder || !rawRightShoulder || !rawLeftHip || !rawRightHip) {
            return {
                frame_index: frame.frame_index,
                timestamp_ms: frame.timestamp_ms,
                nodes: cloneNodes(frame.nodes),
                nodes_3d: Object.assign({}, frame.nodes_3d || {}),
            };
        }

        const torsoAxis = directionOrFallback(rawNeck, rawAbdomen, {x: 0, y: 1});
        const torsoLength = Math.max(vectorLength(vectorBetween(rawNeck, rawAbdomen)), 0.12);
        let shoulderAxis = normalizeVector(perpendicular(torsoAxis)) || {x: 1, y: 0};
        if (dotProduct(vectorBetween(rawNeck, rawLeftShoulder), shoulderAxis) < 0) {
            shoulderAxis = scaleVector(shoulderAxis, -1);
        }

        const neck = {x: Number(rawNeck.x), y: Number(rawNeck.y)};
        const chest = addPoint(neck, scaleVector(torsoAxis, torsoLength * PROPORTION_FACTORS.chestOffset));
        const abdomen = addPoint(neck, scaleVector(torsoAxis, torsoLength));
        const leftShoulder = addPoint(neck, scaleVector(shoulderAxis, torsoLength * PROPORTION_FACTORS.shoulderHalf));
        const rightShoulder = addPoint(neck, scaleVector(shoulderAxis, -torsoLength * PROPORTION_FACTORS.shoulderHalf));
        const leftHip = addPoint(abdomen, scaleVector(shoulderAxis, torsoLength * PROPORTION_FACTORS.hipHalf));
        const rightHip = addPoint(abdomen, scaleVector(shoulderAxis, -torsoLength * PROPORTION_FACTORS.hipHalf));

        const leftUpperArmDir = directionOrFallback(rawLeftShoulder, rawNodes.left_elbow, {x: -0.66, y: 0.75});
        const rightUpperArmDir = directionOrFallback(rawRightShoulder, rawNodes.right_elbow, {x: 0.66, y: 0.75});
        const leftForearmDir = directionOrFallback(rawNodes.left_elbow, rawNodes.left_hand, leftUpperArmDir);
        const rightForearmDir = directionOrFallback(rawNodes.right_elbow, rawNodes.right_hand, rightUpperArmDir);
        const leftThighDir = directionOrFallback(rawLeftHip, rawNodes.left_knee, {x: -0.12, y: 0.99});
        const rightThighDir = directionOrFallback(rawRightHip, rawNodes.right_knee, {x: 0.12, y: 0.99});
        const leftCalfDir = directionOrFallback(rawNodes.left_knee, rawNodes.left_foot, leftThighDir);
        const rightCalfDir = directionOrFallback(rawNodes.right_knee, rawNodes.right_foot, rightThighDir);

        const leftElbow = addPoint(leftShoulder, scaleVector(leftUpperArmDir, torsoLength * PROPORTION_FACTORS.upperArm));
        const rightElbow = addPoint(rightShoulder, scaleVector(rightUpperArmDir, torsoLength * PROPORTION_FACTORS.upperArm));
        const leftHand = addPoint(leftElbow, scaleVector(leftForearmDir, torsoLength * PROPORTION_FACTORS.forearm));
        const rightHand = addPoint(rightElbow, scaleVector(rightForearmDir, torsoLength * PROPORTION_FACTORS.forearm));
        const leftKnee = addPoint(leftHip, scaleVector(leftThighDir, torsoLength * PROPORTION_FACTORS.thigh));
        const rightKnee = addPoint(rightHip, scaleVector(rightThighDir, torsoLength * PROPORTION_FACTORS.thigh));
        const leftFoot = addPoint(leftKnee, scaleVector(leftCalfDir, torsoLength * PROPORTION_FACTORS.calf));
        const rightFoot = addPoint(rightKnee, scaleVector(rightCalfDir, torsoLength * PROPORTION_FACTORS.calf));
        const headCenter = addPoint(neck, scaleVector(torsoAxis, -torsoLength * PROPORTION_FACTORS.headOffset));

        return {
            frame_index: frame.frame_index,
            timestamp_ms: frame.timestamp_ms,
            nodes_3d: Object.assign({}, frame.nodes_3d || {}),
            nodes: {
                head_circle: {
                    cx: headCenter.x,
                    cy: headCenter.y,
                    r: torsoLength * PROPORTION_FACTORS.headRadius,
                },
                neck: neck,
                left_shoulder: leftShoulder,
                right_shoulder: rightShoulder,
                left_elbow: leftElbow,
                right_elbow: rightElbow,
                left_hand: leftHand,
                right_hand: rightHand,
                chest: chest,
                abdomen: abdomen,
                left_hip: leftHip,
                right_hip: rightHip,
                left_knee: leftKnee,
                right_knee: rightKnee,
                left_foot: leftFoot,
                right_foot: rightFoot,
            },
        };
    }

    function buildViewport(frames) {
        let minX = Number.POSITIVE_INFINITY;
        let minY = Number.POSITIVE_INFINITY;
        let maxX = Number.NEGATIVE_INFINITY;
        let maxY = Number.NEGATIVE_INFINITY;

        (frames || []).forEach(function scanFrame(frame) {
            const nodes = frame && frame.nodes ? frame.nodes : {};
            Object.keys(nodes).forEach(function scanNode(key) {
                const node = nodes[key];
                if (!node) {
                    return;
                }
                if (key === "head_circle") {
                    minX = Math.min(minX, Number(node.cx) - Number(node.r));
                    maxX = Math.max(maxX, Number(node.cx) + Number(node.r));
                    minY = Math.min(minY, Number(node.cy) - Number(node.r));
                    maxY = Math.max(maxY, Number(node.cy) + Number(node.r));
                    return;
                }
                minX = Math.min(minX, Number(node.x));
                maxX = Math.max(maxX, Number(node.x));
                minY = Math.min(minY, Number(node.y));
                maxY = Math.max(maxY, Number(node.y));
            });
        });

        if (!Number.isFinite(minX) || !Number.isFinite(minY) || !Number.isFinite(maxX) || !Number.isFinite(maxY)) {
            return null;
        }

        const width = Math.max(0.01, maxX - minX);
        const height = Math.max(0.01, maxY - minY);
        const usableSize = VIEW_BOX - (VIEW_PADDING * 2);
        const scale = Math.min(usableSize / width, usableSize / height);
        return {
            minX: minX,
            minY: minY,
            scale: scale,
            offsetX: (VIEW_BOX - (width * scale)) / 2,
            offsetY: (VIEW_BOX - (height * scale)) / 2,
        };
    }

    function scalePoint(node, viewport) {
        if (!viewport) {
            return {
                x: Number(node.x) * VIEW_BOX,
                y: Number(node.y) * VIEW_BOX,
            };
        }
        return {
            x: viewport.offsetX + ((Number(node.x) - viewport.minX) * viewport.scale),
            y: viewport.offsetY + ((Number(node.y) - viewport.minY) * viewport.scale),
        };
    }

    function scaleCircle(node, viewport) {
        if (!viewport) {
            return {
                cx: Number(node.cx) * VIEW_BOX,
                cy: Number(node.cy) * VIEW_BOX,
                r: Number(node.r) * VIEW_BOX,
            };
        }
        return {
            cx: viewport.offsetX + ((Number(node.cx) - viewport.minX) * viewport.scale),
            cy: viewport.offsetY + ((Number(node.cy) - viewport.minY) * viewport.scale),
            r: Number(node.r) * viewport.scale,
        };
    }

    function renderStickman(target, frame, viewport) {
        if (!target) {
            return;
        }
        if (!frame || !frame.nodes || !Object.keys(frame.nodes).length) {
            target.innerHTML = '<div class="empty-panel"><p>当前帧没有可绘制的骨骼节点。</p></div>';
            return;
        }

        const parts = [];
        LIMBS.forEach(function renderLimb(limb) {
            const start = frame.nodes[limb.pair[0]];
            const end = frame.nodes[limb.pair[1]];
            if (!start || !end || start.cx || end.cx) {
                return;
            }
            const from = scalePoint(start, viewport);
            const to = scalePoint(end, viewport);
            parts.push(
                '<line class="' + limb.className + '" x1="' + from.x + '" y1="' + from.y +
                '" x2="' + to.x + '" y2="' + to.y + '"></line>'
            );
        });

        Object.keys(frame.nodes).forEach(function renderNode(key) {
            const node = frame.nodes[key];
            if (key === "head_circle") {
                const circle = scaleCircle(node, viewport);
                parts.push(
                    '<circle class="stickman-head" cx="' + circle.cx +
                    '" cy="' + circle.cy +
                    '" r="' + circle.r + '"></circle>'
                );
                return;
            }
            const point = scalePoint(node, viewport);
            let nodeClassName = "stickman-node stickman-node--torso";
            if (key.indexOf("left_") === 0) {
                nodeClassName = key.indexOf("_hip") > 0 || key.indexOf("_knee") > 0 || key.indexOf("_foot") > 0
                    ? "stickman-node stickman-node--left-leg"
                    : "stickman-node stickman-node--left-arm";
            } else if (key.indexOf("right_") === 0) {
                nodeClassName = key.indexOf("_hip") > 0 || key.indexOf("_knee") > 0 || key.indexOf("_foot") > 0
                    ? "stickman-node stickman-node--right-leg"
                    : "stickman-node stickman-node--right-arm";
            }
            parts.push(
                '<circle class="' + nodeClassName + '" cx="' + point.x + '" cy="' + point.y + '" r="1.8"></circle>'
            );
        });

        target.innerHTML = [
            '<svg class="stickman-svg" viewBox="0 0 ', VIEW_BOX, ' ', VIEW_BOX, '" aria-label="', api.escapeHtml(settings.playerAriaLabel), '">',
            parts.join(""),
            "</svg>",
        ].join("");
    }

    function build3DViewport(frames) {
        let minX = Number.POSITIVE_INFINITY;
        let minY = Number.POSITIVE_INFINITY;
        let minZ = Number.POSITIVE_INFINITY;
        let maxX = Number.NEGATIVE_INFINITY;
        let maxY = Number.NEGATIVE_INFINITY;
        let maxZ = Number.NEGATIVE_INFINITY;

        (frames || []).forEach(function scanFrame(frame) {
            const nodes = frame && frame.nodes_3d ? frame.nodes_3d : {};
            Object.keys(nodes).forEach(function scanNode(key) {
                const node = nodes[key];
                if (!node) {
                    return;
                }
                minX = Math.min(minX, Number(node.x));
                minY = Math.min(minY, Number(node.y));
                minZ = Math.min(minZ, Number(node.z));
                maxX = Math.max(maxX, Number(node.x));
                maxY = Math.max(maxY, Number(node.y));
                maxZ = Math.max(maxZ, Number(node.z));
            });
        });

        if (!Number.isFinite(minX) || !Number.isFinite(minY) || !Number.isFinite(minZ)) {
            return null;
        }

        const span = Math.max(maxX - minX, maxY - minY, (maxZ - minZ) * 1.35, 0.12);
        return {
            centerX: (minX + maxX) / 2,
            centerY: (minY + maxY) / 2,
            centerZ: (minZ + maxZ) / 2,
            scale: 1 / span,
        };
    }

    function normalize3DPoint(node, viewport) {
        return {
            x: (Number(node.x) - viewport.centerX) * viewport.scale,
            y: (viewport.centerY - Number(node.y)) * viewport.scale,
            z: (viewport.centerZ - Number(node.z)) * viewport.scale * 1.2,
        };
    }

    function rotate3DPoint(point, viewState) {
        const yaw = Number(viewState.yaw || 0);
        const pitch = Number(viewState.pitch || 0);
        const cosYaw = Math.cos(yaw);
        const sinYaw = Math.sin(yaw);
        const cosPitch = Math.cos(pitch);
        const sinPitch = Math.sin(pitch);

        const x1 = (point.x * cosYaw) - (point.z * sinYaw);
        const z1 = (point.x * sinYaw) + (point.z * cosYaw);
        const y2 = (point.y * cosPitch) - (z1 * sinPitch);
        const z2 = (point.y * sinPitch) + (z1 * cosPitch);

        return {
            x: x1,
            y: y2,
            z: z2,
        };
    }

    function syncCanvasSize(canvas) {
        if (!canvas) {
            return null;
        }
        const rect = canvas.getBoundingClientRect();
        if (!rect.width || !rect.height) {
            return null;
        }
        const dpr = window.devicePixelRatio || 1;
        const width = Math.max(1, Math.round(rect.width * dpr));
        const height = Math.max(1, Math.round(rect.height * dpr));
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }
        return {
            width: width,
            height: height,
            dpr: dpr,
        };
    }

    function project3DPoint(point, canvasMetrics, viewState) {
        const cameraDistance = 3.2 / Math.max(0.65, Number(viewState.zoom || 1));
        const depth = Math.max(0.45, cameraDistance - point.z);
        const perspective = 1.2 / depth;
        return {
            x: (canvasMetrics.width / 2) + (point.x * canvasMetrics.width * 0.34 * perspective),
            y: (canvasMetrics.height / 2) - (point.y * canvasMetrics.height * 0.34 * perspective),
            depth: point.z,
        };
    }

    function get3DColor(colorKey, alpha) {
        return "rgba(" + (THREE_D_COLORS[colorKey] || THREE_D_COLORS.torso) + ", " + alpha + ")";
    }

    function resolve3DNodeColor(key) {
        if (key.indexOf("left_") === 0) {
            return key.indexOf("_hip") > 0 || key.indexOf("_knee") > 0 || key.indexOf("_foot") > 0
                ? "leftLeg"
                : "leftArm";
        }
        if (key.indexOf("right_") === 0) {
            return key.indexOf("_hip") > 0 || key.indexOf("_knee") > 0 || key.indexOf("_foot") > 0
                ? "rightLeg"
                : "rightArm";
        }
        return "torso";
    }

    function clear3DStage(canvas, metaTarget, message) {
        if (metaTarget) {
            metaTarget.textContent = message || "等待数据";
        }
        const metrics = syncCanvasSize(canvas);
        if (!canvas || !metrics) {
            return;
        }
        const context = canvas.getContext("2d");
        context.clearRect(0, 0, metrics.width, metrics.height);
        context.fillStyle = "rgba(84, 70, 61, 0.58)";
        context.font = Math.max(22, Math.round(metrics.width / 28)) + "px sans-serif";
        context.textAlign = "center";
        context.textBaseline = "middle";
        context.fillText(message || "等待数据", metrics.width / 2, metrics.height / 2);
    }

    function renderThreeDStage(canvas, frame, viewport, viewState, metaTarget) {
        if (!canvas) {
            return;
        }
        if (!frame || !frame.nodes_3d || !Object.keys(frame.nodes_3d).length || !viewport) {
            clear3DStage(canvas, metaTarget, "当前没有可用的 3D 骨架");
            return;
        }

        const metrics = syncCanvasSize(canvas);
        if (!metrics) {
            return;
        }

        const context = canvas.getContext("2d");
        context.clearRect(0, 0, metrics.width, metrics.height);

        const projectedNodes = {};
        const nodeDepths = [];
        Object.keys(frame.nodes_3d).forEach(function projectNode(key) {
            const rotated = rotate3DPoint(normalize3DPoint(frame.nodes_3d[key], viewport), viewState);
            const projected = project3DPoint(rotated, metrics, viewState);
            projectedNodes[key] = projected;
            nodeDepths.push(projected.depth);
        });

        const depthSortedSegments = THREE_D_LIMBS.map(function mapSegment(limb) {
            const start = projectedNodes[limb.pair[0]];
            const end = projectedNodes[limb.pair[1]];
            if (!start || !end) {
                return null;
            }
            return {
                colorKey: limb.colorKey,
                start: start,
                end: end,
                depth: (start.depth + end.depth) / 2,
            };
        }).filter(Boolean).sort(function sortSegment(left, right) {
            return Number(left.depth) - Number(right.depth);
        });

        const nodeEntries = Object.keys(projectedNodes).map(function mapNode(key) {
            return {
                key: key,
                point: projectedNodes[key],
                colorKey: resolve3DNodeColor(key),
            };
        }).sort(function sortNode(left, right) {
            return Number(left.point.depth) - Number(right.point.depth);
        });

        const floorY = metrics.height * 0.78;
        context.strokeStyle = "rgba(84, 70, 61, 0.12)";
        context.lineWidth = Math.max(1, metrics.width / 540);
        for (let index = 0; index < 5; index += 1) {
            const offset = (index - 2) * (metrics.width * 0.12);
            context.beginPath();
            context.moveTo((metrics.width * 0.12) + offset, floorY);
            context.lineTo((metrics.width * 0.32) + offset, metrics.height * 0.22);
            context.stroke();
        }
        for (let row = 0; row < 4; row += 1) {
            const y = floorY - (row * metrics.height * 0.11);
            context.beginPath();
            context.moveTo(metrics.width * 0.08, y);
            context.lineTo(metrics.width * 0.92, y);
            context.stroke();
        }

        depthSortedSegments.forEach(function drawSegment(segment) {
            context.beginPath();
            context.strokeStyle = get3DColor(segment.colorKey, 0.88);
            context.lineCap = "round";
            context.lineWidth = Math.max(2.5, metrics.width / 180);
            context.moveTo(segment.start.x, segment.start.y);
            context.lineTo(segment.end.x, segment.end.y);
            context.stroke();
        });

        nodeEntries.forEach(function drawNode(nodeEntry) {
            context.beginPath();
            context.fillStyle = get3DColor(nodeEntry.colorKey, 0.96);
            context.arc(
                nodeEntry.point.x,
                nodeEntry.point.y,
                Math.max(3, metrics.width / (nodeEntry.key === "head" ? 64 : 92)),
                0,
                Math.PI * 2
            );
            context.fill();
        });

        if (metaTarget) {
            metaTarget.textContent = "视角 " + Math.round((viewState.yaw || 0) * 57.3) + "° / 俯仰 "
                + Math.round((viewState.pitch || 0) * 57.3) + "° / 缩放 "
                + api.formatNumber(viewState.zoom || 1, 2) + "x";
        }
    }

    function fillActionTemplate(template, values) {
        return String(template || "").replace(/\{(\w+)\}/g, function replaceToken(match, key) {
            return Object.prototype.hasOwnProperty.call(values, key) ? encodeURIComponent(values[key]) : "";
        });
    }

    function findFrameOffsetByTime(frames, targetMs) {
        if (!frames.length) {
            return 0;
        }

        let matchedIndex = 0;
        for (let index = 0; index < frames.length; index += 1) {
            if (Number(frames[index].timestamp_ms) <= targetMs) {
                matchedIndex = index;
                continue;
            }
            break;
        }
        return matchedIndex;
    }

    function renderMeta(target, preview) {
        const video = preview.video;
        const fileRows = Object.keys(PREVIEW_FILE_LABELS)
            .filter(function hasFile(key) {
                return preview.files && preview.files[key];
            })
            .map(function renderFileRow(key) {
                return [
                    '<div class="path-row"><strong>', PREVIEW_FILE_LABELS[key], "</strong><code>",
                    api.escapeHtml(preview.files[key]),
                    "</code></div>",
                ].join("");
            }).join("");
        const actionRows = (settings.actionLinks || []).map(function renderAction(item) {
            const href = fillActionTemplate(item.hrefTemplate, {
                id: video.id,
                sample_fps: preview.sample_fps,
            });
            return '<a class="action-link" href="' + href + '">' + api.escapeHtml(item.label) + "</a>";
        }).join("");

        target.innerHTML = [
            '<div class="result-group"><strong>当前视频摘要</strong>',
            '<div class="info-grid">',
            '<div class="info-row"><strong>', api.escapeHtml(settings.videoTypeLabel), "</strong><span>#", video.id, " ", api.escapeHtml(getVideoLabel(video)), "</span></div>",
            '<div class="info-row"><strong>采样速度</strong><span>', preview.sample_fps, " FPS</span></div>",
            '<div class="info-row"><strong>可回放帧数</strong><span>', preview.frame_count, "</span></div>",
            "</div></div>",
            '<div class="result-group"><strong>下一步建议</strong>',
            '<p class="muted-text">', api.escapeHtml(settings.nextStepText), "</p>",
            "</div>",
            actionRows ? '<div class="detail-actions">' + actionRows + "</div>" : "",
            '<details class="tech-details">',
            "<summary>查看调试信息</summary>",
            '<div class="path-list">',
            fileRows || '<div class="path-row"><strong>处理文件</strong><code>当前暂无文件记录</code></div>',
            "</div>",
            "</details>",
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

    function renderSelectorSummary(target, item) {
        if (!target) {
            return;
        }

        if (!item) {
            target.innerHTML = '<p>' + api.escapeHtml(settings.selectionEmptyText) + "</p>";
            return;
        }

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
            "<span>", api.escapeHtml(item.created_at), "</span>",
            "</div></div></div>",
        ].join("");
    }

    function renderPickerCards(target, items, activeId, emptyText) {
        if (!target) {
            return;
        }

        if (!items.length) {
            target.innerHTML = '<div class="empty-panel"><p>' + api.escapeHtml(emptyText) + "</p></div>";
            return;
        }

        target.innerHTML = items.map(function renderCard(item) {
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
                "<span>", api.escapeHtml(item.created_at), "</span>",
                "</div></div></button>",
            ].join("");
        }).join("");
    }

    async function initializePage() {
        const form = document.getElementById("video-preview-form");
        const searchInput = document.getElementById("video-preview-search");
        const videoSelect = document.getElementById("video-preview-video-select");
        const selectedPanel = document.getElementById("video-preview-selected");
        const libraryPanel = document.getElementById("video-preview-library");
        const sampleFpsSelect = document.getElementById("video-preview-sample-fps");
        const status = document.getElementById("video-preview-status");
        const feedback = document.getElementById("video-preview-feedback");
        const meta = document.getElementById("video-preview-meta");
        const frameCount = document.getElementById("video-preview-frame-count");
        const sourcePlayer = document.getElementById("video-preview-source-player");
        const sourceMeta = document.getElementById("video-preview-source-meta");
        const playerMeta = document.getElementById("video-preview-player-meta");
        const playerStage = document.getElementById("video-preview-player-stage");
        const preview3DCanvas = document.getElementById("video-preview-3d-canvas");
        const preview3DMeta = document.getElementById("video-preview-3d-meta");
        const preview3DReset = document.getElementById("video-preview-3d-reset");
        const preview3DRotateLeft = document.getElementById("video-preview-3d-rotate-left");
        const preview3DRotateRight = document.getElementById("video-preview-3d-rotate-right");
        const preview3DTiltUp = document.getElementById("video-preview-3d-tilt-up");
        const preview3DTiltDown = document.getElementById("video-preview-3d-tilt-down");
        const preview3DZoomIn = document.getElementById("video-preview-3d-zoom-in");
        const preview3DZoomOut = document.getElementById("video-preview-3d-zoom-out");
        const playButton = document.getElementById("video-preview-play");
        const pauseButton = document.getElementById("video-preview-pause");
        const prevButton = document.getElementById("video-preview-prev");
        const nextButton = document.getElementById("video-preview-next");
        const progressInput = document.getElementById("video-preview-progress");
        const progressLabel = document.getElementById("video-preview-progress-label");
        const firstMeta = document.getElementById("video-preview-first-meta");
        const lastMeta = document.getElementById("video-preview-last-meta");
        const firstStage = document.getElementById("video-preview-first-stage");
        const lastStage = document.getElementById("video-preview-last-stage");
        const joints = document.getElementById("video-preview-joints");
        const trajectories = document.getElementById("video-preview-trajectories");

        if (!api || !form || !searchInput || !videoSelect || !sampleFpsSelect) {
            return;
        }

        let previewState = null;
        let renderableFrames = [];
        let previewViewport = null;
        let preview3DViewport = null;
        let currentFrameOffset = 0;
        let playTimer = null;
        let syncFrameRequest = null;
        let availableVideos = [];
        let filteredVideos = [];
        let selectedVideoId = getQueryVideoId();
        const preview3DViewState = {
            yaw: -0.52,
            pitch: 0.34,
            zoom: 1.06,
            dragging: false,
            lastClientX: 0,
            lastClientY: 0,
        };

        const initialVideoId = getQueryVideoId();
        const initialSampleFps = getQuerySampleFps();
        sampleFpsSelect.value = String(initialSampleFps);

        function reset3DView() {
            preview3DViewState.yaw = -0.52;
            preview3DViewState.pitch = 0.34;
            preview3DViewState.zoom = 1.06;
            renderPlayerFrame();
        }

        function adjust3DView(deltaYaw, deltaPitch, deltaZoom) {
            preview3DViewState.yaw += Number(deltaYaw || 0);
            preview3DViewState.pitch = Math.max(-1.1, Math.min(1.1, preview3DViewState.pitch + Number(deltaPitch || 0)));
            preview3DViewState.zoom = Math.max(0.72, Math.min(1.8, preview3DViewState.zoom + Number(deltaZoom || 0)));
            renderPlayerFrame();
        }

        function getSelectedVideo() {
            return availableVideos.find(function findVideo(item) {
                return item.id === selectedVideoId;
            }) || null;
        }

        function syncVideoSelect() {
            if (!videoSelect) {
                return;
            }

            const optionItems = filteredVideos.slice();
            const selectedVideo = getSelectedVideo();
            if (selectedVideo && !optionItems.some(function hasSelected(item) { return item.id === selectedVideo.id; })) {
                optionItems.unshift(selectedVideo);
            }

            if (!optionItems.length) {
                videoSelect.innerHTML = '<option value="">当前暂无可选视频</option>';
                videoSelect.value = "";
                videoSelect.disabled = true;
                return;
            }

            videoSelect.disabled = false;
            videoSelect.innerHTML = optionItems.map(function renderOption(item) {
                return '<option value="' + item.id + '">#' + item.id + " " + api.escapeHtml(getVideoLabel(item)) + "</option>";
            }).join("");

            if (!selectedVideo && optionItems[0]) {
                selectedVideoId = optionItems[0].id;
            }

            if (selectedVideoId) {
                videoSelect.value = String(selectedVideoId);
            }
        }

        function renderSelectorState(emptyText) {
            renderSelectorSummary(selectedPanel, getSelectedVideo());
            renderPickerCards(libraryPanel, filteredVideos, selectedVideoId, emptyText);
        }

        function applyVideoFilter() {
            filteredVideos = availableVideos.filter(function filterItem(item) {
                return matchesVideoSearch(item, searchInput.value);
            });
            syncVideoSelect();
            renderSelectorState(filteredVideos.length ? settings.selectionEmptyText : settings.filterEmptyText);
        }

        function selectVideo(videoId) {
            const matched = availableVideos.find(function matchItem(item) {
                return item.id === videoId;
            });
            if (!matched) {
                return;
            }
            selectedVideoId = matched.id;
            syncVideoSelect();
            renderSelectorState(settings.selectionEmptyText);
        }

        function stopPlayback() {
            if (playTimer) {
                window.clearInterval(playTimer);
                playTimer = null;
            }
            if (syncFrameRequest) {
                window.cancelAnimationFrame(syncFrameRequest);
                syncFrameRequest = null;
            }
            if (sourcePlayer && !sourcePlayer.paused) {
                sourcePlayer.pause();
            }
        }

        function getActiveFrame() {
            if (!renderableFrames.length) {
                return null;
            }
            return renderableFrames[currentFrameOffset] || renderableFrames[0];
        }

        function renderSnapshotStages() {
            if (!previewState) {
                renderStickman(firstStage, null, null);
                renderStickman(lastStage, null, null);
                return;
            }
            renderStickman(firstStage, buildRenderableFrame(previewState.first_frame), previewViewport);
            renderStickman(lastStage, buildRenderableFrame(previewState.last_frame), previewViewport);
        }

        function renderPlayerFrame() {
            const frame = getActiveFrame();
            renderStickman(playerStage, frame, previewViewport);
            if (settings.enable3dPreview && preview3DCanvas) {
                if (frame) {
                    renderThreeDStage(preview3DCanvas, frame, preview3DViewport, preview3DViewState, preview3DMeta);
                } else {
                    clear3DStage(preview3DCanvas, preview3DMeta, previewState ? "当前没有可用的 3D 骨架" : "等待数据");
                }
            }
            if (!frame) {
                if (sourceMeta) {
                    sourceMeta.textContent = "等待数据";
                }
                playerMeta.textContent = "等待数据";
                progressLabel.textContent = "0 / 0";
                progressInput.max = "0";
                progressInput.value = "0";
                return;
            }

            if (sourceMeta && sourcePlayer) {
                const currentTimeMs = sourcePlayer.readyState >= 1
                    ? sourcePlayer.currentTime * 1000
                    : frame.timestamp_ms;
                sourceMeta.textContent = "当前时间 " + formatTimestamp(currentTimeMs);
            }
            playerMeta.textContent = "第 " + frame.frame_index + " 帧 / " + formatTimestamp(frame.timestamp_ms);
            progressInput.max = String(Math.max(0, renderableFrames.length - 1));
            progressInput.value = String(currentFrameOffset);
            progressLabel.textContent = (currentFrameOffset + 1) + " / " + renderableFrames.length;
        }

        function stepFrame(direction) {
            if (!renderableFrames.length) {
                return;
            }

            currentFrameOffset = Math.min(
                renderableFrames.length - 1,
                Math.max(0, currentFrameOffset + direction)
            );
            if (sourcePlayer && sourcePlayer.readyState >= 1) {
                sourcePlayer.currentTime = renderableFrames[currentFrameOffset].timestamp_ms / 1000;
            }
            renderPlayerFrame();
        }

        function syncSkeletonToSourceVideo() {
            if (!sourcePlayer || !renderableFrames.length || sourcePlayer.readyState < 1) {
                return;
            }

            currentFrameOffset = findFrameOffsetByTime(renderableFrames, sourcePlayer.currentTime * 1000);
            renderPlayerFrame();
        }

        function startSourceSyncLoop() {
            if (!sourcePlayer) {
                return;
            }

            if (syncFrameRequest) {
                window.cancelAnimationFrame(syncFrameRequest);
                syncFrameRequest = null;
            }

            const tick = function tick() {
                syncSkeletonToSourceVideo();
                if (!sourcePlayer.paused && !sourcePlayer.ended) {
                    syncFrameRequest = window.requestAnimationFrame(tick);
                } else {
                    syncFrameRequest = null;
                }
            };

            syncFrameRequest = window.requestAnimationFrame(tick);
        }

        function startPlayback() {
            if (!previewState || renderableFrames.length <= 1) {
                return;
            }

            stopPlayback();

            if (sourcePlayer && sourcePlayer.currentSrc) {
                sourcePlayer.play()
                    .then(function handlePlay() {
                        startSourceSyncLoop();
                    })
                    .catch(function handlePlayError() {
                        playTimer = window.setInterval(function tick() {
                            if (!previewState || currentFrameOffset >= renderableFrames.length - 1) {
                                stopPlayback();
                                return;
                            }
                            currentFrameOffset += 1;
                            renderPlayerFrame();
                        }, Math.max(80, Math.round(1000 / Math.max(1, previewState.sample_fps || 5))));
                    });
                return;
            }

            playTimer = window.setInterval(function tick() {
                if (!previewState || currentFrameOffset >= renderableFrames.length - 1) {
                    stopPlayback();
                    return;
                }
                currentFrameOffset += 1;
                renderPlayerFrame();
            }, Math.max(80, Math.round(1000 / Math.max(1, previewState.sample_fps || 5))));
        }

        function resetPlayerState() {
            stopPlayback();
            previewState = null;
            renderableFrames = [];
            previewViewport = null;
            preview3DViewport = null;
            currentFrameOffset = 0;
            if (sourcePlayer) {
                sourcePlayer.removeAttribute("src");
                sourcePlayer.load();
            }
            if (sourceMeta) {
                sourceMeta.textContent = "等待数据";
            }
            if (settings.enable3dPreview && preview3DCanvas) {
                clear3DStage(preview3DCanvas, preview3DMeta, "等待数据");
            }
            renderSnapshotStages();
            renderPlayerFrame();
        }

        async function loadVideoLibrary() {
            const data = await api.fetchJson(settings.listApiPath);
            availableVideos = Array.isArray(data.items) ? data.items : [];

            if (!availableVideos.length) {
                selectedVideoId = null;
                filteredVideos = [];
                syncVideoSelect();
                renderSelectorState(settings.selectionEmptyText);
                status.textContent = "空列表";
                feedback.innerHTML = "<p>" + api.escapeHtml(settings.selectionEmptyText) + "</p>";
                return false;
            }

            if (!selectedVideoId || !availableVideos.some(function hasSelected(item) { return item.id === selectedVideoId; })) {
                selectedVideoId = availableVideos[0].id;
            }

            applyVideoFilter();
            return true;
        }

        async function loadPreview(videoId, sampleFps) {
            status.textContent = "加载中";
            feedback.innerHTML = "<p>" + api.escapeHtml(settings.loadingText) + "</p>";
            const data = await api.fetchJson(
                settings.apiPathPrefix + videoId + "/preview?sample_fps=" + encodeURIComponent(sampleFps)
            );
            const preview = data.preview;
            previewState = preview;
            renderableFrames = (preview.frames || []).map(buildRenderableFrame);
            previewViewport = buildViewport(renderableFrames);
            preview3DViewport = build3DViewport(renderableFrames);
            currentFrameOffset = 0;

            if (sourcePlayer) {
                sourcePlayer.pause();
                const sourceUrls = resolveSourceUrls(preview);
                if (sourceUrls.primary) {
                    sourcePlayer.setAttribute("data-source-url", sourceUrls.primary);
                    sourcePlayer.setAttribute("data-fallback-source-url", sourceUrls.fallback);
                    sourcePlayer.setAttribute("data-source-stage", "primary");
                    sourcePlayer.src = sourceUrls.primary;
                    sourcePlayer.load();
                } else {
                    sourcePlayer.removeAttribute("data-source-url");
                    sourcePlayer.removeAttribute("data-fallback-source-url");
                    sourcePlayer.removeAttribute("data-source-stage");
                    sourcePlayer.removeAttribute("src");
                    sourcePlayer.load();
                }
            }
            if (sourceMeta) {
                sourceMeta.textContent = resolveSourceUrls(preview).primary ? "正在加载视频源回放" : "视频源回放不可用";
            }

            renderMeta(meta, preview);
            frameCount.textContent = preview.frame_count + " 帧";
            firstMeta.textContent = preview.first_frame
                ? "第 " + preview.first_frame.frame_index + " 帧 / " + formatTimestamp(preview.first_frame.timestamp_ms)
                : "无可用帧";
            lastMeta.textContent = preview.last_frame
                ? "第 " + preview.last_frame.frame_index + " 帧 / " + formatTimestamp(preview.last_frame.timestamp_ms)
                : "无可用帧";
            renderSnapshotStages();
            renderPlayerFrame();
            renderList(joints, preview.tracked_joints || [], function renderJoint(item) {
                return "<li>" + api.escapeHtml(item) + "</li>";
            });
            renderList(trajectories, preview.tracked_trajectories || [], function renderTrajectory(item) {
                return "<li>" + api.escapeHtml(item) + "</li>";
            });
            if (settings.enable3dPreview && preview3DCanvas && preview3DMeta) {
                preview3DMeta.textContent = preview3DViewport
                    ? "拖拽旋转，滚轮缩放"
                    : "当前结果未提供可用的 3D 骨架";
            }

            feedback.innerHTML = (settings.successFeedbackLines || []).map(function renderLine(item) {
                return "<p>" + api.escapeHtml(item) + "</p>";
            }).join("");
            status.textContent = settings.successStatusText;
            updateUrl(videoId, sampleFps);
        }

        searchInput.addEventListener("input", function handleSearchInput() {
            applyVideoFilter();
        });

        videoSelect.addEventListener("change", function handleVideoSelectChange() {
            const nextVideoId = Number(videoSelect.value);
            if (!Number.isInteger(nextVideoId) || nextVideoId <= 0) {
                return;
            }
            selectVideo(nextVideoId);
        });

        libraryPanel.addEventListener("click", function handleLibraryClick(event) {
            const button = event.target.closest("[data-video-id]");
            if (!button) {
                return;
            }
            selectVideo(Number(button.getAttribute("data-video-id")));
        });

        playButton.addEventListener("click", function handlePlay() {
            startPlayback();
        });

        pauseButton.addEventListener("click", function handlePause() {
            stopPlayback();
        });

        prevButton.addEventListener("click", function handlePrev() {
            stopPlayback();
            stepFrame(-1);
        });

        nextButton.addEventListener("click", function handleNext() {
            stopPlayback();
            stepFrame(1);
        });

        progressInput.addEventListener("input", function handleProgressInput() {
            if (!renderableFrames.length) {
                return;
            }
            stopPlayback();
            currentFrameOffset = Number(progressInput.value) || 0;
            if (sourcePlayer && sourcePlayer.readyState >= 1) {
                sourcePlayer.currentTime = renderableFrames[currentFrameOffset].timestamp_ms / 1000;
            }
            renderPlayerFrame();
        });

        if (settings.enable3dPreview && preview3DCanvas) {
            preview3DCanvas.addEventListener("mousedown", function handleMouseDown(event) {
                preview3DViewState.dragging = true;
                preview3DViewState.lastClientX = event.clientX;
                preview3DViewState.lastClientY = event.clientY;
                preview3DCanvas.classList.add("is-dragging");
            });
            window.addEventListener("mousemove", function handleMouseMove(event) {
                if (!preview3DViewState.dragging) {
                    return;
                }
                const deltaX = event.clientX - preview3DViewState.lastClientX;
                const deltaY = event.clientY - preview3DViewState.lastClientY;
                preview3DViewState.lastClientX = event.clientX;
                preview3DViewState.lastClientY = event.clientY;
                preview3DViewState.yaw += deltaX * 0.01;
                preview3DViewState.pitch = Math.max(-1.1, Math.min(1.1, preview3DViewState.pitch + (deltaY * 0.01)));
                renderPlayerFrame();
            });
            window.addEventListener("mouseup", function handleMouseUp() {
                if (!preview3DViewState.dragging) {
                    return;
                }
                preview3DViewState.dragging = false;
                preview3DCanvas.classList.remove("is-dragging");
            });
            preview3DCanvas.addEventListener("mouseleave", function handleMouseLeave() {
                if (!preview3DViewState.dragging) {
                    return;
                }
                window.setTimeout(function releaseDragAfterLeave() {
                    preview3DViewState.dragging = false;
                    preview3DCanvas.classList.remove("is-dragging");
                }, 0);
            });
            preview3DCanvas.addEventListener("wheel", function handleWheel(event) {
                event.preventDefault();
                const zoomDelta = event.deltaY < 0 ? 0.08 : -0.08;
                preview3DViewState.zoom = Math.max(0.72, Math.min(1.8, preview3DViewState.zoom + zoomDelta));
                renderPlayerFrame();
            }, {passive: false});
            window.addEventListener("resize", function handlePreview3DResize() {
                renderPlayerFrame();
            });
        }

        if (preview3DReset) {
            preview3DReset.addEventListener("click", function handlePreview3DReset() {
                reset3DView();
            });
        }
        if (preview3DRotateLeft) {
            preview3DRotateLeft.addEventListener("click", function handleRotateLeft() {
                adjust3DView(-0.22, 0, 0);
            });
        }
        if (preview3DRotateRight) {
            preview3DRotateRight.addEventListener("click", function handleRotateRight() {
                adjust3DView(0.22, 0, 0);
            });
        }
        if (preview3DTiltUp) {
            preview3DTiltUp.addEventListener("click", function handleTiltUp() {
                adjust3DView(0, 0.14, 0);
            });
        }
        if (preview3DTiltDown) {
            preview3DTiltDown.addEventListener("click", function handleTiltDown() {
                adjust3DView(0, -0.14, 0);
            });
        }
        if (preview3DZoomIn) {
            preview3DZoomIn.addEventListener("click", function handleZoomIn() {
                adjust3DView(0, 0, 0.12);
            });
        }
        if (preview3DZoomOut) {
            preview3DZoomOut.addEventListener("click", function handleZoomOut() {
                adjust3DView(0, 0, -0.12);
            });
        }

        if (sourcePlayer) {
            sourcePlayer.addEventListener("loadedmetadata", function handleLoadedMetadata() {
                if (sourceMeta) {
                    sourceMeta.textContent = "视频时长 " + formatTimestamp(sourcePlayer.duration * 1000);
                }
                renderPlayerFrame();
            });
            sourcePlayer.addEventListener("play", startSourceSyncLoop);
            sourcePlayer.addEventListener("pause", function handlePause() {
                if (syncFrameRequest) {
                    window.cancelAnimationFrame(syncFrameRequest);
                    syncFrameRequest = null;
                }
                syncSkeletonToSourceVideo();
            });
            sourcePlayer.addEventListener("seeking", syncSkeletonToSourceVideo);
            sourcePlayer.addEventListener("ended", function handleEnded() {
                if (syncFrameRequest) {
                    window.cancelAnimationFrame(syncFrameRequest);
                    syncFrameRequest = null;
                }
                currentFrameOffset = renderableFrames.length ? renderableFrames.length - 1 : 0;
                renderPlayerFrame();
            });
            sourcePlayer.addEventListener("error", function handleSourceError() {
                const fallbackUrl = String(sourcePlayer.getAttribute("data-fallback-source-url") || "").trim();
                const currentSourceUrl = String(sourcePlayer.getAttribute("data-source-url") || "").trim();
                const sourceStage = String(sourcePlayer.getAttribute("data-source-stage") || "primary");
                if (fallbackUrl && currentSourceUrl !== fallbackUrl && sourceStage !== "fallback") {
                    sourcePlayer.pause();
                    sourcePlayer.setAttribute("data-source-url", fallbackUrl);
                    sourcePlayer.setAttribute("data-source-stage", "fallback");
                    sourcePlayer.src = fallbackUrl;
                    sourcePlayer.load();
                    if (sourceMeta) {
                        sourceMeta.textContent = "兼容源不可用，正在回退原视频";
                    }
                    return;
                }
                if (sourceMeta) {
                    sourceMeta.textContent = "视频源回放加载失败";
                }
            });
        }

        form.addEventListener("submit", async function handleSubmit(event) {
            event.preventDefault();

            const videoId = selectedVideoId;
            const sampleFps = Number(sampleFpsSelect.value);
            if (!Number.isInteger(videoId) || videoId <= 0) {
                status.textContent = "视频无效";
                feedback.innerHTML = "<p>" + api.escapeHtml(settings.invalidIdText) + "</p>";
                return;
            }

            try {
                await loadPreview(videoId, sampleFps);
            } catch (error) {
                resetPlayerState();
                status.textContent = "加载失败";
                feedback.innerHTML = "<p>" + api.escapeHtml(settings.errorPrefix + error.message) + "</p>";
                meta.innerHTML = "<p>" + api.escapeHtml(settings.metaEmptyText) + "</p>";
                frameCount.textContent = "-- 帧";
                if (sourceMeta) {
                    sourceMeta.textContent = "等待数据";
                }
                playerMeta.textContent = "等待数据";
                firstMeta.textContent = "等待数据";
                lastMeta.textContent = "等待数据";
                renderStickman(playerStage, null, null);
                if (settings.enable3dPreview && preview3DCanvas) {
                    clear3DStage(preview3DCanvas, preview3DMeta, "加载失败");
                }
                renderStickman(firstStage, null, null);
                renderStickman(lastStage, null, null);
                renderList(joints, [], function noop() { return ""; });
                renderList(trajectories, [], function noop() { return ""; });
            }
        });

        try {
            const hasVideos = await loadVideoLibrary();
            if (!hasVideos) {
                resetPlayerState();
                return;
            }
        } catch (error) {
            status.textContent = "加载失败";
            feedback.innerHTML = "<p>" + api.escapeHtml(settings.errorPrefix + error.message) + "</p>";
            renderSelectorState(settings.selectionEmptyText);
            resetPlayerState();
            return;
        }

        if (initialVideoId) {
            try {
                await loadPreview(initialVideoId, initialSampleFps);
            } catch (error) {
                resetPlayerState();
                status.textContent = "加载失败";
                feedback.innerHTML = "<p>" + api.escapeHtml(settings.errorPrefix + error.message) + "</p>";
            }
        } else {
            renderSnapshotStages();
            renderPlayerFrame();
        }
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
