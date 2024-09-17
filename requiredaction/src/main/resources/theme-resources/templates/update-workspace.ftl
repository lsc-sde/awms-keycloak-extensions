<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('workspace_name'); section>
    <#if section = "header">
        ${msg("updateWorkspaceTitle")}
    <#elseif section = "form">
			<h2>${msg("Analytics Workspace",(username!''))}</h2>
			<p>${msg("Please select your workspace")}</p>
			<form id="kc-workspace-update-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
				<div class="${properties.kcFormGroupClass!}">
					<div class="${properties.kcLabelWrapperClass!}">
						<label for="workspace_name" class="${properties.kcLabelClass!}">${msg("updateWorkspaceFieldLabel")}</label>
					</div>
					<div class="${properties.kcInputWrapperClass!}">
                        <select
                            id="workspace_name"
                            class="${properties.kcInputClass!}"
                            name="workspace_name"
                            value="">
                        </select>
              <#if messagesPerField.existsError('workspace_name')>
								<span id="input-error-workspace-id" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
										${kcSanitize(messagesPerField.get('workspace_name'))?no_esc}
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
            <script type="text/javascript">
                var availableWorkspaces = ${available_workspaces?no_esc};
                var workspaceNameFormItem = document.getElementById("workspace_name");
                for (const [key, value] of Object.entries(availableWorkspaces)) {
                    var el = document.createElement("option");
                    el.textContent = value;
                    el.value = key;
                    workspaceNameFormItem.appendChild(el);
                }
            </script>
    </#if>
</@layout.registrationLayout>
