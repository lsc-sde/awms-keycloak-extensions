package awms.lscsde.requiredaction;

import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspace;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceBinding;

public class BoundWorkspace {
    protected V1AnalyticsWorkspace _workspace;
    protected V1AnalyticsWorkspaceBinding _binding;

    public BoundWorkspace(V1AnalyticsWorkspace workspace, V1AnalyticsWorkspaceBinding binding) {
        _workspace = workspace;
        _binding = binding;
    }

    public V1AnalyticsWorkspace getWorkspace() {
        return _workspace;
    }

    public V1AnalyticsWorkspaceBinding getBinding() {
        return _binding;
    }
}