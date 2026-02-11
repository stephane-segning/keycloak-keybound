(function () {
    var form = document.getElementById("kc-device-approval-form");
    if (!form) {
        return;
    }

    var pollingUrl = form.getAttribute("data-polling-url") || "";
    var pollingToken = form.getAttribute("data-polling-token") || "";
    var intervalRaw = form.getAttribute("data-polling-interval") || "2000";
    var pollingInterval = Number.parseInt(intervalRaw, 10);
    if (!Number.isFinite(pollingInterval) || pollingInterval < 250) {
        pollingInterval = 2000;
    }

    if (!pollingUrl || !pollingToken) {
        console.error("Approval wait polling config is missing", { pollingUrl: pollingUrl, hasToken: !!pollingToken });
        return;
    }

    var approvalStatusInput = document.getElementById("approval-status");

    function buildStatusUrl() {
        var statusUrl = new URL(pollingUrl, window.location.origin);
        statusUrl.searchParams.set("token", pollingToken);
        return statusUrl.toString();
    }

    function submitWithStatus(status) {
        if (approvalStatusInput) {
            approvalStatusInput.value = status || "PENDING";
        }
        form.submit();
    }

    function scheduleNextPoll() {
        window.setTimeout(poll, pollingInterval);
    }

    function poll() {
        fetch(buildStatusUrl(), {
            method: "GET",
            credentials: "same-origin",
            headers: {
                "Accept": "application/json"
            }
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Polling returned status " + response.status);
                }
                return response.json();
            })
            .then(function (data) {
                var status = (data && data.status) || "PENDING";
                if (status === "APPROVED" || status === "DENIED" || status === "EXPIRED") {
                    submitWithStatus(status);
                    return;
                }
                scheduleNextPoll();
            })
            .catch(function (error) {
                console.error("Approval polling failed", error);
                scheduleNextPoll();
            });
    }

    scheduleNextPoll();
})();
