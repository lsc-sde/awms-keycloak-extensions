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
import org.jboss.logging.Logger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspace;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceBinding;
import com.google.gson.Gson;
import org.keycloak.events.EventBuilder;
import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;

@AutoService(RequiredActionFactory.class)
public class WorkspaceRequiredAction implements
		RequiredActionFactory, RequiredActionProvider {

	private static final Logger LOG = Logger.getLogger(WorkspaceRequiredAction.class);
	public static final String WORKSPACE_BINDING = "workspace_binding";
	public static final String WORKSPACE_NAME = "workspace_name";
	public static final String WORKSPACE_ID = "workspace_id";
	public static final String WORKSPACE_ID_FORMATTED = "workspace_id_formatted";
	public static final String WORKSPACE_ASSIGNED_SESSION = "workspace_assigned_session";
	public static final String PROVIDER_ID = "workspace";
	public static final String GUACAMOLE_CLIENT_NAME = "guacamole";
	public WorkspaceKubernetesClient workspaceClient;

	@Override
	public InitiatedActionSupport initiatedActionSupport() {
		return InitiatedActionSupport.SUPPORTED;
	}

	@Override
	public void evaluateTriggers(RequiredActionContext context) {
		if (context.getUser().getFirstAttribute(WORKSPACE_ASSIGNED_SESSION) == null
				|| context.getUser().getFirstAttribute(WORKSPACE_NAME) == null
				|| context.getUser().getFirstAttribute(WORKSPACE_ID) == null
				|| context.getUser().getFirstAttribute(WORKSPACE_ID_FORMATTED) == null
				|| context.getUser().getFirstAttribute(WORKSPACE_BINDING) == null) {
			context.getUser().addRequiredAction(PROVIDER_ID);
		} else if (context.getSession().getContext().getClient().getName().equals(GUACAMOLE_CLIENT_NAME)
				&& context.getUser().getFirstAttribute(WORKSPACE_ASSIGNED_SESSION) != null
				&& !context.getUser().getFirstAttribute(WORKSPACE_ASSIGNED_SESSION)
						.equals(context.getAuthenticationSession().getParentSession().getId())) {
			context.getUser().addRequiredAction(PROVIDER_ID);
		} else {
			initialiseClient();
			String workspaceBinding = context.getUser().getFirstAttribute(WORKSPACE_BINDING);
			String username = context.getUser().getUsername();
			workspaceClient.setActiveWorkspaceBindingForUser(workspaceBinding, username);
		}
	}

	@Override
	public void requiredActionChallenge(RequiredActionContext context) {
		// initial form
		context.challenge(createForm(context));
	}

	@Override
	public void processAction(RequiredActionContext context) {
		// submitted form
		EventBuilder eventBuilder = context.getEvent();
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		String boundWorkspace = formData.getFirst(WORKSPACE_NAME);
		String workspaceName = boundWorkspace.split(":")[0];
		String bindingName = boundWorkspace.split(":")[1];

		if (Validation.isBlank(workspaceName) || workspaceName.length() < 5) {
			context.challenge(createForm(context,
					form -> form.addError(new FormMessage(WORKSPACE_NAME, "workspaceNameInvalid"))));
			return;
		}

		UserModel user = context.getUser();
		String workspaceId = String.format("%s\\%s", workspaceName, user.getUsername());
		String workspaceIdFormatted = String.format("%s@%s", workspaceName, user.getUsername());
		user.setSingleAttribute(WORKSPACE_BINDING, bindingName);
		user.setSingleAttribute(WORKSPACE_NAME, workspaceName);
		user.setSingleAttribute(WORKSPACE_ID, workspaceId);
		user.setSingleAttribute(WORKSPACE_ID_FORMATTED, workspaceIdFormatted);
		user.setSingleAttribute(WORKSPACE_ASSIGNED_SESSION,
				context.getAuthenticationSession().getParentSession().getId());
		user.removeRequiredAction(PROVIDER_ID);
		eventBuilder.detail(WORKSPACE_ID, workspaceId);
		eventBuilder.detail(WORKSPACE_NAME, workspaceName);
		eventBuilder.detail(WORKSPACE_BINDING, bindingName);
		workspaceClient.setActiveWorkspaceBindingForUser(bindingName, user.getUsername());
		context.getAuthenticationSession().removeRequiredAction(PROVIDER_ID);
		try {
			TimeUnit.SECONDS.sleep(3);
		} catch (InterruptedException ex) {

		}
		context.success();
	}

	@Override
	public RequiredActionProvider create(KeycloakSession keycloakSession) {
		return this;
	}

	@Override
	public String getDisplayText() {
		return "Select workspace";
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

	private void initialiseClient() {
		if (workspaceClient == null) {
			try {
				workspaceClient = new WorkspaceKubernetesClient();
				LOG.info("Workspace Client Initialised");
			} catch (IOException ex) {
				LOG.error(ex);
			}
		}
	}

	private Response createForm(RequiredActionContext context, Consumer<LoginFormsProvider> formConsumer) {
		initialiseClient();
		String workspaceName = context.getUser().getFirstAttribute(WORKSPACE_NAME);
		String username = context.getUser().getUsername();
		List<BoundWorkspace> availableWorkspaces = workspaceClient.getAllWorkspacesForUser(username);
		Gson gson = new Gson();
		HashMap<String, String> availableWorkspacesHashMap = new HashMap<>();
		for (BoundWorkspace boundWorkspace : availableWorkspaces) {
			V1AnalyticsWorkspace workspace = boundWorkspace.getWorkspace();
			V1AnalyticsWorkspaceBinding binding = boundWorkspace.getBinding();
			if (workspace != null && binding != null) {
				String name = String.format("%s:%s", workspace.getMetadata().getName(),
						binding.getMetadata().getName());
				String displayName = workspace.getSpec().getDisplayName();
				availableWorkspacesHashMap.put(name, displayName);
			}
		}

		String availableWorkspacesJson = gson.toJson(availableWorkspacesHashMap);

		LoginFormsProvider form = context.form()
				.setAttribute("username", username)
				.setAttribute("available_workspaces", availableWorkspacesJson)
				.setAttribute(WORKSPACE_NAME, workspaceName);

		if (formConsumer != null) {
			formConsumer.accept(form);
		}

		return form.createForm("update-workspace.ftl");
	}

}