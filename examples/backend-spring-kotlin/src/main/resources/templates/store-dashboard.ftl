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
        <p class="hero-subtitle">Flat snapshot of live in-memory state for users, devices, approvals, indexes, and SMS challenges.</p>
        <div class="hero-meta">
            <span class="pill">users ${users?size}</span>
            <span class="pill">devices ${devices?size}</span>
            <span class="pill">approvals ${approvals?size}</span>
            <span class="pill">sms ${smsChallenges?size}</span>
        </div>
    </div>
</header>

<main>
    <div class="container">
        <div class="grid">
            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Users</h2>
                    <span class="count">${users?size}</span>
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
                        <tbody>
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
                    <span class="count">${devices?size}</span>
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
                        <tbody>
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
                    <span class="count">${devicesByJkt?size}</span>
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
                        <tbody>
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
                    <span class="count">${usernameIndex?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Key</th>
                            <th>User ID</th>
                        </tr>
                        </thead>
                        <tbody>
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
                    <span class="count">${emailIndex?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Key</th>
                            <th>User ID</th>
                        </tr>
                        </thead>
                        <tbody>
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

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">Approvals</h2>
                    <span class="count">${approvals?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Request ID</th>
                            <th>User ID</th>
                            <th>Device ID</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#if approvals?size == 0>
                            <tr>
                                <td colspan="4" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list approvals as approval>
                                <tr>
                                    <td class="mono">${approval.getRequestId()!''}</td>
                                    <td class="mono">${approval.getUserId()!''}</td>
                                    <td class="mono">${approval.getDeviceId()!''}</td>
                                    <td>${approval.getStatus()!''}</td>
                                </tr>
                            </#list>
                        </#if>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="panel">
                <div class="panel-head">
                    <h2 class="panel-title">SMS Challenges</h2>
                    <span class="count">${smsChallenges?size}</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>Hash</th>
                            <th>OTP</th>
                            <th>Expires At</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#if smsChallenges?size == 0>
                            <tr>
                                <td colspan="3" class="empty">No entries</td>
                            </tr>
                        <#else>
                            <#list smsChallenges as challenge>
                                <tr>
                                    <td class="mono">${challenge.getHash()!''}</td>
                                    <td class="mono">${challenge.getOtp()!''}</td>
                                    <td>${challenge.getExpiresAt()!''}</td>
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
</body>
</html>
