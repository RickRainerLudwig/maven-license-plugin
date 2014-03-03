/*
 * Copyright 2013 PureSol Technologies
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.puresoltechnologies.maven.plugins.license;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.settings.Settings;

/**
 * This class is a Maven Mojo to check the validity of licenses specified in the
 * dependencies of a maven module.
 * 
 * Used to configure injection of Plexus components by
 * MavenPluginManager.getConfiguredMojo(...) and special Maven objects as well:
 * 
 * mojoExecution org.apache.maven.plugin.MojoExecution project
 * org.apache.maven.project.MavenProject session
 * org.apache.maven.execution.MavenSession settings
 * org.apache.maven.settings.Settings plugin (Maven-3 only)
 * org.apache.maven.plugin.descriptor.PluginDescriptor
 * 
 * @author Rick-Rainer Ludwig
 */
@Mojo(//
name = "validate", //
requiresDirectInvocation = false, //
requiresProject = true, //
requiresReports = false, //
requiresOnline = false, //
inheritByDefault = true, //
threadSafe = true,//
requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,//
requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME//
)
@Execute(//
goal = "validate",//
phase = LifecyclePhase.VALIDATE//
)
public class ValidatorMojo extends AbstractMojo {

	private static final String TEST_SCOPE_NAME = "test";

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

	@Parameter(alias = "knownLicenses", required = true)
	private Set<KnownLicense> knownLicenses;

	/**
	 * This list contains the list of valid license names.
	 */
	@Parameter(alias = "validLicenses", required = true)
	private Set<ValidLicense> validLicenses;

	/**
	 * This list contains the regexp string for approved artifacts.
	 */
	@Parameter(alias = "approvedDependencies", required = true)
	private Set<ApprovedDependency> approvedDependencies;

	/**
	 * This parameter contains whether this Mojo shall fail fast (with first
	 * occurrence of an invalid license) or should do all the work first before
	 * failing. Default value is false.
	 */
	@Parameter(alias = "failFast", required = false, defaultValue = "false")
	private boolean failFast;

	/**
	 * This parameter contains whether or not the dependency should be checked
	 * recursively or not. Default is true.
	 */
	@Parameter(alias = "recursive", required = false, defaultValue = "true")
	private boolean recursive;

	/**
	 * Specifies whether or not the dependencies in test scope should be
	 * skipped. Default is false.
	 */
	@Parameter(alias = "skipTestScope", required = false, defaultValue = "false")
	private boolean skipTestScope;

	@Component
	private MavenProject project;

	@Component
	// for Maven 3 only
	private PluginDescriptor plugin;

	@Component
	private Settings settings;

	@Component
	private MavenProjectBuilder projectBuilder;

	private final Log log;

	public ValidatorMojo() {
		log = getLog();
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Set<Artifact> artifacts = retrieveArtifacts();
		validateArtifacts(artifacts);
	}

	/**
	 * This method retrievs all artifacts of the current Maven module.
	 * 
	 * @return A {@link Set} of {@link Artifact} is returned containing the
	 *         artifacts found.
	 */
	private Set<Artifact> retrieveArtifacts() {
		if (recursive) {
			@SuppressWarnings("unchecked")
			Set<Artifact> set = project.getArtifacts();
			return set;
		} else {
			@SuppressWarnings("unchecked")
			Set<Artifact> set = project.getDependencyArtifacts();
			return set;
		}
	}

	/**
	 * This method checks a set of artifacts for validity.
	 * 
	 * @param artifacts
	 *            is a {@link Set} of {@link Artifact} which is to be checked
	 *            for validity.
	 * @throws MojoExecutionException
	 *             is throw if the execution was faulty.
	 * @throws MojoFailureException
	 *             is thrown if an invalid license is found.
	 */
	private void validateArtifacts(Set<Artifact> artifacts)
			throws MojoExecutionException, MojoFailureException {
		boolean valid = true;
		for (Artifact artifact : artifacts) {
			if (log.isDebugEnabled()) {
				log.debug("Check " + getArtifactIdentifier(artifact) + ".");
			}
			if (!isArtifactValid(artifact)) {
				valid = false;
			}
		}
		if (!valid) {
			throw new MojoFailureException("Invalid license(s) was/were found!");
		}
	}

