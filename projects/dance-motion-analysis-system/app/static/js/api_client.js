(function bootstrapApiClient(window) {
    "use strict";

    function escapeHtml(value) {
        return String(value ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function formatNumber(value, fractionDigits) {
        if (value === null || value === undefined || Number.isNaN(Number(value))) {
            return "--";
        }
        return Number(value).toFixed(fractionDigits);
    }

    async function fetchJson(url) {
        const response = await fetch(url, {
            headers: {
                Accept: "application/json",
            },
        });

        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            throw new Error("invalid json response");
        }

        if (!response.ok || !payload || payload.code !== 0) {
            const message = payload && payload.message ? payload.message : "request failed";
            throw new Error(message);
        }

        return payload.data;
    }

    async function postJson(url, body) {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
            },
            body: JSON.stringify(body),
        });

        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            throw new Error("invalid json response");
        }

        if (!response.ok || !payload || payload.code !== 0) {
            const message = payload && payload.message ? payload.message : "request failed";
            throw new Error(message);
        }

        return payload.data;
    }

    async function postForm(url, formData) {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                Accept: "application/json",
            },
            body: formData,
        });

        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            throw new Error("invalid json response");
        }

        if (!response.ok || !payload || payload.code !== 0) {
            const message = payload && payload.message ? payload.message : "request failed";
            throw new Error(message);
        }

        return payload.data;
    }

    window.MotionApiClient = {
        escapeHtml,
        fetchJson,
        formatNumber,
        postForm,
        postJson,
    };
})(window);
