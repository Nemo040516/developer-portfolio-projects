(function bootstrapHistoryPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const SCORE_BASIC_MATCH_THRESHOLD = 70;
    const SCORE_REHEARSE_THRESHOLD = 90;
    const SCORE_REVIEW_FOCUS_THRESHOLD = 95;
    const MIN_JOINT_REVIEW_DIFF = 8;
    const MIN_TRAJECTORY_REVIEW_DIFF = 0.15;
    const TIME_BUCKETS = [
        {key: "today", label: "今天更新"},
        {key: "yesterday", label: "昨天更新"},
        {key: "earlier", label: "更早记录"},
    ];
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
    const FILTER_LABELS = {
        all: "全部结果",
        success: "已完成",
        failed: "失败",
        running: "生成中",
        pending: "等待开始",
    };
    const SORT_LABELS = {
        recent: "最近更新优先",
        score_asc: "分数从低到高",
        score_desc: "分数从高到低",
    };

    const state = {
        items: [],
        filteredItems: [],
        activeId: null,
        activePhase: "overview",
        detailCache: {},
        filters: {
            keyword: "",
            status: "all",
            sort: "recent",
        },
        videoLookup: {
            standard: {},
            learner: {},
        },
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

    function resolveStatusClassName(status) {
        if (status === "success") {
            return "history-status-badge--success";
        }
        if (status === "failed") {
            return "history-status-badge--failed";
        }
        if (status === "running") {
            return "history-status-badge--running";
        }
        return "history-status-badge--pending";
    }

    function parseTimestamp(value) {
        const normalized = String(value || "").replace(" ", "T");
        const parsed = Date.parse(normalized);
        return Number.isNaN(parsed) ? 0 : parsed;
    }

    function parseDate(value) {
        const timestamp = parseTimestamp(value);
        return timestamp ? new Date(timestamp) : null;
    }

    function formatCompactDateTime(value) {
        const date = parseDate(value);
        if (!date) {
            return "--";
        }

        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        const hours = String(date.getHours()).padStart(2, "0");
        const minutes = String(date.getMinutes()).padStart(2, "0");
        return month + "-" + day + " " + hours + ":" + minutes;
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

    function sortMetricEntries(entries) {
        return entries.slice().sort(function sortEntry(left, right) {
            return Number(right[1]) - Number(left[1]);
        });
    }

    function getNumericScore(item) {
        const rawScore = item ? item.score : null;
        if (rawScore === null || rawScore === undefined || Number.isNaN(Number(rawScore))) {
            return null;
        }
        return Number(rawScore);
    }

    function hasScoredResult(item) {
        return getNumericScore(item) !== null;
    }

    function hasReviewFocus(item) {
        const score = getNumericScore(item);
        return item && item.status === "success" && score !== null && score < SCORE_REVIEW_FOCUS_THRESHOLD;
    }

    function getSignificantMetricEntries(metricMap, minValue) {
        return sortMetricEntries(Object.entries(metricMap || {})).filter(function filterEntry(entry) {
            return Number(entry[1]) >= minValue;
        });
    }

    function buildReviewFocusEntries(item) {
        const focusEntries = [];
        const seenKeys = {};

        if (!hasReviewFocus(item)) {
            return focusEntries;
        }

        (Array.isArray(item.issues) ? item.issues : []).forEach(function collectIssue(issue) {
            if (focusEntries.length >= 3) {
                return;
            }

            const issueType = String(issue && issue.issue_type || "joint");
            const issueKey = String(issue && issue.issue_key || "").trim();
            const countKey = issueType + ":" + issueKey;
            if (!issueKey || seenKeys[countKey]) {
                return;
            }

            const nextValue = Number(issue && issue.average_diff);
            if (Number.isNaN(nextValue) || nextValue <= 0) {
                return;
            }

            seenKeys[countKey] = true;
            focusEntries.push({
                type: issueType,
                key: issueKey,
                label: String(issue && issue.issue_label || humanizeMetricKey(issueKey)),
                value: nextValue,
            });
        });

        if (focusEntries.length) {
            return focusEntries;
        }

        getSignificantMetricEntries(item.joint_diffs, MIN_JOINT_REVIEW_DIFF).slice(0, 2).forEach(function addJoint(entry) {
            focusEntries.push({
                type: "joint",
                key: entry[0],
                label: humanizeMetricKey(entry[0]),
                value: Number(entry[1]),
            });
        });

        getSignificantMetricEntries(item.trajectory_diffs, MIN_TRAJECTORY_REVIEW_DIFF).slice(0, 1).forEach(function addTrajectory(entry) {
            focusEntries.push({
                type: "trajectory",
                key: entry[0],
                label: humanizeMetricKey(entry[0]),
                value: Number(entry[1]),
            });
        });

        return focusEntries;
    }

    function buildPriorityItems(item) {
        const suggestions = Array.isArray(item.suggestions) ? item.suggestions.slice() : [];
        const jointEntries = sortMetricEntries(Object.entries(item.joint_diffs || {}));
        const trajectoryEntries = sortMetricEntries(Object.entries(item.trajectory_diffs || {}));
        const items = [];

        if (suggestions.length) {
            items.push(String(suggestions[0]));
        }
        if (jointEntries.length) {
            items.push("优先纠正 " + humanizeMetricKey(jointEntries[0][0]) + "，偏差 " + api.formatNumber(jointEntries[0][1], 1));
        }
        if (trajectoryEntries.length) {
            items.push("再看 " + humanizeMetricKey(trajectoryEntries[0][0]) + "，差异 " + api.formatNumber(trajectoryEntries[0][1], 2));
        }

        return items.slice(0, 3);
    }

    function getHistorySummaryText(item) {
        if (item && item.summary_text) {
            return String(item.summary_text);
        }
        if (!item || item.status === "pending") {
            return "这次分析还没开始生成结果，当前先保留这条记录。";
        }
        if (item.status === "running") {
            return "这次分析仍在生成结果，稍后刷新就能继续查看报告。";
        }
        if (item.status === "failed") {
            return "这次分析没有成功完成，请先查看结果说明或回分析页重新处理。";
        }
        return "这次分析结果已生成，可以继续查看详情。";
    }

    function buildHistoryChip(label, value) {
        return '<span class="history-chip"><strong>' + api.escapeHtml(label) + "</strong>" + api.escapeHtml(value) + "</span>";
    }

    function getQueryHistoryId(items) {
        const params = new URLSearchParams(window.location.search);
        const rawValue = Number(params.get("history_id"));
        if (!Number.isInteger(rawValue) || rawValue <= 0) {
            return items.length ? items[0].id : null;
        }

        const matched = items.find(function matchItem(item) {
            return item.id === rawValue;
        });
        return matched ? matched.id : (items.length ? items[0].id : null);
    }

    function updateHistoryUrl(historyId) {
        const params = new URLSearchParams(window.location.search);
        if (historyId) {
            params.set("history_id", String(historyId));
        } else {
            params.delete("history_id");
        }
        const query = params.toString();
        const nextUrl = query ? "/history?" + query : "/history";
        window.history.replaceState(null, "", nextUrl);
    }

    function buildTrainingWorkbenchLink(item, includePreviewTarget) {
        if (!item) {
            return "/analysis";
        }

        const params = new URLSearchParams();
        if (item.standard_video_id) {
            params.set("standard_video_id", String(item.standard_video_id));
        }
        if (item.learner_video_id) {
            params.set("learner_video_id", String(item.learner_video_id));
        }
        if (includePreviewTarget && item.standard_video_id && item.learner_video_id) {
            params.set("preview_target", "both");
        }
        const query = params.toString();
        return query ? "/analysis?" + query : "/analysis";
    }

    function setActionLinkState(element, config) {
        if (!element) {
            return;
        }

        const nextConfig = config || {};
        const enabled = Boolean(nextConfig.enabled);
        element.href = enabled ? nextConfig.href : "#";
        element.textContent = nextConfig.label || element.textContent;
        element.classList.toggle("is-disabled", !enabled);
        element.setAttribute("aria-disabled", enabled ? "false" : "true");
        element.tabIndex = enabled ? 0 : -1;
    }

    function getVideoLabel(item, fallbackLabel, videoId) {
        if (item) {
            return item.display_name || item.original_filename || (fallbackLabel + " #" + videoId);
        }
        return fallbackLabel + " #" + videoId;
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

    function buildLookup(items) {
        return (Array.isArray(items) ? items : []).reduce(function assignLookup(lookup, item) {
            lookup[item.id] = item;
            return lookup;
        }, {});
    }

    function enrichHistoryItem(item) {
        const standardVideo = state.videoLookup.standard[item.standard_video_id] || null;
        const learnerVideo = state.videoLookup.learner[item.learner_video_id] || null;
        return Object.assign({}, item, {
            standardVideo: standardVideo,
            learnerVideo: learnerVideo,
            standard_video_label: standardVideo
                ? getVideoLabel(standardVideo, "参考视频", item.standard_video_id)
                : String(item.standard_video_label || ("参考视频 #" + item.standard_video_id)),
            learner_video_label: learnerVideo
                ? getVideoLabel(learnerVideo, "当前视频", item.learner_video_id)
                : String(item.learner_video_label || ("当前视频 #" + item.learner_video_id)),
        });
    }

    function normalizeItems(items) {
        return (Array.isArray(items) ? items : []).map(enrichHistoryItem);
    }

    function getTimeBucketKey(value) {
        const targetDate = parseDate(value);
        if (!targetDate) {
            return "earlier";
        }

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const targetDay = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate());
        const diffDays = Math.round((today.getTime() - targetDay.getTime()) / 86400000);
        if (diffDays <= 0) {
            return "today";
        }
        if (diffDays === 1) {
            return "yesterday";
        }
        return "earlier";
    }

    function countRecentPractices(items, days) {
        const anchorTimestamp = items.reduce(function findLatestTimestamp(maxTimestamp, item) {
            return Math.max(maxTimestamp, parseTimestamp(item.updated_at || item.created_at));
        }, 0);
        const anchorDate = anchorTimestamp ? new Date(anchorTimestamp) : new Date();
        const windowStart = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), anchorDate.getDate() - Math.max(0, Number(days) - 1));
        windowStart.setHours(0, 0, 0, 0);

        return items.filter(function filterRecentItem(item) {
            const targetDate = parseDate(item.updated_at || item.created_at);
            return targetDate && targetDate.getTime() >= windowStart.getTime();
        }).length;
    }

    function needsRehearsal(item) {
        const score = getNumericScore(item);
        if (item.status !== "success" || score === null) {
            return false;
        }
        return score < SCORE_REHEARSE_THRESHOLD;
    }

    function countNeedsFollowup(items) {
        return items.filter(function filterFollowupItem(item) {
            return needsRehearsal(item);
        }).length;
    }

    function getHistoryCardSignalText(item) {
        if (item.status === "failed") {
            return "建议回分析页处理";
        }
        if (item.status === "running" || item.status === "pending") {
            return "等待本轮结果";
        }
        return needsRehearsal(item) ? "建议继续分析" : "本轮结果稳定";
    }

    function resolveScoreClassName(item) {
        const score = getNumericScore(item);
        if (score === null) {
            return "history-score-pill--warning";
        }
        if (score >= SCORE_REHEARSE_THRESHOLD) {
            return "history-score-pill--success";
        }
        if (score >= SCORE_BASIC_MATCH_THRESHOLD) {
            return "history-score-pill--warning";
        }
        return "history-score-pill--danger";
    }

    function formatScoreText(item) {
        const score = getNumericScore(item);
        if (score === null) {
            return item.status === "failed" ? "结果失败" : "暂无分数";
        }
        return api.formatNumber(score, 1) + " 分";
    }

    function buildSearchText(item) {
        return [
            String(item.id || ""),
            formatStatusLabel(item.status),
            String(item.summary_text || ""),
            String(item.created_at || ""),
            String(item.updated_at || ""),
            String(item.standard_video_label || ""),
            String(item.learner_video_label || ""),
            String(item.standardVideo && item.standardVideo.original_filename || ""),
            String(item.learnerVideo && item.learnerVideo.original_filename || ""),
        ].join(" ").toLowerCase();
    }

    function matchesKeyword(item, keyword) {
        const normalizedKeyword = String(keyword || "").trim().toLowerCase();
        if (!normalizedKeyword) {
            return true;
        }
        return buildSearchText(item).indexOf(normalizedKeyword) >= 0;
    }

    function compareByRecent(left, right) {
        const timeDiff = parseTimestamp(right.updated_at || right.created_at) - parseTimestamp(left.updated_at || left.created_at);
        if (timeDiff !== 0) {
            return timeDiff;
        }
        return Number(right.id || 0) - Number(left.id || 0);
    }

    function compareByScore(left, right, direction) {
        const leftScore = getNumericScore(left);
        const rightScore = getNumericScore(right);

        if (leftScore === null && rightScore === null) {
            return compareByRecent(left, right);
        }
        if (leftScore === null) {
            return 1;
        }
        if (rightScore === null) {
            return -1;
        }

        const scoreDiff = direction === "asc" ? leftScore - rightScore : rightScore - leftScore;
        if (scoreDiff !== 0) {
            return scoreDiff;
        }
        return compareByRecent(left, right);
    }

    function sortItems(items, sortMode) {
        const nextItems = items.slice();
        if (sortMode === "score_asc") {
            nextItems.sort(function compareScoreAsc(left, right) {
                return compareByScore(left, right, "asc");
            });
            return nextItems;
        }
        if (sortMode === "score_desc") {
            nextItems.sort(function compareScoreDesc(left, right) {
                return compareByScore(left, right, "desc");
            });
            return nextItems;
        }
        nextItems.sort(compareByRecent);
        return nextItems;
    }

    function filterItems(items) {
        const filtered = items.filter(function matchItem(item) {
            const statusMatches = state.filters.status === "all" || item.status === state.filters.status;
            return statusMatches && matchesKeyword(item, state.filters.keyword);
        });
        return sortItems(filtered, state.filters.sort);
    }

    function buildBucketCounts(items) {
        const counts = {
            today: 0,
            yesterday: 0,
            earlier: 0,
        };
        items.forEach(function countItem(item) {
            counts[getTimeBucketKey(item.updated_at || item.created_at)] += 1;
        });
        return counts;
    }

    function groupItems(items) {
        const groupMap = {
            today: [],
            yesterday: [],
            earlier: [],
        };
        items.forEach(function assignItem(item) {
            groupMap[getTimeBucketKey(item.updated_at || item.created_at)].push(item);
        });

        return TIME_BUCKETS.map(function buildGroup(bucket) {
            return {
                key: bucket.key,
                label: bucket.label,
                items: groupMap[bucket.key],
            };
        }).filter(function filterGroup(group) {
            return group.items.length > 0;
        });
    }

    function getLatestProgressText(items) {
        const scoredItems = sortItems(items.filter(function filterScoredItem(item) {
            return item.status === "success" && hasScoredResult(item);
        }), "recent");

        if (scoredItems.length < 2) {
            return "最近还没有足够的完成记录可比较。";
        }

        const latestScore = getNumericScore(scoredItems[0]);
        const previousScore = getNumericScore(scoredItems[1]);
        const diff = latestScore - previousScore;
        if (Math.abs(diff) < 0.1) {
            return "最近一次和上一次基本持平。";
        }
        if (diff > 0) {
            return "最近一次比上一次提高了 " + api.formatNumber(diff, 1) + " 分。";
        }
        return "最近一次比上一次下降了 " + api.formatNumber(Math.abs(diff), 1) + " 分。";
    }

    function getTopRepeatedIssueText(items) {
        const issueCounts = {};
        let orderIndex = 0;

        items.forEach(function collectIssue(item) {
            const itemKeys = {};
            buildReviewFocusEntries(item).slice(0, 2).forEach(function countFocusEntry(entry) {
                const issueKey = entry.type + ":" + entry.key;
                if (itemKeys[issueKey]) {
                    return;
                }
                itemKeys[issueKey] = true;

                if (!issueCounts[issueKey]) {
                    issueCounts[issueKey] = {
                        label: entry.label,
                        count: 0,
                        order: orderIndex,
                    };
                    orderIndex += 1;
                }
                issueCounts[issueKey].count += 1;
            });
        });

        const rankedIssues = Object.values(issueCounts).sort(function sortIssue(left, right) {
            const countDiff = Number(right.count) - Number(left.count);
            if (countDiff !== 0) {
                return countDiff;
            }
            return Number(left.order) - Number(right.order);
        });
        const topEntry = rankedIssues[0];

        if (!topEntry || topEntry.count < 2) {
            return "暂无明显重复问题";
        }

        const topLabels = rankedIssues
            .filter(function filterTopIssue(entry) {
                return entry.count === topEntry.count;
            })
            .slice(0, 2)
            .map(function mapLabel(entry) {
                return entry.label;
            });

        if (topLabels.length > 1) {
            return topLabels.join(" / ") + "（各 " + topEntry.count + " 次）";
        }
        return topEntry.label + "（" + topEntry.count + " 次）";
    }

    function getNeedsFollowupText(items) {
        const followupCount = countNeedsFollowup(items);
        if (!followupCount) {
            return "暂无";
        }
        return followupCount + " 条";
    }

    function getRecentScoredItems(items, limit) {
        return items
            .filter(function filterScoredItem(item) {
                return item.status === "success" && hasScoredResult(item);
            })
            .sort(compareByRecent)
            .slice(0, Math.max(1, Number(limit) || 5));
    }

    function resolveTrendClassName(score) {
        if (score >= SCORE_REHEARSE_THRESHOLD) {
            return "trend-bar--success";
        }
        if (score >= SCORE_BASIC_MATCH_THRESHOLD) {
            return "trend-bar--warning";
        }
        return "trend-bar--danger";
    }

    function buildScoreBandLabel(score) {
        if (score === null) {
            return "等待结果";
        }
        if (score >= SCORE_REVIEW_FOCUS_THRESHOLD) {
            return "稳定段";
        }
        if (score >= SCORE_REHEARSE_THRESHOLD) {
            return "可继续压实";
        }
        if (score >= SCORE_BASIC_MATCH_THRESHOLD) {
            return "需要回看细节";
        }
        return "问题回看段";
    }

    function buildRecentDeltaText(scoredItems) {
        if (scoredItems.length < 2) {
            return "等待下一次对比";
        }

        const latestScore = getNumericScore(scoredItems[0]);
        const previousScore = getNumericScore(scoredItems[1]);
        const diff = latestScore - previousScore;
        if (Math.abs(diff) < 0.1) {
            return "和上一次基本持平";
        }
        if (diff > 0) {
            return "比上一次提高 " + api.formatNumber(diff, 1) + " 分";
        }
        return "比上一次下降 " + api.formatNumber(Math.abs(diff), 1) + " 分";
    }

    function renderTrendRack(items) {
        return items.map(function renderTrendItem(item) {
            const score = getNumericScore(item);
            const clampedLevel = Math.max(18, Math.min(100, score));
            return [
                '<div class="trend-bar ',
                resolveTrendClassName(score),
                '">',
                '<span class="trend-bar__value">',
                api.formatNumber(score, 1),
                "</span>",
                '<span class="trend-bar__track"><span class="trend-bar__fill" style="height:',
                String(clampedLevel),
                '%"></span></span>',
                '<span class="trend-bar__label">#',
                api.escapeHtml(String(item.id)),
                "</span>",
                "</div>",
            ].join("");
        }).join("");
    }

    function renderFlightDeck(items) {
        const recentScoredItems = getRecentScoredItems(items, 5);
        if (!recentScoredItems.length) {
            return '<div class="history-flight-deck__empty">当前筛选结果里还没有带分数的完成记录，趋势带会在结果生成后自动出现。</div>';
        }

        const chartItems = recentScoredItems.slice().reverse();
        const scores = recentScoredItems.map(getNumericScore);
        const latestScore = scores[0];
        const averageScore = scores.reduce(function sumScore(total, value) {
            return total + value;
        }, 0) / scores.length;
        const scoreRange = Math.max.apply(null, scores) - Math.min.apply(null, scores);

        return [
            '<div class="telemetry-board telemetry-board--dark console-board console-board--dark">',
            '<div class="telemetry-board__header console-board__header">',
            '<div><p class="telemetry-board__eyebrow console-board__eyebrow">当前筛选</p><strong>当前筛选结果的最近 5 次走势</strong><span>快速判断最近这一段是在稳住，还是还在反复。</span></div>',
            '<span class="telemetry-chip telemetry-chip--accent"><strong>当前段位</strong>',
            api.escapeHtml(buildScoreBandLabel(latestScore)),
            "</span></div>",
            '<div class="trend-rack" aria-label="当前筛选结果的最近 5 次分数走势">',
            renderTrendRack(chartItems),
            "</div>",
            '<div class="telemetry-board__meta console-board__meta">',
            '<span class="telemetry-chip"><strong>最新变化</strong>',
            api.escapeHtml(buildRecentDeltaText(recentScoredItems)),
            "</span>",
            '<span class="telemetry-chip"><strong>最近均分</strong>',
            api.formatNumber(averageScore, 1),
            " 分</span>",
            '<span class="telemetry-chip"><strong>波动区间</strong>',
            api.formatNumber(scoreRange, 1),
            " 分</span>",
            "</div></div>",
        ].join("");
    }

    function renderStatusBadge(status) {
        return [
            '<span class="badge history-status-badge ',
            resolveStatusClassName(status),
            '">',
            api.escapeHtml(formatStatusLabel(status)),
            "</span>",
        ].join("");
    }

    function renderBucketOverview(items) {
        if (!items.length) {
            return '<span class="history-bucket-pill">当前没有符合筛选条件的记录</span>';
        }

        const counts = buildBucketCounts(items);
        return TIME_BUCKETS.map(function renderBucket(bucket) {
            return [
                '<span class="history-bucket-pill"><strong>',
                api.escapeHtml(bucket.label),
                "</strong>",
                counts[bucket.key],
                " 条</span>",
            ].join("");
        }).join("");
    }

    function renderOverview(items) {
        if (!items.length) {
            return [
                '<div class="summary-tile"><strong>当前可见记录</strong><span>0</span></div>',
                '<div class="summary-tile"><strong>最近一周结果</strong><span>0 条</span></div>',
                '<div class="summary-tile"><strong>平均得分</strong><span>暂无分数</span></div>',
                '<div class="summary-tile"><strong>当前最好</strong><span>暂无分数</span></div>',
                '<div class="summary-tile"><strong>建议回看</strong><span>暂无</span></div>',
                '<div class="summary-tile"><strong>反复卡住</strong><span>暂无明显重复问题</span></div>',
            ].join("");
        }

        const recentPracticeCount = countRecentPractices(items, 7);
        const scoredItems = items.filter(function filterScoredItem(item) {
            return item.status === "success" && hasScoredResult(item);
        });
        const averageScoreText = scoredItems.length
            ? api.formatNumber(
                scoredItems.reduce(function sumScore(total, item) {
                    return total + getNumericScore(item);
                }, 0) / scoredItems.length,
                1
            ) + " 分"
            : "暂无分数";
        const bestScoreText = scoredItems.length
            ? api.formatNumber(
                scoredItems.reduce(function findBest(bestScore, item) {
                    return Math.max(bestScore, getNumericScore(item));
                }, getNumericScore(scoredItems[0])),
                1
            ) + " 分"
            : "暂无分数";

        return [
            '<div class="summary-tile summary-tile--signal"><strong>当前可见记录</strong><span>',
            items.length,
            "</span></div>",
            '<div class="summary-tile"><strong>最近一周结果</strong><span>',
            recentPracticeCount,
            " 条</span></div>",
            '<div class="summary-tile"><strong>平均得分</strong><span>',
            averageScoreText,
            "</span></div>",
            '<div class="summary-tile"><strong>当前最好</strong><span>',
            bestScoreText,
            "</span></div>",
            '<div class="summary-tile"><strong>建议回看</strong><span>',
            api.escapeHtml(getNeedsFollowupText(items)),
            "</span></div>",
            '<div class="summary-tile"><strong>反复卡住</strong><span>',
            api.escapeHtml(getTopRepeatedIssueText(items)),
            "</span></div>",
        ].join("");
    }

    function renderFilterSummary() {
        const followupCount = countNeedsFollowup(state.filteredItems);
        const repeatedIssueText = getTopRepeatedIssueText(state.filteredItems);
        const keywordText = state.filters.keyword
            ? '搜索 "' + api.escapeHtml(state.filters.keyword.trim()) + '"'
            : "未启用关键词";
        return [
            "当前显示 ",
            state.filteredItems.length,
            " / ",
            state.items.length,
            " 条，状态：",
            api.escapeHtml(FILTER_LABELS[state.filters.status] || FILTER_LABELS.all),
            "，排序：",
            api.escapeHtml(SORT_LABELS[state.filters.sort] || SORT_LABELS.recent),
            "，",
            keywordText,
            "。 ",
            api.escapeHtml(getLatestProgressText(state.filteredItems)),
            " 建议继续回看的有 ",
            followupCount,
            " 条。",
            repeatedIssueText === "暂无明显重复问题"
                ? " 暂未形成反复卡住的部位。"
                : " 反复卡在 " + api.escapeHtml(repeatedIssueText) + "。",
        ].join("");
    }

    function renderVideoCover(item, emptyText, className) {
        const nextClassName = className ? "video-cover " + className : "video-cover";
        if (item && item.thumbnail_url) {
            return [
                '<div class="', nextClassName, '">',
                '<img src="', api.escapeHtml(item.thumbnail_url), '" alt="', api.escapeHtml(getVideoLabel(item, "视频", item.id) + " 封面"), '" loading="lazy">',
                "</div>",
            ].join("");
        }

        return [
            '<div class="', nextClassName, ' is-empty">',
            '<div class="video-cover__fallback">', api.escapeHtml(emptyText), "</div>",
            "</div>",
        ].join("");
    }

    function renderHistoryCard(item, activeId) {
        const activeClassName = item.id === activeId ? " is-active" : "";
        const summaryText = getHistorySummaryText(item);
        const updatedLabel = formatCompactDateTime(item.updated_at || item.created_at);
        return [
            '<button class="data-card history-card', activeClassName, '" type="button" data-history-id="', item.id, '">',
            '<div class="data-card__title"><strong>结果记录 #', item.id, "</strong>",
            renderStatusBadge(item.status),
            "</div>",
            '<div class="history-card__pair">',
            '<strong>', api.escapeHtml(item.standard_video_label), "</strong>",
            '<span class="history-card__pair-text">对照</span>',
            '<strong>', api.escapeHtml(item.learner_video_label), "</strong>",
            "</div>",
            '<div class="data-card__meta">',
            '<span class="history-card__meta-stamp">更新 ', api.escapeHtml(updatedLabel), "</span>",
            '<span class="history-card__meta-stamp">采样 ', api.escapeHtml(item.sample_fps), " FPS</span>",
            "</div>",
            '<div class="history-card__signals">',
            '<span class="history-score-pill ', resolveScoreClassName(item), '">',
            api.escapeHtml(formatScoreText(item)),
            "</span>",
            '<span class="history-pair-tag">',
            api.escapeHtml(getHistoryCardSignalText(item)),
            "</span>",
            "</div>",
            '<p class="muted-text history-card__summary">', api.escapeHtml(summaryText), "</p>",
            "</button>",
        ].join("");
    }

    function renderList(items, activeId) {
        if (!items.length) {
            return [
                '<div class="empty-panel history-empty-hint">',
                "<p>当前筛选条件下没有匹配的结果记录。</p>",
                "<p>你可以清空筛选，或者换一个视频名、编号、时间和摘要关键词再试。</p>",
                "</div>",
            ].join("");
        }

        return [
            '<div class="history-group-list">',
            groupItems(items).map(function renderGroup(group) {
                return [
                    '<section class="history-group">',
                    '<div class="history-group__header"><strong>',
                    api.escapeHtml(group.label),
                    "</strong><span>",
                    group.items.length,
                    " 条</span></div>",
                    '<div class="history-group__items">',
                    group.items.map(function renderItem(item) {
                        return renderHistoryCard(item, activeId);
                    }).join(""),
                    "</div></section>",
                ].join("");
            }).join(""),
            "</div>",
        ].join("");
    }

    function renderMaterialCard(video, eyebrow, fallbackLabel, videoId) {
        const label = getVideoLabel(video, fallbackLabel, videoId);
        const metaParts = buildMediaMetaParts(video);
        return [
            '<div class="history-material-card selector-summary selector-summary--media">',
            renderVideoCover(video, "封面待生成", "video-cover--detail"),
            '<div class="selector-summary__content">',
            '<span class="history-material-card__eyebrow">', api.escapeHtml(eyebrow), "</span>",
            '<div class="selector-summary__head"><strong>', api.escapeHtml(label), "</strong>",
            '<span class="badge">#', api.escapeHtml(videoId), "</span></div>",
            '<div class="selector-summary__meta">',
            metaParts.primary.map(function renderPrimaryPart(part) {
                return "<span>" + api.escapeHtml(part) + "</span>";
            }).join(""),
            "</div>",
            metaParts.secondary.length
                ? '<div class="history-material-card__submeta">' + metaParts.secondary.map(function renderSecondaryPart(part) {
                    return "<span>" + api.escapeHtml(part) + "</span>";
                }).join("") + "</div>"
                : "",
            "</div></div>",
        ].join("");
    }

    function renderDiffList(items, formatter) {
        if (!items.length) {
            return "<li>当前暂无差异明细</li>";
        }
        return items.map(formatter).join("");
    }

    function renderDetail(item) {
        const openReviewLink = "/review?analysis_id=" + encodeURIComponent(item.id);
        const replayLink = "/analysis?standard_video_id="
            + encodeURIComponent(item.standard_video_id)
            + "&learner_video_id="
            + encodeURIComponent(item.learner_video_id)
            + "&preview_target=both";
        const jointEntries = sortMetricEntries(Object.entries(item.joint_diffs || {}));
        const trajectoryEntries = sortMetricEntries(Object.entries(item.trajectory_diffs || {}));
        const priorityItems = buildPriorityItems(item);
        const focusEntries = buildReviewFocusEntries(item).slice(0, 2);
        const summaryText = getHistorySummaryText(item);

        return [
            '<div class="history-selection-shell">',
            '<section class="history-selection-summary">',
            '<div class="history-selection-summary__head"><div>',
            '<p class="history-selection-summary__eyebrow">已选记录</p>',
            "<h3>结果记录 #", item.id, "</h3>",
            '<p class="muted-text">', api.escapeHtml(summaryText), "</p>",
            "</div>",
            renderStatusBadge(item.status),
            "</div>",
            '<div class="history-chip-row">',
            buildHistoryChip("整体评分", formatScoreText(item)),
            buildHistoryChip("采样", String(item.sample_fps) + " FPS"),
            buildHistoryChip("记录状态", "已保存"),
            buildHistoryChip("最近更新", formatCompactDateTime(item.updated_at || item.created_at)),
            "</div></section>",
            '<div class="result-group"><strong>本次使用素材</strong>',
            '<div class="history-material-grid">',
            renderMaterialCard(item.standardVideo, "参考视频", "参考视频", item.standard_video_id),
            renderMaterialCard(item.learnerVideo, "当前视频", "当前视频", item.learner_video_id),
            "</div></div>",
            '<section id="history-anchor-next" class="history-decision-stage"><strong>下一步怎么走</strong>',
            '<div class="history-decision-grid console-decision-grid">',
            '<a class="history-decision-card history-decision-card--primary console-decision-card console-decision-card--primary" data-history-next="true" href="', openReviewLink, '">',
            '<span class="history-decision-card__eyebrow console-decision-card__eyebrow">查看报告</span>',
            "<strong>打开结果报告</strong>",
            '<span class="console-decision-card__note">继续看双视频、建议定位和差异细项。</span>',
            "</a>",
            '<a class="history-decision-card console-decision-card" data-history-next="true" href="', replayLink, '">',
            '<span class="history-decision-card__eyebrow console-decision-card__eyebrow">继续分析</span>',
            "<strong>带着这组视频继续分析</strong>",
            '<span class="console-decision-card__note">直接回分析页，沿用当前素材继续看或重新评分。</span>',
            "</a>",
            '<a class="history-decision-card console-decision-card" data-history-next="true" href="/session">',
            '<span class="history-decision-card__eyebrow console-decision-card__eyebrow">重新开始</span>',
            "<strong>重新导入视频</strong>",
            '<span class="console-decision-card__note">开始一轮新的分析，不沿用当前素材。</span>',
            "</a>",
            "</div></section>",
            '<div class="history-note-grid">',
            '<article class="history-note-card"><strong>这次摘要</strong>',
            '<p class="muted-text">', api.escapeHtml(summaryText), "</p>",
            '<div class="history-chip-row">',
            '<span class="history-pair-tag">关节 ', jointEntries.length, ' 项</span>',
            '<span class="history-pair-tag">轨迹 ', trajectoryEntries.length, ' 项</span>',
            '<span class="history-pair-tag">建议 ', (Array.isArray(item.suggestions) ? item.suggestions.length : 0), ' 条</span>',
            "</div>",
            "</article>",
            '<article class="history-note-card"><strong>这次先看</strong><ul class="feature-list">',
            renderDiffList(priorityItems, function formatPriorityItem(entry) {
                return "<li>" + api.escapeHtml(entry) + "</li>";
            }),
            "</ul>",
            focusEntries.length
                ? '<div class="history-chip-row">' + focusEntries.map(function formatFocusEntry(entry) {
                    const metricValue = entry.type === "trajectory"
                        ? api.formatNumber(entry.value, 2)
                        : api.formatNumber(entry.value, 1);
                    return buildHistoryChip(entry.label, metricValue);
                }).join("") + "</div>"
                : "",
            "</article></div></div>",
        ].join("");
    }

    function renderDetailPlaceholder(title, bodyText) {
        return [
            '<div class="empty-panel history-empty-hint">',
            "<p>",
            api.escapeHtml(title),
            "</p><p>",
            api.escapeHtml(bodyText),
            "</p></div>",
        ].join("");
    }

    function syncPhaseState(elements) {
        const readiness = {
            overview: state.items.length > 0,
            filter: state.items.length > 0,
            select: Boolean(state.activeId),
            next: Boolean(state.activeId && state.detailCache[state.activeId]),
        };

        elements.phaseButtons.forEach(function syncPhaseButton(button) {
            const anchorKey = button.getAttribute("data-history-anchor");
            button.classList.toggle("is-active", anchorKey === state.activePhase);
            button.classList.toggle("is-done", readiness[anchorKey] && anchorKey !== state.activePhase);
            button.classList.toggle("is-disabled", !readiness[anchorKey] && anchorKey !== "overview");
        });
    }

    function setActivePhase(elements, anchorKey) {
        state.activePhase = String(anchorKey || "overview");
        syncPhaseState(elements);
    }

    function syncFilterControls(elements) {
        elements.searchInput.value = state.filters.keyword;
        elements.statusFilter.value = state.filters.status;
        elements.sortOrder.value = state.filters.sort;
        const hasActiveFilter = Boolean(state.filters.keyword.trim()) || state.filters.status !== "all" || state.filters.sort !== "recent";
        elements.clearButton.disabled = !hasActiveFilter;
    }

    function syncCommandActions(elements) {
        const activeItem = state.activeId
            ? state.items.find(function findItem(item) {
                return item.id === state.activeId;
            }) || null
            : null;
        const hasRecords = state.items.length > 0;
        const hasActiveItem = Boolean(activeItem);

        setActionLinkState(elements.startPracticeLink, {
            enabled: true,
            href: "/session",
            label: hasRecords ? "重新导入视频" : "先导入第一组视频",
        });

        setActionLinkState(elements.adjustMaterialsLink, {
            enabled: hasActiveItem,
            href: hasActiveItem ? "/review?analysis_id=" + encodeURIComponent(activeItem.id) : "#",
            label: hasActiveItem ? "打开已选结果" : "先选一条再打开结果",
        });
    }

    function renderPage(elements) {
        elements.overview.innerHTML = renderOverview(state.filteredItems);
        elements.groupOverview.innerHTML = renderBucketOverview(state.filteredItems);
        elements.flightDeck.innerHTML = renderFlightDeck(state.filteredItems);
        elements.filterSummary.innerHTML = renderFilterSummary();
        elements.list.innerHTML = renderList(state.filteredItems, state.activeId);

        if (!state.items.length) {
            elements.list.innerHTML = [
                '<div class="empty-panel history-empty-hint">',
                "<p>当前还没有可查看的结果记录。</p>",
                "<p>先完成一次动作比对，这里才会开始累计最终结果。</p>",
                "</div>",
            ].join("");
            elements.groupOverview.innerHTML = '<span class="history-bucket-pill">当前暂无历史记录</span>';
            elements.flightDeck.innerHTML = '<div class="history-flight-deck__empty">当前还没有可用于查看的结果趋势。</div>';
            elements.filterSummary.textContent = "当前还没有结果记录，后续完成分析后会自动累计。";
            elements.status.textContent = "空列表";
            elements.filterStatus.textContent = "空列表";
            elements.overviewStatus.textContent = "空列表";
            elements.detailStatus.textContent = "等待选择";
            elements.detail.innerHTML = renderDetailPlaceholder(
                "当前还没有可查看的结果记录。",
                "完成一次动作比对后，这里会自动保留最终结果，方便后续回看和横向比较。"
            );
            syncFilterControls(elements);
            syncCommandActions(elements);
            setActivePhase(elements, "overview");
            return;
        }

        elements.filterStatus.textContent = "筛选已更新";
        elements.overviewStatus.textContent = "已汇总";
        elements.status.textContent = state.filteredItems.length
            ? "当前展示 " + state.filteredItems.length + " 条"
            : "筛选为空";

        if (!state.filteredItems.length) {
            elements.detailStatus.textContent = "筛选为空";
            elements.detail.innerHTML = renderDetailPlaceholder(
                "当前筛选结果为空。",
                "可以清空筛选，或换个关键词和状态组合。"
            );
        }

        syncFilterControls(elements);
        syncCommandActions(elements);
        syncPhaseState(elements);
    }

    function ensureActiveId() {
        if (!state.filteredItems.length) {
            state.activeId = null;
            updateHistoryUrl(null);
            return;
        }

        const matched = state.filteredItems.find(function findItem(item) {
            return item.id === state.activeId;
        });
        if (matched) {
            updateHistoryUrl(state.activeId);
            return;
        }

        state.activeId = state.filteredItems[0].id;
        updateHistoryUrl(state.activeId);
    }

    function applyFilters(elements) {
        state.filteredItems = filterItems(state.items);
        ensureActiveId();
        renderPage(elements);
        setActivePhase(elements, state.items.length ? "filter" : "overview");
        if (state.activeId) {
            loadDetail(state.activeId, elements);
        }
    }

    function loadDetail(historyId, elements) {
        if (!historyId) {
            elements.detailStatus.textContent = "等待选择";
            elements.detail.innerHTML = renderDetailPlaceholder(
                "请选择一条结果记录。",
                "选中左侧一条记录后，这里会展示本次摘要、结果去向，以及进入完整报告页的入口。"
            );
            setActivePhase(elements, state.items.length ? "filter" : "overview");
            return;
        }

        const cached = state.detailCache[historyId];
        if (cached) {
            elements.detailStatus.textContent = formatStatusLabel(cached.status);
            elements.detail.innerHTML = renderDetail(cached);
            setActivePhase(elements, "select");
            return;
        }

        elements.detailStatus.textContent = "加载中";
        elements.detail.innerHTML = renderDetailPlaceholder(
            "正在加载这次结果详情。",
            "系统正在读取本次结果摘要和决策入口，请稍候。"
        );
        setActivePhase(elements, "select");

        api.fetchJson("/api/history/" + historyId)
            .then(function assignDetail(data) {
                const detail = enrichHistoryItem(data.history || {});
                state.detailCache[historyId] = detail;
                if (state.activeId !== historyId) {
                    return;
                }
                elements.detailStatus.textContent = formatStatusLabel(detail.status);
                elements.detail.innerHTML = renderDetail(detail);
                syncPhaseState(elements);
            })
            .catch(function handleDetailError() {
                if (state.activeId !== historyId) {
                    return;
                }
                elements.detailStatus.textContent = "加载失败";
                elements.detail.innerHTML = renderDetailPlaceholder(
                    "当前无法加载这次结果详情。",
                    "请检查记录是否完整，或稍后再试。"
                );
                syncPhaseState(elements);
            });
    }

    function bindEvents(elements) {
        elements.searchInput.addEventListener("input", function handleSearchInput() {
            state.filters.keyword = elements.searchInput.value;
            applyFilters(elements);
        });

        elements.statusFilter.addEventListener("change", function handleStatusChange() {
            state.filters.status = elements.statusFilter.value;
            applyFilters(elements);
        });

        elements.sortOrder.addEventListener("change", function handleSortChange() {
            state.filters.sort = elements.sortOrder.value;
            applyFilters(elements);
        });

        elements.clearButton.addEventListener("click", function handleClear() {
            state.filters.keyword = "";
            state.filters.status = "all";
            state.filters.sort = "recent";
            applyFilters(elements);
        });

        elements.list.addEventListener("click", function handleListClick(event) {
            const button = event.target.closest("[data-history-id]");
            if (!button) {
                return;
            }

            state.activeId = Number(button.getAttribute("data-history-id"));
            renderPage(elements);
             setActivePhase(elements, "select");
            loadDetail(state.activeId, elements);
        });

        elements.detail.addEventListener("click", function handleDetailClick(event) {
            const link = event.target.closest("[data-history-next]");
            if (!link) {
                return;
            }
            setActivePhase(elements, "next");
        });

        elements.phaseButtons.forEach(function bindPhaseButton(button) {
            button.addEventListener("click", function handlePhaseClick() {
                const anchorKey = button.getAttribute("data-history-anchor");
                let target = document.getElementById("history-anchor-" + anchorKey);
                if (!target && anchorKey === "next") {
                    target = document.getElementById("history-anchor-next") || document.getElementById("history-anchor-select");
                }
                setActivePhase(elements, anchorKey);
                if (target) {
                    target.scrollIntoView({behavior: "smooth", block: "start"});
                }
            });
        });
    }

    function initializePage() {
        const elements = {
            phaseButtons: Array.from(document.querySelectorAll("[data-history-anchor]")),
            list: document.getElementById("history-list"),
            detail: document.getElementById("history-detail"),
            status: document.getElementById("history-status"),
            overview: document.getElementById("history-overview"),
            overviewStatus: document.getElementById("history-overview-status"),
            filterStatus: document.getElementById("history-filter-status"),
            filterSummary: document.getElementById("history-filter-summary"),
            groupOverview: document.getElementById("history-group-overview"),
            flightDeck: document.getElementById("history-flight-deck"),
            startPracticeLink: document.getElementById("history-start-practice-link"),
            adjustMaterialsLink: document.getElementById("history-adjust-materials-link"),
            searchInput: document.getElementById("history-search"),
            statusFilter: document.getElementById("history-status-filter"),
            sortOrder: document.getElementById("history-sort-order"),
            clearButton: document.getElementById("history-clear-filters"),
            detailStatus: document.getElementById("history-detail-status"),
        };

        if (!api || Object.values(elements).some(function hasMissingElement(value) { return !value; })) {
            return;
        }

        elements.status.textContent = "加载中";
        elements.overviewStatus.textContent = "加载中";
        elements.filterStatus.textContent = "加载中";
        elements.detailStatus.textContent = "等待选择";
        syncPhaseState(elements);

        Promise.all([
            api.fetchJson("/api/history"),
            api.fetchJson("/api/videos/standard").catch(function fallbackStandard() {
                return {items: []};
            }),
            api.fetchJson("/api/videos/learner").catch(function fallbackLearner() {
                return {items: []};
            }),
        ])
            .then(function assignData(results) {
                const historyData = results[0] || {};
                const standardData = results[1] || {};
                const learnerData = results[2] || {};

                state.videoLookup.standard = buildLookup(standardData.items);
                state.videoLookup.learner = buildLookup(learnerData.items);
                state.items = normalizeItems(historyData.items);
                state.activeId = getQueryHistoryId(state.items);

                applyFilters(elements);
            })
            .catch(function handleListError() {
                elements.list.innerHTML = '<div class="empty-panel"><p>结果记录列表加载失败，请稍后重试。</p></div>';
                elements.detail.innerHTML = renderDetailPlaceholder(
                    "当前无法展示练习详情。",
                    "历史记录接口暂时不可用，请稍后再试。"
                );
                elements.overview.innerHTML = [
                    '<div class="summary-tile"><strong>当前可见记录</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>最近一周练习</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>平均得分</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>当前最好</strong><span>加载失败</span></div>',
                    '<div class="summary-tile"><strong>建议再练</strong><span>加载失败</span></div>',
                    '<div class="summary-tile"><strong>反复卡住</strong><span>加载失败</span></div>',
                ].join("");
                elements.status.textContent = "加载失败";
                elements.overviewStatus.textContent = "加载失败";
                elements.filterStatus.textContent = "加载失败";
                elements.detailStatus.textContent = "加载失败";
                elements.groupOverview.innerHTML = '<span class="history-bucket-pill">当前无法汇总分组信息</span>';
                elements.flightDeck.innerHTML = '<div class="history-flight-deck__empty">当前无法汇总最近走势，请稍后刷新页面。</div>';
                elements.filterSummary.textContent = "当前无法读取历史记录，请稍后刷新页面。";
                syncFilterControls(elements);
            });

        bindEvents(elements);
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
