<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('invalidOtp'); section>
    <#if section = "header">
        ${msg("verifyPhoneTitle")}
    <#elseif section = "form">
        <form id="kc-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="otp" class="${properties.kcLabelClass!}">${msg("otpCode")}</label>
                <input type="text"
                       required
                       id="otp"
                       name="otp"
                       autofocus
                       class="${properties.kcInputClass!}"
                       placeholder="123456"/>

                <#if messagesPerField.existsError('invalidOtp')>
                    <span id="input-error-otp" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${msg("invalidOtpMessage")}
                    </span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" value="${msg("doVerify")}"/>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
