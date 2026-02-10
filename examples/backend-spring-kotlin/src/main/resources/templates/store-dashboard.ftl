<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Backend Store Dashboard</title>
    <link
            href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
            rel="stylesheet"
            integrity="sha384-sgfQv6ZfQ5Ee9La73xJpYp2NANX2rTKoK3tH9or8eB/nS1xR0o4nLd5zrW7jN0WL"
            crossorigin="anonymous"
    />
</head>
<body class="bg-light text-dark">
<header class="bg-primary text-white py-4 mb-4 shadow-sm">
    <div class="container">
        <h1 class="h3 mb-1">Backend Store Snapshot</h1>
        <p class="mb-0 text-white-50">Live view of all in-memory backend stores.</p>
    </div>
</header>
<main class="container mb-5">
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Users</h2>
            <span class="badge bg-secondary fs-6">${users?size} entries</span>
        </div>
        <table class="table table-striped table-bordered align-middle">
            <thead class="table-dark">
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
                    <td colspan="5" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list users as user>
                    <tr>
                        <td>${user.user_id}</td>
                        <td>${user.username}</td>
                        <td>${user.email!''}</td>
                        <td>${user.realm}</td>
                        <td><#if user.enabled?? && user.enabled=='true'>✅<#else>⚪</#if></td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Devices</h2>
            <span class="badge bg-secondary fs-6">${devices?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
            <tr>
                <th>Device ID</th>
                <th>User ID</th>
                <th>Status</th>
                <th>JKT</th>
            </tr>
            </thead>
            <tbody>
            <#if devices?size == 0>
                <tr>
                    <td colspan="4" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list devices as device>
                    <tr>
                        <td>${device.deviceId}</td>
                        <td>${device.userId}</td>
                        <td>${device.status}</td>
                        <td title="${device.jkt}">${device.jkt?substring(0, min(device.jkt?length, 8))}…</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Devices By JKT Index</h2>
            <span class="badge bg-secondary fs-6">${devicesByJkt?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
            <tr>
                <th>JKT</th>
                <th>Device ID</th>
                <th>User ID</th>
            </tr>
            </thead>
            <tbody>
            <#if devicesByJkt?size == 0>
                <tr>
                    <td colspan="3" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list devicesByJkt as entry>
                    <tr>
                        <td title="${entry.jkt}">${entry.jkt?substring(0, min(entry.jkt?length, 8))}…</td>
                        <td>${entry.deviceId}</td>
                        <td>${entry.userId}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Username Index</h2>
            <span class="badge bg-secondary fs-6">${usernameIndex?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
            <tr>
                <th>Key</th>
                <th>User ID</th>
            </tr>
            </thead>
            <tbody>
            <#if usernameIndex?size == 0>
                <tr>
                    <td colspan="2" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list usernameIndex as entry>
                    <tr>
                        <td>${entry.key}</td>
                        <td>${entry.value}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Email Index</h2>
            <span class="badge bg-secondary fs-6">${emailIndex?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
            <tr>
                <th>Key</th>
                <th>User ID</th>
            </tr>
            </thead>
            <tbody>
            <#if emailIndex?size == 0>
                <tr>
                    <td colspan="2" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list emailIndex as entry>
                    <tr>
                        <td>${entry.key}</td>
                        <td>${entry.value}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section>
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">Approvals</h2>
            <span class="badge bg-secondary fs-6">${approvals?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
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
                    <td colspan="4" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list approvals as approval>
                    <tr>
                        <td>${approval.requestId}</td>
                        <td>${approval.userId}</td>
                        <td>${approval.deviceId}</td>
                        <td>${approval.status}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
    <section class="mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h5 mb-0">SMS Challenges</h2>
            <span class="badge bg-secondary fs-6">${smsChallenges?size} entries</span>
        </div>
        <table class="table table-striped table-bordered table-sm align-middle">
            <thead class="table-dark">
            <tr>
                <th>Hash</th>
                <th>OTP</th>
                <th>Expires At</th>
            </tr>
            </thead>
            <tbody>
            <#if smsChallenges?size == 0>
                <tr>
                    <td colspan="3" class="text-center text-muted">No entries</td>
                </tr>
            <#else>
                <#list smsChallenges as challenge>
                    <tr>
                        <td>${challenge.hash}</td>
                        <td>${challenge.otp}</td>
                        <td>${challenge.expiresAt}</td>
                    </tr>
                </#list>
            </#if>
            </tbody>
        </table>
    </section>
</main>
</body>
</html>
