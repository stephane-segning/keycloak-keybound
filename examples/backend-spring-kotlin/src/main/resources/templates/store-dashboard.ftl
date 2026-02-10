<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Backend Store Dashboard</title>
    <link href="https://cdn.jsdelivr.net/npm/daisyui@5" rel="stylesheet" type="text/css"/>
    <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>
</head>
<body class="bg-base-200 text-base-content min-h-screen">
<header class="bg-base-100 border-b border-base-300 shadow-sm">
    <div class="max-w-7xl mx-auto px-4 py-5">
        <h1 class="text-2xl font-semibold">Backend Store Snapshot</h1>
        <p class="text-sm opacity-70">Live view of all in-memory backend stores.</p>
    </div>
</header>
<main class="max-w-7xl mx-auto px-4 py-6 space-y-6">
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Users</h2>
                <span class="badge badge-neutral badge-lg">${users?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
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
                            <td colspan="5" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list users as user>
                            <tr>
                                <td class="font-mono text-xs">${user.getUserId()!''}</td>
                                <td>${user.getUsername()!''}</td>
                                <td>${user.getEmail()!''}</td>
                                <td>${user.getRealm()!''}</td>
                                <td><#if (user.getEnabled())!false>✅<#else>⚪</#if></td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Devices</h2>
                <span class="badge badge-neutral badge-lg">${devices?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
                    <thead>
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
                            <td colspan="4" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list devices as device>
                            <#assign deviceJkt = device.getJkt()!''>
                            <tr>
                                <td class="font-mono text-xs">${device.getDeviceId()!''}</td>
                                <td class="font-mono text-xs">${device.getUserId()!''}</td>
                                <td>${device.getStatus()!''}</td>
                                <td class="font-mono text-xs" title="${deviceJkt}">
                                    <#if deviceJkt?length gt 8>${deviceJkt?substring(0, 8)}…<#else>${deviceJkt}</#if>
                                </td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Devices By JKT Index</h2>
                <span class="badge badge-neutral badge-lg">${devicesByJkt?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
                    <thead>
                    <tr>
                        <th>JKT</th>
                        <th>Device ID</th>
                        <th>User ID</th>
                    </tr>
                    </thead>
                    <tbody>
                    <#if devicesByJkt?size == 0>
                        <tr>
                            <td colspan="3" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list devicesByJkt as entry>
                            <#assign indexedJkt = entry.getJkt()!''>
                            <tr>
                                <td class="font-mono text-xs" title="${indexedJkt}">
                                    <#if indexedJkt?length gt 8>${indexedJkt?substring(0, 8)}…<#else>${indexedJkt}</#if>
                                </td>
                                <td class="font-mono text-xs">${entry.getDeviceId()!''}</td>
                                <td class="font-mono text-xs">${entry.getUserId()!''}</td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Username Index</h2>
                <span class="badge badge-neutral badge-lg">${usernameIndex?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
                    <thead>
                    <tr>
                        <th>Key</th>
                        <th>User ID</th>
                    </tr>
                    </thead>
                    <tbody>
                    <#if usernameIndex?size == 0>
                        <tr>
                            <td colspan="2" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list usernameIndex as entry>
                            <tr>
                                <td>${entry.getKey()!''}</td>
                                <td class="font-mono text-xs">${entry.getValue()!''}</td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Email Index</h2>
                <span class="badge badge-neutral badge-lg">${emailIndex?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
                    <thead>
                    <tr>
                        <th>Key</th>
                        <th>User ID</th>
                    </tr>
                    </thead>
                    <tbody>
                    <#if emailIndex?size == 0>
                        <tr>
                            <td colspan="2" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list emailIndex as entry>
                            <tr>
                                <td>${entry.getKey()!''}</td>
                                <td class="font-mono text-xs">${entry.getValue()!''}</td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">Approvals</h2>
                <span class="badge badge-neutral badge-lg">${approvals?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
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
                            <td colspan="4" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list approvals as approval>
                            <tr>
                                <td class="font-mono text-xs">${approval.getRequestId()!''}</td>
                                <td class="font-mono text-xs">${approval.getUserId()!''}</td>
                                <td class="font-mono text-xs">${approval.getDeviceId()!''}</td>
                                <td>${approval.getStatus()!''}</td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
    <section class="card bg-base-100 shadow-md">
        <div class="card-body">
            <div class="flex items-center justify-between mb-2">
                <h2 class="card-title">SMS Challenges</h2>
                <span class="badge badge-neutral badge-lg">${smsChallenges?size} entries</span>
            </div>
            <div class="overflow-x-auto">
                <table class="table table-zebra text-sm">
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
                            <td colspan="3" class="text-center opacity-60">No entries</td>
                        </tr>
                    <#else>
                        <#list smsChallenges as challenge>
                            <tr>
                                <td class="font-mono text-xs">${challenge.getHash()!''}</td>
                                <td class="font-mono">${challenge.getOtp()!''}</td>
                                <td>${challenge.getExpiresAt()!''}</td>
                            </tr>
                        </#list>
                    </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </section>
</main>
</body>
</html>
