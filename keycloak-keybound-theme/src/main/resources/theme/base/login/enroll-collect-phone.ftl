<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('invalidPhoneNumber'); section>
    <#if section = "header">
        ${msg("collectPhoneTitle")}
    <#elseif section = "form">
        <form id="kc-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="phone" class="${properties.kcLabelClass!}">${msg("phoneNumber")}</label>
                <input type="text"
                       required
                       id="phone"
                       name="phone"
                       autofocus
                       class="${properties.kcInputClass!}"
                       value="${(phone)!""}"
                       placeholder="+1234567890"/>

                <#if messagesPerField.existsError('invalidPhoneNumber')>
                    <span id="input-error-phone" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${msg("invalidPhoneNumberMessage")}
                    </span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" value="${msg("doContinue")}"/>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
