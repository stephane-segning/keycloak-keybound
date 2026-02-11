<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("deviceApprovalWaitTitle")}
    <#elseif section = "form">
        <div class="pf-c-content">
            <p>${msg("deviceApprovalWaitMessage")}</p>
            <div class="pf-c-empty-state__icon">
                <i class="fas fa-mobile-alt fa-3x"></i>
            </div>
            <p>${msg("deviceApprovalWaitInstruction")}</p>
        </div>

        <form
            id="kc-device-approval-form"
            class="${properties.kcFormClass!}"
            action="${url.loginAction}"
            method="post"
            data-polling-url="${(pollingUrl!'')?html}"
            data-polling-token="${(pollingToken!'')?html}"
            data-polling-interval="${(pollingInterval!2000)?c}"
        >
            <input type="hidden" id="approval-status" name="approval_status" value="PENDING"/>
        </form>

        <script src="${url.resourcesPath}/js/approval-wait.js" defer></script>
    </#if>
</@layout.registrationLayout>
