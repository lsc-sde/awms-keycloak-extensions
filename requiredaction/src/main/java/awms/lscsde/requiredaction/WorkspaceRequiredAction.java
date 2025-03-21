/**
 * A Keycloak required action provider for workspace selection functionality.
 * <p>
 * This class implements the RequiredActionFactory and RequiredActionProvider
 * interfaces to provide a custom authentication flow step that requires users
 * to select a workspace before accessing the system.
 * <p>
 * The implementation manages workspace associations for users, stores workspace
 * metadata as user attributes, and coordinates with Kubernetes to manage
 * workspace bindings. It presents a form to the user allowing them to select
 * from available workspaces and then processes their selection.
 * <p>
 * The action is triggered when:
 * <ul>
 * <li>A user does not have a workspace assigned yet</li>
 * <li>A Guacamole client is being accessed with a different session than the
 * one the workspace was assigned with</li>
 * </ul>
 * <p>
 * Key functions:
 * <ul>
 * <li>Evaluates when the action should be triggered</li>
 * <li>Presents a form for workspace selection</li>
 * <li>Processes the selected workspace and stores it as user attributes</li>
 * <li>Communicates with Kubernetes to set active workspace bindings</li>
 * </ul>
 *
 * @see RequiredActionFactory
 * @see RequiredActionProvider
 */
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

/**
 * A Required Action implementation that manages workspace binding for users.
 *
 * This class handles the process of assigning and validating workspace
 * information for user accounts. It implements both RequiredActionFactory and
 * RequiredActionProvider interfaces from Keycloak to register and process the
 * required action.
 *
 * The action is triggered when: 1. The user doesn't have complete workspace
 * information in their account 2. When accessing through Guacamole client with
 * an assigned session that doesn't match the current authentication session
 *
 * During the action, users are presented with a form to select a workspace from
 * available options. Upon successful selection, the workspace binding
 * information is stored in the user's attributes, and the Kubernetes client is
 * updated with the active workspace binding.
 *
 * This implementation uses a WorkspaceKubernetesClient to interact with
 * workspace resources and maintains several user attributes to track workspace
 * association.
 */
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

    /**
     * Evaluates the conditions that trigger the required action for the user.
     *
     * This method checks various scenarios: 1. If any essential workspace
     * attributes are missing from the user profile, the required action is
     * added to the user. 2. If the client is Guacamole, and the user has a
     * workspace session that doesn't match the current authentication session,
     * the required action is triggered. 3. Otherwise, the workspace client is
     * initialized and the active workspace binding is set for the user.
     *
     * @param context The required action context containing user and session
     * information
     */
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

    /**
     * Overrides the requiredActionChallenge method to prompt the user with a
     * required action form. When a user is required to complete this action,
     * this method is called to generate and present the challenge form to the
     * user.
     *
     * @param context The RequiredActionContext which contains information about
     * the current authentication session and provides methods to challenge the
     * user or complete the action.
     */
    @Override
    public void requiredActionChallenge(RequiredActionContext context) {

        context.challenge(createForm(context));
    }

    /**
     * Processes the required action of setting a workspace for a user during
     * authentication.
     *
     * This method: 1. Extracts the workspace information from the form data 2.
     * Validates the workspace name 3. Sets workspace attributes on the user
     * profile 4. Removes the required action from user and session 5. Sets the
     * active workspace binding for the user via workspace client 6. Completes
     * the authentication process
     *
     * @param context The required action context containing authentication
     * session and HTTP request data
     * @throws IllegalArgumentException If the workspace name format is invalid
     */
    @Override
    public void processAction(RequiredActionContext context) {
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

    /**
     * Initializes the WorkspaceKubernetesClient if it hasn't been initialized
     * yet. Creates a new instance of WorkspaceKubernetesClient and assigns it
     * to workspaceClient. Logs a message upon successful initialization. If
     * initialization fails due to an IOException, the exception is logged.
     */
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

    /**
     * Creates a form for selecting a workspace.
     *
     * This method initializes the client, retrieves available workspaces for
     * the user, and prepares the form for workspace selection. It converts the
     * available workspaces into a JSON representation for use in the form
     * template.
     *
     * @param context The required action context containing user information
     * and form provider
     * @param formConsumer Optional consumer to customize the form before
     * creation (may be null)
     * @return Response containing the rendered form
     */
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
