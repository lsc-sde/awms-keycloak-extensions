package awms.lscsde.requiredaction;

import com.google.auto.service.AutoService;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;

import java.util.function.Consumer;

@AutoService(RequiredActionFactory.class)
public class WorkspaceRequiredAction implements
	RequiredActionFactory, RequiredActionProvider {

	public static final String WORKSPACE_ID = "workspace_id";

	public static final String PROVIDER_ID = "workspace";

	@Override
	public InitiatedActionSupport initiatedActionSupport() {
		return InitiatedActionSupport.SUPPORTED;
	}

	@Override
	public void evaluateTriggers(RequiredActionContext context) {

	}

	@Override
	public void requiredActionChallenge(RequiredActionContext context) {
		// initial form
		context.challenge(createForm(context));
	}

	@Override
	public void processAction(RequiredActionContext context) {
		// submitted form
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		String workspaceId = formData.getFirst(WORKSPACE_ID);

		if (Validation.isBlank(workspaceId) || workspaceId.length() < 5) {
			context.challenge(createForm(context, form -> form.addError(new FormMessage(WORKSPACE_ID, "workspaceIdInvalid"))));
			return;
		}

		UserModel user = context.getUser();
		user.setSingleAttribute(WORKSPACE_ID, workspaceId);
		user.removeRequiredAction(PROVIDER_ID);
		context.getAuthenticationSession().removeRequiredAction(PROVIDER_ID);
		context.success();
	}

	@Override
	public RequiredActionProvider create(KeycloakSession keycloakSession) {
		return this;
	}

	@Override
	public String getDisplayText() {
		return "Please select your workspace";
	}

	@Override
	public void init(Config.Scope scope) {
	}

	@Override
	public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
	}

	@Override
	public void close() {
	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	private Response createForm(RequiredActionContext context) {
		return createForm(context, null);
	}

	private Response createForm(RequiredActionContext context, Consumer<LoginFormsProvider> formConsumer) {
		LoginFormsProvider form = context.form()
			.setAttribute("username", context.getUser().getUsername())
			.setAttribute(WORKSPACE_ID, context.getUser().getFirstAttribute(WORKSPACE_ID));

		if (formConsumer != null) {
			formConsumer.accept(form);
		}

		return form.createForm("update-workspace.ftl");
	}

}