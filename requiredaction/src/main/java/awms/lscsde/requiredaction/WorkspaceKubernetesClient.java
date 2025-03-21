/**
 * This module defines the WorkspaceKubernetesClient class within the
 * awms.lscsde.requiredaction package. The class is part of the
 * awms-keycloak-extensions project, specifically within the requiredaction
 * module. The purpose of this class is to interact with Kubernetes
 * workspaces, providing necessary functionalities to manage and operate
 * within a Kubernetes environment.
 *
 * File Path: /c:/Users/vishnu.chandrabalan/dev/lsc-sde/products/sde-3rd-party/keycloak/awms-keycloak-extensions/requiredaction/src/main/java/awms/lscsde/requiredaction/WorkspaceKubernetesClient.java
 */
package awms.lscsde.requiredaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import awms.lscsde.requiredaction.BoundWorkspace;
import awms.lscsde.requiredaction.WorkspaceRequiredAction;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspace;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceList;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceBinding;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceBindingList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import org.jboss.logging.Logger;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.custom.V1Patch;

/**
 * The WorkspaceKubernetesClient class provides methods to interact with
 * Kubernetes resources related to analytics workspaces and workspace bindings.
 * It uses the Kubernetes API to perform operations such as fetching workspace
 * bindings for a user, retrieving all workspaces for a user, and patching
 * workspace binding scales.
 *
 * <p>
 * Key functionalities include:
 * <ul>
 * <li>Fetching workspace bindings with or without a specific user label.</li>
 * <li>Retrieving all workspace bindings for a user.</li>
 * <li>Getting all workspaces associated with a user.</li>
 * <li>Patching the scale of a workspace binding.</li>
 * <li>Setting the active workspace binding for a user.</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * WorkspaceKubernetesClient client = new WorkspaceKubernetesClient();
 * List<BoundWorkspace> workspaces = client.getAllWorkspacesForUser("username");
 * client.setActiveWorkspaceBindingForUser("workspaceBindingName", "username");
 * }
 * </pre>
 *
 * <p>
 * Note: This class requires a properly configured Kubernetes cluster and
 * appropriate permissions to interact with the Kubernetes API.
 *
 * @see io.kubernetes.client.openapi.ApiClient
 * @see io.kubernetes.client.util.ClientBuilder
 * @see io.kubernetes.client.util.generic.GenericKubernetesApi
 */
public class WorkspaceKubernetesClient {

    protected static final String API_GROUP = "xlscsde.nhs.uk";
    protected static final String API_VERSION = "v1";
    protected static final String WORKSPACE_PLURAL = "analyticsworkspaces";
    protected static final String WORKSPACE_BINDING_PLURAL = "analyticsworkspacebindings";
    protected static final String DEFAULT_NAMESPACE = "jh-test";
    private static final Logger LOG = Logger.getLogger(WorkspaceKubernetesClient.class);

    protected ApiClient apiClient;
    protected GenericKubernetesApi<V1AnalyticsWorkspace, V1AnalyticsWorkspaceList> workspaceApi;
    protected GenericKubernetesApi<V1AnalyticsWorkspaceBinding, V1AnalyticsWorkspaceBindingList> workspaceBindingApi;

    /**
     * The WorkspaceKubernetesClient provides functionality to interact with
     * Kubernetes resources, specifically for managing AnalyticsWorkspace and
     * AnalyticsWorkspaceBinding resources.
     *
     * This constructor initializes the Kubernetes client and sets up API
     * connections for workspace resources. It uses the in-cluster configuration
     * to authenticate with the Kubernetes API server.
     *
     * @throws IOException If there is an error establishing connection with the
     * Kubernetes cluster
     */
    public WorkspaceKubernetesClient() throws IOException {
        apiClient = ClientBuilder.cluster().build();
        Configuration.setDefaultApiClient(apiClient);
        workspaceApi = new GenericKubernetesApi<>(V1AnalyticsWorkspace.class, V1AnalyticsWorkspaceList.class, API_GROUP, API_VERSION, WORKSPACE_PLURAL, apiClient);
        workspaceBindingApi = new GenericKubernetesApi<>(V1AnalyticsWorkspaceBinding.class, V1AnalyticsWorkspaceBindingList.class, API_GROUP, API_VERSION, WORKSPACE_BINDING_PLURAL, apiClient);
    }

    /**
     * Retrieves a list of analytics workspace bindings for a specific user
     * based on their username label.
     *
     * The method converts the username to a valid Kubernetes label format by
     * replacing non-alphanumeric characters (except dots) with triple
     * underscores. It then queries the Kubernetes API for workspace bindings
     * that have the specified username label.
     *
     * @param username The username for which to fetch workspace bindings
     * @return A list of analytics workspace bindings associated with the
     * specified user
     */
    public V1AnalyticsWorkspaceBindingList getWorkspaceBindingsForUserWithUserLabel(String username) {
        String usernameAsLabel = username.replaceAll("[^0-9a-z.]+", "___");
        LOG.info(String.format("Fetching Workspace Bindings With username label '%s'", usernameAsLabel));
        ListOptions listOptions = new ListOptions();
        listOptions.setLabelSelector(String.format("xlscsde.nhs.uk/username=%s", usernameAsLabel));
        KubernetesApiResponse<V1AnalyticsWorkspaceBindingList> response = workspaceBindingApi.list(listOptions);
        return response.getObject();
    }

