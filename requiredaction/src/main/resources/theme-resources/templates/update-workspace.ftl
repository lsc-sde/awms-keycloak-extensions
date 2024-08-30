<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('workspace_id'); section>
    <#if section = "header">
        ${msg("updateWorkspaceTitle")}
    <#elseif section = "form">
			<h2>${msg("updateWorkspaceHello",(username!''))}</h2>
			<p>${msg("updateWorkspaceText")}</p>
			<form id="kc-workspace-update-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
				<div class="${properties.kcFormGroupClass!}">
					<div class="${properties.kcLabelWrapperClass!}">
						<label for="workspace_id" class="${properties.kcLabelClass!}">${msg("updateWorkspaceFieldLabel")}</label>
					</div>
					<div class="${properties.kcInputWrapperClass!}">
						<input type="tel" id="workspace_id" name="workspace_id" class="${properties.kcInputClass!}"
									 value="${workspace_id!}" required aria-invalid="<#if messagesPerField.existsError('workspace_id')>true</#if>"/>
              <#if messagesPerField.existsError('workspace_id')>
								<span id="input-error-workspace-id" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
										${kcSanitize(messagesPerField.get('workspace_id'))?no_esc}
								</span>
              </#if>
					</div>
				</div>
				<div class="${properties.kcFormGroupClass!}">
					<div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
						<input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}"/>
					</div>
				</div>
			</form>
    </#if>
</@layout.registrationLayout>
