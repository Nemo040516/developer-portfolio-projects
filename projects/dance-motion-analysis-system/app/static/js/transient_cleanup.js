(function bootstrapTransientCleanup(window, document) {
    "use strict";

    const WORKFLOW_PATHS = new Set([
        "/session",
        "/analysis",
        "/review",
        "/history",
        "/standard-preview",
        "/learner-preview",
    ]);

    if (!WORKFLOW_PATHS.has(window.location.pathname)) {
        return;
    }

    let internalNavigation = false;
    let cleanupSent = false;

    function isSameOriginUrl(value) {
        try {
            const resolved = new URL(value, window.location.href);
            return resolved.origin === window.location.origin;
        } catch (error) {
            return false;
        }
    }

    function markInternalNavigation() {
        internalNavigation = true;
    }

    function sendCleanupRequest() {
        if (cleanupSent || internalNavigation) {
            return;
        }
        cleanupSent = true;

        const cleanupUrl = window.location.origin + "/api/session/transient-cleanup";
        if (navigator.sendBeacon) {
            navigator.sendBeacon(cleanupUrl, new Blob(["{}"], {type: "application/json"}));
            return;
        }

        window.fetch(cleanupUrl, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: "{}",
            keepalive: true,
        }).catch(function ignoreCleanupError() {
            return null;
        });
    }

    document.addEventListener("click", function handleNavigationClick(event) {
        const anchor = event.target.closest("a[href]");
        if (!anchor) {
            return;
        }
        if (anchor.target === "_blank" || anchor.hasAttribute("download")) {
            return;
        }
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        if (isSameOriginUrl(anchor.href)) {
            markInternalNavigation();
        }
    }, true);

    document.addEventListener("submit", function handleFormSubmit() {
        markInternalNavigation();
    }, true);

    window.addEventListener("pagehide", function handlePageHide(event) {
        if (event.persisted) {
            return;
        }
        sendCleanupRequest();
    });
})(window, document);
