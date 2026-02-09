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

        <form id="kc-device-approval-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <input type="hidden" id="approval-status" name="approval_status" value="PENDING"/>
        </form>

        <script>
            (function() {
                var pollingUrl = "${pollingUrl}";
                var pollingToken = "${pollingToken}";
                var pollingInterval = ${pollingInterval};
                var form = document.getElementById('kc-device-approval-form');

                function poll() {
                    fetch(pollingUrl + '?token=' + pollingToken)
                        .then(function(response) {
                            if (response.ok) {
                                return response.json();
                            } else {
                                throw new Error('Network response was not ok.');
                            }
                        })
                        .then(function(data) {
                            if (data.status === 'APPROVED') {
                                form.submit();
                            } else if (data.status === 'DENIED' || data.status === 'EXPIRED') {
                                // Handle denial or expiration (e.g., show error message or redirect)
                                // For now, we can submit the form to let the backend handle the failure
                                form.submit();
                            } else {
                                // Still pending, poll again
                                setTimeout(poll, pollingInterval);
                            }
                        })
                        .catch(function(error) {
                            console.error('Polling error:', error);
                            // Retry on error? Or stop?
                            setTimeout(poll, pollingInterval);
                        });
                }

                // Start polling
                setTimeout(poll, pollingInterval);
            })();
        </script>
    </#if>
</@layout.registrationLayout>
