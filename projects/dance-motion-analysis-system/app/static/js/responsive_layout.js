/*
 * @file responsive_layout
 * @summary Switches layout density based on the effective viewport width.
 */

(() => {
    const root = document.documentElement;
    let frameToken = 0;

    function getEffectiveWidth() {
        const viewportWidth = window.visualViewport && Number.isFinite(window.visualViewport.width)
            ? window.visualViewport.width
            : 0;

        return Math.max(
            0,
            Math.round(viewportWidth || window.innerWidth || root.clientWidth || 0),
        );
    }

    function resolveMode(width) {
        if (width >= 1540) {
            return "wide";
        }

        if (width >= 1180) {
            return "regular";
        }

        if (width >= 900) {
            return "compact";
        }

        return "tight";
    }

    function applyLayoutMode() {
        frameToken = 0;
        const effectiveWidth = getEffectiveWidth();
        const nextMode = resolveMode(effectiveWidth);

        if (root.dataset.layoutMode !== nextMode) {
            root.dataset.layoutMode = nextMode;
        }

        root.style.setProperty("--layout-effective-width", `${effectiveWidth}px`);
    }

    function scheduleApply() {
        if (frameToken) {
            cancelAnimationFrame(frameToken);
        }

        frameToken = requestAnimationFrame(applyLayoutMode);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", applyLayoutMode, { once: true });
    } else {
        applyLayoutMode();
    }

    window.addEventListener("resize", scheduleApply, { passive: true });
    window.addEventListener("orientationchange", scheduleApply, { passive: true });

    if (window.visualViewport) {
        window.visualViewport.addEventListener("resize", scheduleApply, { passive: true });
        window.visualViewport.addEventListener("scroll", scheduleApply, { passive: true });
    }
})();
