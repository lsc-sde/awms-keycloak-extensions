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
    
    public WorkspaceKubernetesClient() throws IOException {
        apiClient = ClientBuilder.cluster().build();
        Configuration.setDefaultApiClient(apiClient);
        workspaceApi = new GenericKubernetesApi<>(V1AnalyticsWorkspace.class, V1AnalyticsWorkspaceList.class, API_GROUP, API_VERSION, WORKSPACE_PLURAL, apiClient);
        workspaceBindingApi = new GenericKubernetesApi<>(V1AnalyticsWorkspaceBinding.class, V1AnalyticsWorkspaceBindingList.class, API_GROUP, API_VERSION, WORKSPACE_BINDING_PLURAL, apiClient);
    }

    public V1AnalyticsWorkspaceBindingList getWorkspaceBindingsForUserWithUserLabel(String username) {
        String usernameAsLabel = username.replaceAll("[^0-9a-z.]+", "___"); 
        LOG.info(String.format("Fetching Workspace Bindings With username label '%s'", usernameAsLabel));
        ListOptions listOptions = new ListOptions();
        listOptions.setLabelSelector(String.format("xlscsde.nhs.uk/username=%s", usernameAsLabel));
        KubernetesApiResponse<V1AnalyticsWorkspaceBindingList> response = workspaceBindingApi.list(listOptions);
        return response.getObject();
    }

    public V1AnalyticsWorkspaceBindingList getWorkspaceBindingsWithoutUserLabel() {
        ListOptions listOptions = new ListOptions();
        LOG.info("Fetching Workspace Bindings Without username label");
        listOptions.setLabelSelector("!xlscsde.nhs.uk/username");
        KubernetesApiResponse<V1AnalyticsWorkspaceBindingList> response = workspaceBindingApi.list(listOptions);
        return response.getObject();
    }

    public List<V1AnalyticsWorkspaceBinding> getAllWorkspaceBindingsForUser(String username) {
        LOG.info(String.format("Fetching All Workspace Bindings for '%s'", username));
        List<V1AnalyticsWorkspaceBinding> bindings = new ArrayList<V1AnalyticsWorkspaceBinding>();
        
        for(V1AnalyticsWorkspaceBinding binding : getWorkspaceBindingsForUserWithUserLabel(username).getItems()) {
            bindings.add(binding);
        }
        
        for(V1AnalyticsWorkspaceBinding binding : getWorkspaceBindingsWithoutUserLabel().getItems()) {
            if(binding.getSpec().getUsername() == username) {
                bindings.add(binding);
            }
        }

        return bindings.stream().distinct().collect(Collectors.toList());
    }

    public List<BoundWorkspace> getAllWorkspacesForUser(String username) {
        LOG.info(String.format("Fetching All Workspaces for '%s'", username));
        HashMap<String, BoundWorkspace> workspaces = new HashMap<String, BoundWorkspace>();
        List<V1AnalyticsWorkspaceBinding> bindings = getAllWorkspaceBindingsForUser(username);
        for(V1AnalyticsWorkspaceBinding binding : bindings) {
            String workspaceName = binding.getSpec().getWorkspace();
            if(!workspaces.containsKey(workspaceName)) {
                LOG.info(String.format("Found Workspace '%s' for '%s'", workspaceName, username));
                V1AnalyticsWorkspace workspace = workspaceApi.get(DEFAULT_NAMESPACE, workspaceName).getObject();
                workspaces.put(workspaceName, new BoundWorkspace(workspace, binding));
            }
        }
        return workspaces.values().stream().collect(Collectors.toList());
    }

    public V1AnalyticsWorkspaceBinding patchWorkspaceBindingScale(String workspaceBindingName, Integer value){
        String jsonPatchString = String.format("[{\"op\":\"replace\", \"path\":\"/spec/replicas\", \"value\":%d}]", value);
        KubernetesApiResponse<V1AnalyticsWorkspaceBinding> response = workspaceBindingApi.patch(DEFAULT_NAMESPACE, workspaceBindingName, "application/json-patch+json", new V1Patch(jsonPatchString));
        LOG.info(String.format("HttpStatusCode is: %s", response.getHttpStatusCode()));
        LOG.info(String.format("Status is: %s", response.getStatus()));
        return response.getObject();
    }

    public void setActiveWorkspaceBindingForUser(String workspaceBindingName, String username){
        List<V1AnalyticsWorkspaceBinding> bindings = getAllWorkspaceBindingsForUser(username);
        for(V1AnalyticsWorkspaceBinding binding : bindings) {
            String bindingName = binding.getMetadata().getName();
            Integer replicas = 0;
            if(bindingName.equals(workspaceBindingName)){
                replicas = 1;
            }

            LOG.info(String.format("Setting replica=%d on binding '%s' for user '%s'", replicas, bindingName, username));
            patchWorkspaceBindingScale(bindingName, replicas);
        }
    }
}