	/**
	 * This method checks the validity of a single artifact.
	 * 
	 * @param artifact
	 *            is the {@link Artifact} to be checked for validity.
	 * @return <code>true</code> is returned if the license is valid.
	 *         <code>false</code> is returned otherwise.
	 * @throws MojoFailureException
	 *             is thrown if {@link #failFast} is set to <code>true</code>
	 *             and the license is invalid to enforce the fail fast behavior
	 *             requested.
	 * @throws MojoExecutionException
	 *             is thrown in case of a faulty Maven run.
	 */
	private boolean isArtifactValid(Artifact artifact)
			throws MojoFailureException, MojoExecutionException {
		if (skipTestScope) {
			if (TEST_SCOPE_NAME.equals(artifact.getScope())) {
				logArtifactResult(artifact, ValidationResult.VALID,
						"test scope");
				return true;
			}
		}
		List<License> licenses = retrieveLicenses(artifact);
		boolean valid = true;
		if (licenses.size() == 0) {
			if (isApprovedDependency(artifact)) {
				String licenseName = findName(artifact);
				logArtifactResult(artifact, ValidationResult.VALID,
						"no license found, but dependency is approved with license "
								+ licenseName);
				return true;
			} else {
				logArtifactResult(artifact, ValidationResult.INVALID,
						"no license found and artifact is not approved");
				if (failFast) {
					throw new MojoFailureException(
							"Invalid license(s) was/were found!");
				}
				valid = false;
			}
		}
		for (License license : licenses) {
			String licenseName = findName(license);
			if (!isValidLicense(license)) {
				logArtifactResult(artifact, ValidationResult.INVALID,
						licenseName);
				if (failFast) {
					throw new MojoFailureException(
							"Invalid license(s) was/were found!");
				}
				valid = false;
			} else {
				logArtifactResult(artifact, ValidationResult.VALID, licenseName);
			}
		}
		return valid;
	}

	private String findName(Artifact artifact) throws MojoFailureException {
		String licenseKey = findLicenseKey(artifact);
		KnownLicense knownLicense = findKnownLicense(licenseKey);
		return knownLicense == null ? null : knownLicense.getName();
	}

	private String findLicenseKey(Artifact artifact)
			throws MojoFailureException {
		ApprovedDependency approvedDependency = findApprovedDependency(artifact);
		return approvedDependency == null ? null : approvedDependency.getKey();
	}

	/**
	 * This method returns the normalized name of the license.
	 * 
	 * @param license
	 *            is the {@link License} object which is to be looked up.
	 * @return A {@link String} is returned containing the normalized name.
	 * @throws MojoFailureException
	 *             is thrown if the normalized name cannot be looked up due to
	 *             missing configuration.
	 */
	private String findName(License license) throws MojoFailureException {
		String licenseKey = findLicenseKey(license);
		KnownLicense knownLicense = findKnownLicense(licenseKey);
		return knownLicense == null ? null : knownLicense.getName();
	}

	/**
	 * This method checks whether or not an artifact is already approved. For
	 * that the {@link #approvedDependencies} list is checked for a pattern
	 * matching the artifact provided as parameter to this method.
	 * 
	 * @param artifact
	 *            is the {@link Artifact} to be checked for approval.
	 * @return <code>true</code> is returned if the artifact is already
	 *         approved. <code>false</code> is returned otherwise.
	 * @throws MojoFailureException
	 */
	private boolean isApprovedDependency(Artifact artifact)
			throws MojoFailureException {
		return findApprovedDependency(artifact) != null;
	}

	private ApprovedDependency findApprovedDependency(Artifact artifact)
			throws MojoFailureException {
		String artifactIdentifier = getArtifactIdentifier(artifact);
		for (ApprovedDependency approvedDependency : approvedDependencies) {
			String approvedDependencyIdentifier = approvedDependency
					.getIdentifier();
			if ((approvedDependencyIdentifier == null)
					|| (approvedDependencyIdentifier.isEmpty())) {
				throw new MojoFailureException(
						"An approved dependency was found without identifier.");
			}
			if (Pattern.matches(approvedDependencyIdentifier,
					artifactIdentifier)) {
				return approvedDependency;
			}
		}
		return null;
	}

