/**
 * Represents a workspace bound to a binding in the analytics workspace management system.
 * This class encapsulates the relationship between an analytics workspace and its binding,
 * providing access to both components through getter methods.
 *
 * <p>
 * This class is used in the context of required actions for user authentication
 * and workspace access management.</p>
 */
package awms.lscsde.requiredaction;

import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspace;
import io.github.lsc.sde.analytics.workspace.management.models.V1AnalyticsWorkspaceBinding;

/**
 * Represents a workspace that has been bound to a user or group.
 *
 * This class encapsulates both an analytics workspace and its associated
 * binding information, providing a convenient way to access both entities
 * together when processing workspace allocations.
 *
 * @see V1AnalyticsWorkspace
 * @see V1AnalyticsWorkspaceBinding
 */
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
