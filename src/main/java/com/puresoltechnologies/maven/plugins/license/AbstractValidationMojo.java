package com.puresoltechnologies.maven.plugins.license;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.settings.Settings;

import com.puresoltechnologies.maven.plugins.license.internal.ArtifactUtilities;
import com.puresoltechnologies.maven.plugins.license.internal.DependencyTree;
import com.puresoltechnologies.maven.plugins.license.internal.DependencyUtilities;

/**
 * This abstract class provides basic functionality for all license validations.
 *
 * @author Rick-Rainer Ludwig
 */
public abstract class AbstractValidationMojo extends AbstractMojo {

    @Component
    private MavenProject mavenProject;

    @Component
    private PluginDescriptor plugin;

    @Component
    private Settings settings;

    @Component
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * This field contains the remote artifact repositories.
     */
    @Parameter(required = false, defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private List<ArtifactRepository> remoteArtifactRepositories;

    /**
     * This field contains the local repository.
     */
    @Parameter(required = false, defaultValue = "${localRepository}", readonly = true)
    private ArtifactRepository localRepository;

    /**
     * This method returns the current {@link MavenProject}.
     *
     * @return A {@link MavenProject} object is returned referencing the current
     *         Maven project.
     */
    protected final MavenProject getMavenProject() {
        return mavenProject;
    }

    /**
     * This method retrieves all artifacts of the current Maven module.
     *
     * @param recursive         is to be set to <code>true</code> is the
     *                          dependencies shall be loaded recursively.
     *                          <code>false</code> is set if wanted otherwise.
     * @param skipTestScope     is to be set to <code>true</code> is artifacts in
     *                          test scope are to be skipped and neglected.
     *                          <code>false</code> is set if wanted otherwise.
     * @param skipProvidedScope is to be set to <code>true</code> if artifacts in
     *                          provided scope are to be skipped and neglected.
     *                          <code>false</code> is set if wanted otherwise.
     * @param skipOptionals     is to be set to <code>true</code> if artifacts in
     *                          optional scope are to be skipped and neglected.
     *                          <code>false</code> is set if wanted otherwise.
     * @return A {@link DependencyTree} is returned containing the artifacts found.
     * @throws MojoExecutionException is thrown in cases of issues.
     */
    protected DependencyTree loadArtifacts(boolean recursive, boolean skipTestScope, boolean skipProvidedScope,
            boolean skipOptionals) throws MojoExecutionException {
        DependencyTree dependencyTree = createDependencyTreeNode(mavenProject);
        loadArtifacts(0, dependencyTree, mavenProject, recursive, skipTestScope, skipProvidedScope, skipOptionals);
        return dependencyTree;
    }

    /**
     * Loads the artifact recursively.
     *
     * @param artifact
     * @param recursive     specified whether all dependencies should be loaded
     *                      recursively.
     * @param skipTestScope specified whether to skip test scoped artifacts or not.
     * @return A {@link DependencyTree} object is returned.
     * @throws MojoExecutionException is thrown if anything unexpected goes wrong.
     */
    private void loadArtifacts(int depth, DependencyTree dependencyTreeNode, MavenProject project, boolean recursive,
            boolean skipTestScope, boolean skipProvidedScope, boolean skipOptionals) throws MojoExecutionException {
        if ((recursive) || (depth == 0)) {
            processDependencies(depth, dependencyTreeNode, project, recursive, skipTestScope, skipProvidedScope,
                    skipOptionals);
        }
    }

    private DependencyTree createDependencyTreeNode(MavenProject project) {
        @SuppressWarnings("unchecked")
        List<License> licenses = project.getLicenses();
        DependencyTree dependencyTree = new DependencyTree(project.getArtifact(), licenses);
        return dependencyTree;
    }

    private void processDependencies(int depth, DependencyTree dependencyTree, MavenProject project, boolean recursive,
            boolean skipTestScope, boolean skipProvidedScope, boolean skipOptionals) throws MojoExecutionException {
        @SuppressWarnings("unchecked")
        List<Dependency> dependencies = project.getDependencies();
        if (dependencies != null) {
            for (Dependency dependency : dependencies) {
                processDependency(depth, dependencyTree, project, dependency, recursive, skipTestScope,
                        skipProvidedScope, skipOptionals);
            }
        }
    }

    private void processDependency(int depth, DependencyTree dependencyTree, MavenProject project,
            Dependency dependency, boolean recursive, boolean skipTestScope, boolean skipProvidedScope,
            boolean skipOptionals) throws MojoExecutionException {
        Log log = getLog();
        StringBuffer buffer = new StringBuffer();
        if (log.isDebugEnabled()) {
            for (int i = 0; i < depth; i++) {
                buffer.append("    ");
            }
            buffer.append("\\-> ");
            log.debug(buffer.toString() + ArtifactUtilities.toString(dependency));
        }
        if (skipTestScope && Artifact.SCOPE_TEST.equals(dependency.getScope())) {
            if (log.isDebugEnabled()) {
                log.debug(buffer.toString() + " >> test scope is skipped");
            }
            return;
        }
        if (skipProvidedScope && Artifact.SCOPE_PROVIDED.equals(dependency.getScope())) {
            if (log.isDebugEnabled()) {
                log.debug(buffer.toString() + " >> provided scope is skipped");
            }
            return;
        }
        if (skipOptionals && dependency.isOptional()) {
            if (log.isDebugEnabled()) {
                log.debug(buffer.toString() + " >> optional is skipped");
            }
            return;
        }
        if (hasCycle(dependencyTree, dependency)) {
            if (log.isDebugEnabled()) {
                log.warn(buffer.toString() + " >> cylce found and needs to be skipped");
            }
            return;
        }
        Artifact dependencyArtifact = DependencyUtilities.buildArtifact(project.getArtifact(), dependency);
        try {
            MavenProject dependencyProject = mavenProjectBuilder.buildFromRepository(dependencyArtifact,
                    remoteArtifactRepositories, localRepository);
            DependencyTree childDependencyTree = createDependencyTreeNode(dependencyProject);
            dependencyTree.addDependency(childDependencyTree);
            loadArtifacts(depth + 1, childDependencyTree, dependencyProject, recursive, skipTestScope,
                    skipProvidedScope, skipOptionals);
        } catch (ProjectBuildingException e) {
            log.warn("Could not load artifacts recursively. For artifact '"
                    + ArtifactUtilities.toString(project.getArtifact()) + "' the project creation failed.", e);
        }
    }

    private boolean hasCycle(DependencyTree dependencyTree, Dependency dependency) {
        Log log = getLog();
        List<DependencyTree> path = new ArrayList<>();
        while (dependencyTree != null) {
            path.add(0, dependencyTree);
            Artifact artifact = dependencyTree.getArtifact();
            String artifactString = ArtifactUtilities.toString(artifact);
            String dependencyString = ArtifactUtilities.toString(dependency);
            if (artifactString.equals(dependencyString)) {
                log.debug("WARNING! Cycle detected for '" + artifactString + "':");
                while (dependencyTree != null) {
                    path.add(0, dependencyTree);
                    dependencyTree = dependencyTree.getParent();
                }
                for (int i = 0; i < path.size(); i++) {
                    DependencyTree node = path.get(i);
                    StringBuffer buffer = new StringBuffer();
                    for (int col = 0; col < i; col++) {
                        buffer.append("    ");
                    }
                    buffer.append("\\-> ");
                    buffer.append(ArtifactUtilities.toString(node.getArtifact()));
                    log.warn(buffer.toString());
                }
                StringBuffer buffer = new StringBuffer();
                buffer.append(" !! ");
                for (DependencyTree element : path) {
                    buffer.append("    ");
                }
                buffer.append("\\-> ");
                buffer.append(artifactString);
                buffer.append(" !! ");
                log.warn(buffer.toString());
                return true;
            }
            dependencyTree = dependencyTree.getParent();
        }
        return false;
    }
}