    /**
     * Retrieves a list of workspace bindings that do not have a username label.
     *
     * This method queries the Kubernetes API for Analytics Workspace Bindings
     * that are missing the 'xlscsde.nhs.uk/username' label. The selection is
     * done using a negation selector in the label query.
     *
     * @return V1AnalyticsWorkspaceBindingList containing workspace bindings
     * without the username label
     */
    public V1AnalyticsWorkspaceBindingList getWorkspaceBindingsWithoutUserLabel() {
        ListOptions listOptions = new ListOptions();
        LOG.info("Fetching Workspace Bindings Without username label");
        listOptions.setLabelSelector("!xlscsde.nhs.uk/username");
        KubernetesApiResponse<V1AnalyticsWorkspaceBindingList> response = workspaceBindingApi.list(listOptions);
        return response.getObject();
    }

    /**
     * Retrieves all workspace bindings associated with a specific user.
     *
     * The method combines workspace bindings from two sources: 1. Workspace
     * bindings that have a user label for the specified username 2. Workspace
     * bindings without a user label but where the spec.username matches the
     * specified username
     *
     * The resulting list contains distinct workspace bindings to avoid
     * duplicates.
     *
     * @param username The username to fetch workspace bindings for
     * @return A list of unique V1AnalyticsWorkspaceBinding objects associated
     * with the user
     */
    public List<V1AnalyticsWorkspaceBinding> getAllWorkspaceBindingsForUser(String username) {
        LOG.info(String.format("Fetching All Workspace Bindings for '%s'", username));
        List<V1AnalyticsWorkspaceBinding> bindings = new ArrayList<V1AnalyticsWorkspaceBinding>();

        for (V1AnalyticsWorkspaceBinding binding : getWorkspaceBindingsForUserWithUserLabel(username).getItems()) {
            bindings.add(binding);
        }

        for (V1AnalyticsWorkspaceBinding binding : getWorkspaceBindingsWithoutUserLabel().getItems()) {
            if (binding.getSpec().getUsername() == username) {
                bindings.add(binding);
            }
        }

        return bindings.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Retrieves all workspaces bound to a specific user.
     *
     * This method fetches all workspace bindings for the given username, then
     * retrieves the corresponding workspace information for each binding. If
     * multiple bindings exist for the same workspace, only the first one is
     * considered.
     *
     * @param username The username for which to fetch bound workspaces
     * @return A list of BoundWorkspace objects representing the workspaces
     * bound to the user
     */
    public List<BoundWorkspace> getAllWorkspacesForUser(String username) {
        LOG.info(String.format("Fetching All Workspaces for '%s'", username));
        HashMap<String, BoundWorkspace> workspaces = new HashMap<String, BoundWorkspace>();
        List<V1AnalyticsWorkspaceBinding> bindings = getAllWorkspaceBindingsForUser(username);
        for (V1AnalyticsWorkspaceBinding binding : bindings) {
            String workspaceName = binding.getSpec().getWorkspace();
            if (!workspaces.containsKey(workspaceName)) {
                LOG.info(String.format("Found Workspace '%s' for '%s'", workspaceName, username));
                V1AnalyticsWorkspace workspace = workspaceApi.get(binding.getMetadata().getNamespace(), workspaceName).getObject();
                workspaces.put(workspaceName, new BoundWorkspace(workspace, binding));
            }
        }
        return workspaces.values().stream().collect(Collectors.toList());
    }

    /**
     * Patches a V1AnalyticsWorkspaceBinding in the specified namespace to
     * update its replica count.
     *
     * This method applies a JSON patch to the workspace binding's replicas
     * field. The patch replaces the current replica count with the provided
     * value.
     *
     * @param namespace The Kubernetes namespace where the workspace binding is
     * located
     * @param workspaceBindingName The name of the workspace binding to be
     * patched
     * @param value The new replica count to set for the workspace binding
     * @return The updated V1AnalyticsWorkspaceBinding object after the patch is
     * applied
     */
    public V1AnalyticsWorkspaceBinding patchWorkspaceBindingScale(String namespace, String workspaceBindingName, Integer value) {
        String jsonPatchString = String.format("[{\"op\":\"replace\", \"path\":\"/spec/replicas\", \"value\":%d}]", value);
        KubernetesApiResponse<V1AnalyticsWorkspaceBinding> response = workspaceBindingApi.patch(namespace, workspaceBindingName, "application/json-patch+json", new V1Patch(jsonPatchString));
        LOG.info(String.format("HttpStatusCode is: %s", response.getHttpStatusCode()));
        LOG.info(String.format("Status is: %s", response.getStatus()));
        return response.getObject();
    }

    /**
     * Sets the specified workspace binding as active for a user by scaling it
     * to 1 replica, and scales down all other workspace bindings to 0 replicas.
     *
     * This method first retrieves all workspace bindings for the given user,
     * then iterates through them to determine which should be active. Only the
     * binding with a name matching the provided workspaceBindingName will be
     * set to 1 replica (active), while all others will be set to 0 replicas
     * (inactive).
     *
     * @param workspaceBindingName The name of the workspace binding to set as
     * active
     * @param username The username of the user whose workspace bindings will be
     * modified
     */
    public void setActiveWorkspaceBindingForUser(String workspaceBindingName, String username) {

        List<V1AnalyticsWorkspaceBinding> bindings = getAllWorkspaceBindingsForUser(username);
        for (V1AnalyticsWorkspaceBinding binding : bindings) {
            String bindingName = binding.getMetadata().getName();
            Integer replicas = 0;
            if (bindingName.equals(workspaceBindingName)) {
                replicas = 1;
            }

            LOG.info(String.format("Setting replica=%d on binding '%s' for user '%s'", replicas, bindingName, username));
            patchWorkspaceBindingScale(binding.getMetadata().getNamespace(), bindingName, replicas);
        }
    }
}
