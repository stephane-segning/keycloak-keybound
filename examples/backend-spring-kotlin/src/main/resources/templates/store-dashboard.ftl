<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Backend Store Dashboard</title>
    <style>
        :root {
            color-scheme: light;
            --bg: #f4f4f5;
            --surface: #ffffff;
            --text: #111827;
            --muted: #6b7280;
            --line: #d4d4d8;
            --accent: #111827;
        }

        * {
            box-sizing: border-box;
        }

        body {
            margin: 0;
            min-height: 100vh;
            background: var(--bg);
            color: var(--text);
            font-family: "IBM Plex Sans", "Inter", "Segoe UI", sans-serif;
            line-height: 1.4;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 16px;
        }

        .hero {
            border-bottom: 1px solid var(--line);
            background: var(--surface);
        }

        .hero-inner {
            padding: 36px 0;
        }

        .hero-kicker {
            margin: 0;
            font-size: 12px;
            letter-spacing: 0.18em;
            text-transform: uppercase;
            color: var(--muted);
        }

        .hero-title {
            margin: 8px 0 10px;
            font-size: clamp(28px, 4vw, 40px);
            line-height: 1.1;
            font-weight: 650;
        }

        .hero-subtitle {
            margin: 0;
            max-width: 820px;
            color: var(--muted);
            font-size: 15px;
        }

        .hero-meta {
            margin-top: 18px;
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }

        .pill {
            border: 1px solid var(--line);
            padding: 5px 10px;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.08em;
            background: var(--surface);
        }

        .pill.live-connected {
            border-color: #15803d;
            color: #15803d;
        }

        .pill.live-disconnected {
            border-color: #b91c1c;
            color: #b91c1c;
        }

        main {
            padding: 20px 0 28px;
        }

        .grid {
            display: grid;
            gap: 12px;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
        }

        .panel {
            border: 1px solid var(--line);
            background: var(--surface);
        }

        .panel-head {
            border-bottom: 1px solid var(--line);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding: 10px 12px;
        }

        .panel-title {
            margin: 0;
            font-size: 14px;
            font-weight: 650;
            text-transform: uppercase;
            letter-spacing: 0.08em;
        }

        .count {
            border: 1px solid var(--line);
            background: var(--bg);
            padding: 2px 8px;
            font-size: 12px;
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
        }

        .table-wrap {
            overflow-x: auto;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }

        th,
        td {
            border-bottom: 1px solid var(--line);
            padding: 8px 10px;
            text-align: left;
            vertical-align: top;
            white-space: nowrap;
        }

        thead th {
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.08em;
            color: var(--muted);
            font-weight: 600;
            background: #fafafa;
        }

        tbody tr:nth-child(2n) {
            background: #fcfcfd;
        }

        .mono {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
            font-size: 12px;
        }

        .empty {
            color: var(--muted);
            text-align: center;
            padding: 16px 10px;
        }

        .status-yes {
            color: #15803d;
            font-weight: 600;
        }

        .status-no {
            color: #a1a1aa;
            font-weight: 600;
        }

        @media (max-width: 640px) {
            .hero-inner {
                padding: 28px 0;
            }

            .grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
<header class="hero">
    <div class="container hero-inner">
        <p class="hero-kicker">Keybound</p>
        <h1 class="hero-title">Backend Store Dashboard</h1>
        <p class="hero-subtitle">Flat snapshot of live in-memory state for users, devices, and indexes.</p>
        <div class="hero-meta">
            <span class="pill" id="pill-users">users ${users?size}</span>
            <span class="pill" id="pill-devices">devices ${devices?size}</span>
            <span class="pill live-disconnected" id="pill-live-status">live disconnected</span>
        </div>
    </div>
</header>

<main>
    <div class="container">
        <div class="grid">
            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Users</h2>
                    <span class="count" id="count-users">${users?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>User ID</th>
                            <th>Username</th>
                            <th>Email</th>
                            <th>Realm</th>
                            <th>Enabled</th>
                        </tr>
                        </thead>
                        <tbody id="tbody-users">
                        <#if users?size == 0>
                            <tr>
                                <td colspan="5" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list users as user>
                                <tr>
                                    <td class="mono">${user.getUserId()!''}</td>
                                    <td>${user.getUsername()!''}</td>
                                    <td>${user.getEmail()!''}</td>
                                    <td>${user.getRealm()!''}</td>
                                    <td>
                                        <#if (user.getEnabled())!false>
                                            <span class="status-yes">YES</span>
                                        <#else>
                                            <span class="status-no">NO</span>
                                        </#if>
                                    </td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Devices</h2>
                    <span class="count" id="count-devices">${devices?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Record ID</th>
                            <th>Device ID</th>
                            <th>User ID</th>
                            <th>OS</th>
                            <th>Model</th>
                            <th>Status</th>
                            <th>JKT</th>
                        </tr>
                        </thead>
                        <tbody id="tbody-devices">
                        <#if devices?size == 0>
                            <tr>
                                <td colspan="7" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list devices as device>
                                <#assign deviceJkt = device.getJkt()!''>
                                <tr>
                                    <td class="mono">${device.getRecordId()!''}</td>
                                    <td class="mono">${device.getDeviceId()!''}</td>
                                    <td class="mono">${device.getUserId()!''}</td>
                                    <td>${device.getDeviceOs()!''}</td>
                                    <td>${device.getDeviceModel()!''}</td>
                                    <td>${device.getStatus()!''}</td>
                                    <td class="mono" title="${deviceJkt}">
                                        <#if deviceJkt?length gt 8>${deviceJkt?substring(0, 8)}...<#else>${deviceJkt}</#if>
                                    </td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Devices By JKT Index</h2>
                    <span class="count" id="count-devicesByJkt">${devicesByJkt?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>JKT</th>
                            <th>Record ID</th>
                            <th>Device ID</th>
                            <th>User ID</th>
                        </tr>
                        </thead>
                        <tbody id="tbody-devicesByJkt">
                        <#if devicesByJkt?size == 0>
                            <tr>
                                <td colspan="4" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list devicesByJkt as entry>
                                <#assign indexedJkt = entry.getJkt()!''>
                                <tr>
                                    <td class="mono" title="${indexedJkt}">
                                        <#if indexedJkt?length gt 8>${indexedJkt?substring(0, 8)}...<#else>${indexedJkt}</#if>
                                    </td>
                                    <td class="mono">${entry.getRecordId()!''}</td>
                                    <td class="mono">${entry.getDeviceId()!''}</td>
                                    <td class="mono">${entry.getUserId()!''}</td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Username Index</h2>
                    <span class="count" id="count-usernameIndex">${usernameIndex?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Key</th>
                            <th>User ID</th>
                        </tr>
                        </thead>
                        <tbody id="tbody-usernameIndex">
                        <#if usernameIndex?size == 0>
                            <tr>
                                <td colspan="2" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list usernameIndex as entry>
                                <tr>
                                    <td>${entry.getKey()!''}</td>
                                    <td class="mono">${entry.getValue()!''}</td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Email Index</h2>
                    <span class="count" id="count-emailIndex">${emailIndex?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Key</th>
                            <th>User ID</th>
                        </tr>
                        </thead>
                        <tbody id="tbody-emailIndex">
                        <#if emailIndex?size == 0>
                            <tr>
                                <td colspan="2" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list emailIndex as entry>
                                <tr>
                                    <td>${entry.getKey()!''}</td>
                                    <td class="mono">${entry.getValue()!''}</td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>
        </div>
    </div>
</main>
<script>
    (function () {
        var liveStatusPill = document.getElementById("pill-live-status");
        var wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        var wsUrl = wsProtocol + "//" + window.location.host + "/ws/admin/stores";
        var socket;

        function setLiveStatus(statusText, isConnected) {
            if (!liveStatusPill) return;
            liveStatusPill.textContent = "live " + statusText;
            liveStatusPill.classList.remove("live-connected", "live-disconnected");
            liveStatusPill.classList.add(isConnected ? "live-connected" : "live-disconnected");
        }

        function text(value) {
            if (value === null || value === undefined) return "";
            return String(value)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#39;");
        }

        function yesNo(value) {
            return value ? '<span class="status-yes">YES</span>' : '<span class="status-no">NO</span>';
        }

        function mono(value) {
            return '<span class="mono">' + text(value) + '</span>';
        }

        function truncateJkt(value) {
            var jkt = text(value);
            if (jkt.length > 8) {
                return jkt.substring(0, 8) + "...";
            }
            return jkt;
        }

        function setCount(id, value) {
            var el = document.getElementById(id);
            if (el) el.textContent = text(value);
        }

        function setPill(id, label, value) {
            var el = document.getElementById(id);
            if (el) el.textContent = label + " " + text(value);
        }

        function renderBody(tbodyId, rows, emptyColspan) {
            var tbody = document.getElementById(tbodyId);
            if (!tbody) return;
            if (!rows || rows.length === 0) {
                tbody.innerHTML = '<tr><td colspan="' + emptyColspan + '" class="empty">No entries</td></tr>';
                return;
            }
            tbody.innerHTML = rows.join("");
        }

        function render(snapshot) {
            var users = Array.isArray(snapshot.users) ? snapshot.users : [];
            var devices = Array.isArray(snapshot.devices) ? snapshot.devices : [];
            var devicesByJkt = Array.isArray(snapshot.devicesByJkt) ? snapshot.devicesByJkt : [];
            var usernameIndex = Array.isArray(snapshot.usernameIndex) ? snapshot.usernameIndex : [];
            var emailIndex = Array.isArray(snapshot.emailIndex) ? snapshot.emailIndex : [];

            setCount("count-users", users.length);
            setCount("count-devices", devices.length);
            setCount("count-devicesByJkt", devicesByJkt.length);
            setCount("count-usernameIndex", usernameIndex.length);
            setCount("count-emailIndex", emailIndex.length);

            setPill("pill-users", "users", users.length);
            setPill("pill-devices", "devices", devices.length);

            renderBody(
                "tbody-users",
                users.map(function (user) {
                    return "<tr>" +
                        "<td>" + mono(user.userId) + "</td>" +
                        "<td>" + text(user.username) + "</td>" +
                        "<td>" + text(user.email) + "</td>" +
                        "<td>" + text(user.realm) + "</td>" +
                        "<td>" + yesNo(!!user.enabled) + "</td>" +
                        "</tr>";
                }),
                5
            );

            renderBody(
                "tbody-devices",
                devices.map(function (device) {
                    var jkt = text(device.jkt);
                    return "<tr>" +
                        "<td>" + mono(device.recordId) + "</td>" +
                        "<td>" + mono(device.deviceId) + "</td>" +
                        "<td>" + mono(device.userId) + "</td>" +
                        "<td>" + text(device.deviceOs) + "</td>" +
                        "<td>" + text(device.deviceModel) + "</td>" +
                        "<td>" + text(device.status) + "</td>" +
                        "<td class=\"mono\" title=\"" + jkt + "\">" + truncateJkt(jkt) + "</td>" +
                        "</tr>";
                }),
                7
            );

            renderBody(
                "tbody-devicesByJkt",
                devicesByJkt.map(function (entry) {
                    var jkt = text(entry.jkt);
                    return "<tr>" +
                        "<td class=\"mono\" title=\"" + jkt + "\">" + truncateJkt(jkt) + "</td>" +
                        "<td>" + mono(entry.recordId) + "</td>" +
                        "<td>" + mono(entry.deviceId) + "</td>" +
                        "<td>" + mono(entry.userId) + "</td>" +
                        "</tr>";
                }),
                4
            );

            renderBody(
                "tbody-usernameIndex",
                usernameIndex.map(function (entry) {
                    return "<tr>" +
                        "<td>" + text(entry.key) + "</td>" +
                        "<td>" + mono(entry.value) + "</td>" +
                        "</tr>";
                }),
                2
            );

            renderBody(
                "tbody-emailIndex",
                emailIndex.map(function (entry) {
                    return "<tr>" +
                        "<td>" + text(entry.key) + "</td>" +
                        "<td>" + mono(entry.value) + "</td>" +
                        "</tr>";
                }),
                2
            );

        }

        function connect() {
            setLiveStatus("connecting", false);
            socket = new WebSocket(wsUrl);

            socket.onopen = function () {
                setLiveStatus("connected", true);
            };

            socket.onmessage = function (event) {
                var payload;
                try {
                    payload = JSON.parse(event.data || "{}");
                } catch (error) {
                    return;
                }

                if (!payload || !payload.snapshot) {
                    return;
                }
                render(payload.snapshot);
            };

            socket.onerror = function () {
                setLiveStatus("error", false);
            };

            socket.onclose = function () {
                setLiveStatus("disconnected", false);
                window.setTimeout(connect, 1500);
            };
        }

        connect();
    })();
</script>
</body>
</html>
