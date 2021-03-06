package com.puresoltechnologies.maven.plugins.license.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.License;

/**
 * This class contains a dependency tree with all its transitive dependencies.
 *
 * @author Rick-Rainer Ludwig
 *
 */
public class DependencyTree implements Iterable<DependencyTree> {

    private final List<Dependency> dependencies = new ArrayList<>();

    /**
     * Contains the child dependencies of the current node.
     */
    private final List<DependencyTree> children = new ArrayList<>();

    /**
     * Contains the parent dependency.
     */
    private DependencyTree parent;

    /**
     * Is a reference to the {@link Artifact} which represents the current node.
     */
    private final Artifact artifact;

    /**
     * This list contains all {@link License}s related to the {@link #artifact}.
     */
    private final List<License> licenses;

    /**
     * Initial value constructor.
     *
     * @param artifact is the {@link Artifact}.
     * @param licenses is the {@link List} of {@link License}.
     */
    public DependencyTree(Artifact artifact, List<Dependency> dependencies, List<License> licenses) {
        super();
        this.artifact = artifact;
        this.dependencies.addAll(dependencies);
        this.licenses = licenses;
    }

    /**
     * Returns the parent of this dependency.
     *
     * @return A {@link DependencyTree} is returned.
     */
    public DependencyTree getParent() {
        return parent;
    }

    /**
     * Returns the {@link Artifact}.
     *
     * @return A {@link Artifact} is returned.
     */
    public Artifact getArtifact() {
        return artifact;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    /**
     * Returns the license of this node.
     *
     * @return A {@link License} is returned.
     */
    public List<License> getLicenses() {
        return licenses;
    }

    /**
     * This method adds a new dependency.
     *
     * @param childNode
     */
    public void addChildNode(DependencyTree childNode) {
        children.add(childNode);
        childNode.setParent(this);
    }

    /**
     * This method sets a parent.
     *
     * @param parent is the {@link DependencyTree} parent node.
     */
    private void setParent(DependencyTree parent) {
        this.parent = parent;
    }

    /**
     * Returns the dependencies of the current {@link DependencyTree}.
     *
     * @return A {@link List} of {@link DependencyTree} is returned with the
     *         dependencies.
     */
    public List<DependencyTree> getChildren() {
        return children;
    }

    @Override
    public Iterator<DependencyTree> iterator() {
        List<DependencyTree> all = getAllNodes();
        return all.iterator();
    }

    /**
     * This method puts all dependencies into a {@link List}.
     *
     * @return A {@link List} of {@link DependencyTree} is returned.
     */
    public List<DependencyTree> getAllNodes() {
        List<DependencyTree> all = new ArrayList<>();
        addDependencies(all, this);
        return all;
    }

    /**
     * Adds all dependency nodes to a list for {@link #iterator()}.
     *
     * @param all    is the list to add all dependencies to.
     * @param parent is the parent node.
     */
    private void addDependencies(List<DependencyTree> all, DependencyTree parent) {
        all.add(parent);
        for (DependencyTree dependency : parent.getChildren()) {
            addDependencies(all, dependency);
        }
    }
}
