(function bootstrapHomePage(window, document) {
    "use strict";

    const api = window.MotionApiClient;
    const SCORE_BASIC_MATCH_THRESHOLD = 70;
    const SCORE_REHEARSE_THRESHOLD = 90;

    function parseTimestamp(value) {
        const normalized = String(value || "").replace(" ", "T");
        const parsed = Date.parse(normalized);
        return Number.isNaN(parsed) ? 0 : parsed;
    }

    function compareByRecent(left, right) {
        return parseTimestamp(right.updated_at || right.created_at) - parseTimestamp(left.updated_at || left.created_at);
    }

    function buildRepresentativeSignature(item) {
        const score = getNumericScore(item);
        const roundedScore = score === null ? "na" : score.toFixed(1);
        return [
            String(item && item.status || ""),
            String(item && item.standard_video_id || ""),
            String(item && item.learner_video_id || ""),
            roundedScore,
        ].join(":");
    }

    function getNumericScore(item) {
        const rawScore = item ? item.score : null;
        if (rawScore === null || rawScore === undefined || Number.isNaN(Number(rawScore))) {
            return null;
        }
        return Number(rawScore);
    }

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

    function getVideoLabel(item, fallbackLabel, videoId) {
        if (item) {
            return item.display_name || item.original_filename || (fallbackLabel + " #" + videoId);
        }
        return fallbackLabel + " #" + videoId;
    }

    function buildLookup(items) {
        return (Array.isArray(items) ? items : []).reduce(function assignLookup(lookup, item) {
            lookup[item.id] = item;
            return lookup;
        }, {});
    }

    function countRecentPractices(items, days) {
        const latestTimestamp = items.reduce(function findLatest(maxTimestamp, item) {
            return Math.max(maxTimestamp, parseTimestamp(item.updated_at || item.created_at));
        }, 0);
        const anchorDate = latestTimestamp ? new Date(latestTimestamp) : new Date();
        const windowStart = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), anchorDate.getDate() - Math.max(0, Number(days) - 1));
        windowStart.setHours(0, 0, 0, 0);

        return items.filter(function filterItem(item) {
            return parseTimestamp(item.updated_at || item.created_at) >= windowStart.getTime();
        }).length;
    }

    function countFollowup(items) {
        return items.filter(function filterItem(item) {
            const score = getNumericScore(item);
            return item.status === "success" && score !== null && score < SCORE_REHEARSE_THRESHOLD;
        }).length;
    }

    function getRepresentativeScoredItems(items, limit) {
        const nextLimit = Math.max(1, Number(limit) || 5);
        const uniqueItems = [];
        const seenSignatures = {};

        items
            .filter(function filterScoredItem(item) {
                return item.status === "success" && getNumericScore(item) !== null;
            })
            .sort(compareByRecent)
            .forEach(function collectItem(item) {
                const signature = buildRepresentativeSignature(item);
                if (seenSignatures[signature] || uniqueItems.length >= nextLimit) {
                    return;
                }
                seenSignatures[signature] = true;
                uniqueItems.push(item);
            });

        return uniqueItems;
    }

    function buildProgressNote(items) {
        const scoredItems = getRepresentativeScoredItems(items, 5);

        if (scoredItems.length < 2) {
            return "当前代表性样本还不够多，先继续补 1 到 2 条不同结果，再看趋势更稳。";
        }

        const latestScore = getNumericScore(scoredItems[0]);
        const previousScore = getNumericScore(scoredItems[1]);
        const diff = latestScore - previousScore;
        if (Math.abs(diff) < 0.1) {
            return "最近一次和上一次基本持平，下一步更值得回看动作细节而不是只看分数。";
        }
        if (diff > 0) {
            return "最近一次比上一次提高了 " + api.formatNumber(diff, 1) + " 分。";
        }
        return "最近一次比上一次下降了 " + api.formatNumber(Math.abs(diff), 1) + " 分，建议先回看最新结果。";
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
        if (score >= 95) {
            return "稳定段";
        }
        if (score >= SCORE_REHEARSE_THRESHOLD) {
            return "可继续压实";
        }
        if (score >= SCORE_BASIC_MATCH_THRESHOLD) {
            return "需要继续盯细节";
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

    function renderTelemetryPanel(items) {
        const recentScoredItems = getRepresentativeScoredItems(items, 5);
        if (!recentScoredItems.length) {
            return '<div class="home-telemetry__empty">至少完成一次带分数结果后，这里才会出现近期趋势判断。</div>';
        }

        const chartItems = recentScoredItems.slice().reverse();
        const scores = recentScoredItems.map(getNumericScore);
        const latestScore = scores[0];
        const recentAverage = scores.reduce(function sumScore(total, value) {
            return total + value;
        }, 0) / scores.length;
        const scoreRange = Math.max.apply(null, scores) - Math.min.apply(null, scores);

        return [
            '<div class="telemetry-board console-board">',
            '<div class="telemetry-board__header console-board__header">',
            '<div><p class="telemetry-board__eyebrow console-board__eyebrow">最近趋势</p><strong>近期状态判断</strong><span>按代表样本去重后，直接判断最近是在稳住，还是还在反复。</span></div>',
            '<span class="telemetry-chip telemetry-chip--accent"><strong>当前段位</strong>',
            api.escapeHtml(buildScoreBandLabel(latestScore)),
            "</span></div>",
            '<div class="trend-rack" aria-label="近期代表记录分数走势">',
            renderTrendRack(chartItems),
            "</div>",
            '<div class="telemetry-board__meta console-board__meta">',
            '<span class="telemetry-chip"><strong>最新变化</strong>',
            api.escapeHtml(buildRecentDeltaText(recentScoredItems)),
            "</span>",
            '<span class="telemetry-chip"><strong>最近均分</strong>',
            api.formatNumber(recentAverage, 1),
            " 分</span>",
            '<span class="telemetry-chip"><strong>波动区间</strong>',
            api.formatNumber(scoreRange, 1),
            " 分</span>",
            "</div></div>",
        ].join("");
    }

    function renderSummaryTiles(items) {
        if (!items.length) {
            return [
                '<div class="summary-tile"><strong>最近一周</strong><span>0 次</span></div>',
                '<div class="summary-tile"><strong>已完成</strong><span>0 条</span></div>',
                '<div class="summary-tile"><strong>当前最好</strong><span>暂无</span></div>',
                '<div class="summary-tile"><strong>建议回看</strong><span>0 条</span></div>',
            ].join("");
        }

        const scoredItems = items.filter(function filterItem(item) {
            return item.status === "success" && getNumericScore(item) !== null;
        });
        const averageScore = scoredItems.length
            ? scoredItems.reduce(function sumScore(total, item) {
                return total + getNumericScore(item);
            }, 0) / scoredItems.length
            : null;
        const bestScore = scoredItems.length
            ? Math.max.apply(null, scoredItems.map(getNumericScore))
            : null;

        return [
            '<div class="summary-tile summary-tile--signal"><strong>最近一周</strong><span>',
            countRecentPractices(items, 7),
            ' 次</span></div>',
            '<div class="summary-tile"><strong>已完成</strong><span>',
            scoredItems.length,
            ' 条</span></div>',
            '<div class="summary-tile"><strong>当前最好</strong><span>',
            bestScore === null ? '暂无' : api.formatNumber(bestScore, 1) + ' 分',
            '</span></div>',
            '<div class="summary-tile"><strong>建议回看</strong><span>',
            countFollowup(items),
            ' 条</span></div>',
            averageScore === null ? '' : [
                '<div class="summary-tile"><strong>平均得分</strong><span>',
                api.formatNumber(averageScore, 1),
                ' 分</span></div>',
            ].join(""),
        ].join("");
    }

    function buildResumeModel(items, lookups) {
        const latestRecord = items.slice().sort(compareByRecent)[0] || null;
        const representativeScoredItems = getRepresentativeScoredItems(items, 6);
        const showcaseItem = representativeScoredItems.find(function findShowcase(item) {
            const score = getNumericScore(item);
            return score !== null && score >= 65 && score < SCORE_REHEARSE_THRESHOLD;
        });
        const latestItem = showcaseItem || representativeScoredItems[0] || latestRecord;

        if (!latestItem) {
            return {
                badge: "暂无记录",
                title: "先开始第一轮练习",
                copy: "当前还没有可恢复的练习记录。直接进入练习台，先完成一轮示范对照和结果生成。",
                meta: "首页会在第一条练习记录生成后自动出现恢复入口。",
                chips: ["从练习台开始", "先选示范素材"],
                primary: {
                    label: "开始新练习",
                    href: "/analysis",
                },
                secondary: {
                    label: "先看示范素材",
                    href: "/standard-videos",
                },
            };
        }

        const standardVideo = lookups.standard[latestItem.standard_video_id] || null;
        const learnerVideo = lookups.learner[latestItem.learner_video_id] || null;
        const score = getNumericScore(latestItem);
        const openResultLink = "/review?analysis_id=" + encodeURIComponent(latestItem.id);
        const replayLink = "/analysis?standard_video_id="
            + encodeURIComponent(latestItem.standard_video_id)
            + "&learner_video_id="
            + encodeURIComponent(latestItem.learner_video_id)
            + "&preview_target=both";
        const isLatestRecord = Boolean(latestRecord && latestRecord.id === latestItem.id);
        const baseModel = {
            title: (isLatestRecord ? "最近一次练习 #" : "当前推荐复盘 #") + latestItem.id,
            meta: (latestItem.updated_at || latestItem.created_at || "--")
                + (score === null ? "" : " · " + api.formatNumber(score, 1) + " 分"),
            chips: [
                "示范：" + getVideoLabel(standardVideo, "示范动作", latestItem.standard_video_id),
                "练习：" + getVideoLabel(learnerVideo, "练习视频", latestItem.learner_video_id),
            ],
        };

        if (latestItem.status === "success") {
            const needsReview = score !== null && score < SCORE_REHEARSE_THRESHOLD;
            return Object.assign(baseModel, {
                badge: showcaseItem ? "建议先演示这条" : (needsReview ? "建议继续复练" : "可以继续回看"),
                copy: latestItem.summary_text || "这次结果已经生成，可以继续进入结果复盘页，或直接带着这组素材再练一次。",
                primary: {
                    label: "继续看结果复盘",
                    href: openResultLink,
                },
                secondary: needsReview ? {
                    label: "带着这组素材再练",
                    href: replayLink,
                } : {
                    label: "打开练习记录",
                    href: "/history",
                },
            });
        }

        if (latestItem.status === "failed") {
            return Object.assign(baseModel, {
                badge: "上次生成失败",
                copy: "上一轮没有成功完成结果生成，更适合直接带着这组素材重新开始，而不是停留在首页。",
                primary: {
                    label: "带着这组素材再练",
                    href: replayLink,
                },
                secondary: {
                    label: "打开练习记录",
                    href: "/history",
                },
            });
        }

        return Object.assign(baseModel, {
            badge: latestItem.status === "running" ? "上次仍在生成" : "上次还没开始",
            copy: latestItem.status === "running"
                ? "上一轮还在生成结果，优先继续打开当前练习页，避免重复切换。"
                : "上一轮只保留了素材，还没开始结果生成。你可以直接继续当前练习。",
            primary: {
                label: "继续当前练习",
                href: openResultLink,
            },
            secondary: {
                label: "打开练习记录",
                href: "/history",
            },
        });
    }

    function renderChips(items) {
        return items.map(function renderChip(entry) {
            return '<span class="home-chip">' + api.escapeHtml(entry) + "</span>";
        }).join("");
    }

    function applyResumeModel(model, elements) {
        elements.resumeBadge.textContent = model.badge;
        elements.resumeTitle.textContent = model.title;
        elements.resumeCopy.textContent = model.copy;
        elements.resumeMeta.textContent = model.meta;
        elements.resumePair.innerHTML = renderChips(model.chips);
        elements.resumePrimary.textContent = model.primary.label;
        elements.resumePrimary.href = model.primary.href;
        elements.resumeSecondary.textContent = model.secondary.label;
        elements.resumeSecondary.href = model.secondary.href;
    }

    function initializePage() {
        const elements = {
            resumeBadge: document.getElementById("home-resume-badge"),
            resumeTitle: document.getElementById("home-resume-title"),
            resumeCopy: document.getElementById("home-resume-copy"),
            resumeMeta: document.getElementById("home-resume-meta"),
            resumePair: document.getElementById("home-resume-pair"),
            resumePrimary: document.getElementById("home-resume-primary"),
            resumeSecondary: document.getElementById("home-resume-secondary"),
            summaryStatus: document.getElementById("home-summary-status"),
            summaryGrid: document.getElementById("home-summary-grid"),
            summaryTelemetry: document.getElementById("home-summary-telemetry"),
            summaryNote: document.getElementById("home-summary-note"),
        };

        if (!api || Object.values(elements).some(function hasMissingElement(value) { return !value; })) {
            return;
        }

        Promise.all([
            api.fetchJson("/api/history").catch(function fallbackHistory() {
                return {items: []};
            }),
            api.fetchJson("/api/videos/standard").catch(function fallbackStandard() {
                return {items: []};
            }),
            api.fetchJson("/api/videos/learner").catch(function fallbackLearner() {
                return {items: []};
            }),
        ])
            .then(function assignData(results) {
                const historyItems = Array.isArray(results[0] && results[0].items) ? results[0].items : [];
                const lookups = {
                    standard: buildLookup(results[1] && results[1].items),
                    learner: buildLookup(results[2] && results[2].items),
                };

                applyResumeModel(buildResumeModel(historyItems, lookups), elements);
                elements.summaryGrid.innerHTML = renderSummaryTiles(historyItems);
                elements.summaryTelemetry.innerHTML = renderTelemetryPanel(historyItems);
                elements.summaryStatus.textContent = historyItems.length ? "已同步" : "暂无记录";
                elements.summaryNote.textContent = historyItems.length
                    ? buildProgressNote(historyItems)
                    : "完成第一轮练习后，这里会自动汇总代表性结果、最好成绩和需要继续回看的样本。";
            })
            .catch(function handleError() {
                elements.resumeBadge.textContent = "读取失败";
                elements.resumeTitle.textContent = "当前无法恢复最近练习";
                elements.resumeCopy.textContent = "首页暂时拿不到最近练习数据。你可以直接进入练习台，或稍后刷新页面。";
                elements.resumeMeta.textContent = "最近摘要接口暂时不可用。";
                elements.resumePair.innerHTML = renderChips(["直接打开练习台", "稍后刷新首页"]);
                elements.resumePrimary.textContent = "进入练习台";
                elements.resumePrimary.href = "/analysis";
                elements.resumeSecondary.textContent = "打开练习记录";
                elements.resumeSecondary.href = "/history";
                elements.summaryStatus.textContent = "读取失败";
                elements.summaryGrid.innerHTML = [
                    '<div class="summary-tile"><strong>最近一周</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>已完成</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>当前最好</strong><span>--</span></div>',
                    '<div class="summary-tile"><strong>建议回看</strong><span>--</span></div>',
                ].join("");
                elements.summaryTelemetry.innerHTML = '<div class="home-telemetry__empty">当前无法汇总最近走势，请稍后刷新页面。</div>';
                elements.summaryNote.textContent = "当前无法读取最近练习摘要，请稍后刷新页面。";
            });
    }

    document.addEventListener("DOMContentLoaded", initializePage);
})(window, document);