	/**
	 * This method is used to log results with Maven log {@link Log}.
	 * 
	 * @param artifact
	 *            is the {@link Artifact} which was checked for validity.
	 * @param validationResult
	 *            is the {@link ValidationResult} of the check.
	 * @param licenseOrApprovalMessage
	 *            is the message to be printed containing the license or the
	 *            appoval message.
	 */
	private void logArtifactResult(Artifact artifact,
			ValidationResult validationResult, String licenseOrApprovalMessage) {
		switch (validationResult) {
		case VALID:
			log.info("License check for artifact '"
					+ getArtifactIdentifier(artifact) + "': "
					+ validationResult.name().toLowerCase() + " ("
					+ licenseOrApprovalMessage + ")");
			break;
		case INVALID:
			log.error("License check for artifact '"
					+ getArtifactIdentifier(artifact) + "': "
					+ validationResult.name().toLowerCase() + " ("
					+ licenseOrApprovalMessage + ")");
			break;
		default:
			log.error("License check for artifact '"
					+ getArtifactIdentifier(artifact) + "': "
					+ validationResult.name().toLowerCase() + " ("
					+ licenseOrApprovalMessage + ")");
		}
	}

	/**
	 * This method returns the license of an artifact.
	 * 
	 * @param artifact
	 *            is the {@link Artifact} where the license is to be read from.
	 * @return A {@link List} of {@link License} is returned containing the
	 *         licenses specified in the artifact.
	 * @throws MojoExecutionException
	 *             is throws if the Maven run is faulty.
	 */
	private List<License> retrieveLicenses(Artifact artifact)
			throws MojoExecutionException {
		try {
			MavenProject repository = projectBuilder.buildFromRepository(
					artifact, remoteArtifactRepositories, localRepository);
			@SuppressWarnings("unchecked")
			List<License> licenses = repository.getLicenses();
			return licenses;
		} catch (ProjectBuildingException e) {
			throw new MojoExecutionException("Cannot create repository.", e);
		}
	}

	/**
	 * This method returns the artifact identifier. The identifier returned here
	 * is a single string containing the artifactId, groupId and version
	 * separated by colons:
	 * 
	 * ${artifactId}:${groupId}:${version}
	 * 
	 * @param artifact
	 *            is the {@link Artifact} of which the identifier is to be
	 *            built.
	 * @return A {@link String} is returned containing the identifier.
	 */
	private String getArtifactIdentifier(Artifact artifact) {
		String groupId = artifact.getGroupId();
		String artifactId = artifact.getArtifactId();
		String version = artifact.getVersion();
		return groupId + ":" + artifactId + ":" + version;
	}

	/**
	 * This method checks the validity of a single license.
	 * 
	 * @param license
	 *            is the {@link License} to be checked.
	 * @return <code>true</code> is returned if the license is valid.
	 *         <code>false</code> is returned otherwise.
	 * @throws MojoFailureException
	 */
	private boolean isValidLicense(License license) throws MojoFailureException {
		return findValidLicense(license) != null;
	}

	private ValidLicense findValidLicense(License license)
			throws MojoFailureException {
		String licenseName = license.getName();
		if ((licenseName == null) || (licenseName.isEmpty())) {
			return null;
		}
		for (ValidLicense validLicense : validLicenses) {
			String validLicenseName = validLicense.getName();
			if ((validLicenseName == null) || (validLicenseName.isEmpty())) {
				throw new MojoFailureException(
						"A valid license without name was found.");
			}
			if (validLicenseName.equals(licenseName)) {
				return validLicense;
			}
		}
		return null;
	}

	/**
	 * This method find the license key for the found license object.
	 * 
	 * @param license
	 *            is the {@link License} object to be looked up.
	 * @return A {@link String} is returned containing the license key.
	 * @throws MojoFailureException
	 *             is thrown in case of missing configuration.
	 */
	private String findLicenseKey(License license) throws MojoFailureException {
		ValidLicense validLicense = findValidLicense(license);
		return validLicense == null ? null : validLicense.getKey();
	}

	/**
	 * This method looks for a known license for a given license key.
	 * 
	 * @param licenseKey
	 *            is the key of the license.
	 * @return A {@link KnownLicense} object is returned containing the license.
	 *         <code>null</code> is returned if no license with the given key
	 *         was found.
	 */
	private KnownLicense findKnownLicense(String licenseKey) {
		for (KnownLicense knownLicense : knownLicenses) {
			if (knownLicense.getKey().equals(licenseKey)) {
				return knownLicense;
			}
		}
		return null;
	}
}
