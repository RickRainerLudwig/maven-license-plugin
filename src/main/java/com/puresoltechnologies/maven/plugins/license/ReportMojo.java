package com.puresoltechnologies.maven.plugins.license;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.sink.Sink;

import com.puresoltechnologies.maven.plugins.license.internal.DependencyTree;
import com.puresoltechnologies.maven.plugins.license.internal.IOUtilities;
import com.puresoltechnologies.maven.plugins.license.parameter.ArtifactInformation;
import com.puresoltechnologies.maven.plugins.license.parameter.KnownLicense;
import com.puresoltechnologies.maven.plugins.license.parameter.ValidLicense;
import com.puresoltechnologies.maven.plugins.license.parameter.ValidationResult;

@SuppressWarnings("deprecation")
@Mojo(//
name = "generate-report", //
requiresDirectInvocation = false, //
requiresProject = true, //
requiresReports = true, //
requiresOnline = false, //
inheritByDefault = true, //
threadSafe = true,//
requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,//
requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME//
)
@Execute(//
goal = "generate-report",//
phase = LifecyclePhase.GENERATE_SOURCES//
)
public class ReportMojo extends AbstractValidationMojo implements MavenReport {

	/**
	 * Specifies the destination directory where documentation is to be saved
	 * to.
	 */
	@Parameter(property = "destDir", alias = "destDir", defaultValue = "${project.build.directory}/licenses", required = true)
	protected File outputDirectory;

	@Parameter(alias = "resultsDirectory", required = false, defaultValue = "${project.build.directory}/licenses")
	private File resultsDirectory;

	private final Log log;
	private final Map<ArtifactInformation, List<ValidationResult>> results = new HashMap<>();
	private DependencyTree dependencyTree = null;
	private boolean recursive = true;
	private boolean skipTestScope = false;

	public ReportMojo() {
		log = getLog();
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			RenderingContext context = new RenderingContext(outputDirectory,
					getOutputName() + ".html");
			SiteRendererSink sink = new SiteRendererSink(context);
			Locale locale = Locale.getDefault();
			generate(sink, locale);
		} catch (MavenReportException e) {
			throw new MojoFailureException("An error has occurred in "
					+ getName(Locale.ENGLISH) + " report generation", e);
		}
	}

	@Override
	public boolean canGenerateReport() {
		return true;
	}

	@Override
	public void generate(Sink sink, Locale locale) throws MavenReportException {
		try {
			readSettings();
			readResults();
			dependencyTree = loadArtifacts(recursive, skipTestScope);
			generate(sink);
		} catch (MojoExecutionException e) {
			throw new MavenReportException("Could not generate report.", e);
		}
	}

	private void readSettings() throws MojoExecutionException {
		File file = IOUtilities.getSettingsFile(log, resultsDirectory);
		try (FileInputStream fileOutputStream = new FileInputStream(file);
				InputStreamReader propertiesReader = new InputStreamReader(
						fileOutputStream, Charset.defaultCharset())) {
			Properties properties = new Properties();
			properties.load(propertiesReader);
			recursive = Boolean.valueOf(properties.getProperty("recursive",
					"true"));
			skipTestScope = Boolean.valueOf(properties.getProperty(
					"skipTestScope", "false"));
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Could not write settings.properties.", e);
		}
	}

	private void readResults() throws MojoExecutionException {
		File resultsFile = IOUtilities.getResultsFile(log, resultsDirectory);
		try (FileInputStream fileInputStream = new FileInputStream(resultsFile);
				InputStreamReader inputStreamReader = new InputStreamReader(
						fileInputStream, Charset.defaultCharset());
				BufferedReader bufferedReader = new BufferedReader(
						inputStreamReader);) {
			results.clear();
			for (;;) {
				ValidationResult validationResult = IOUtilities
						.readResult(bufferedReader);
				if (validationResult == null) {
					break;
				}
				log.debug(" ** "
						+ validationResult.getArtifactInformation().toString());
				ArtifactInformation artifactInformation = validationResult
						.getArtifactInformation();
				List<ValidationResult> artifactResults = results
						.get(artifactInformation);
				if (artifactResults == null) {
					artifactResults = new ArrayList<>();
					results.put(artifactInformation, artifactResults);
				}
				artifactResults.add(validationResult);
			}
			for (ArtifactInformation key : results.keySet()) {
				for (ValidationResult validationResult2 : results.get(key)) {
					log.debug("Result found: "
							+ validationResult2.getArtifactInformation()
									.toString());
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Could not read license validation resultsf from file '"
							+ resultsFile + "'.");
		}
	}

	private void generate(Sink sink) throws MavenReportException {
		log.info("Creating report for licenses.");
		log.info(getReportOutputDirectory().getPath());
		try {
			generateHead(sink);
			generateBody(sink);
			sink.flush();
		} finally {
			sink.close();
		}
	}

	private void generateHead(Sink sink) {
		sink.head();
		sink.title();
		sink.text("Licenses Report");
		sink.title_();
		sink.head_();
	}

	private void generateBody(Sink sink) throws MavenReportException {
		sink.body();
		sink.section1();
		sink.sectionTitle1();
		sink.text("Licenses Report");
		sink.sectionTitle1_();
		sink.paragraph();
		sink.text("This report contains an overview of all licenses related to the project and a validation whether these licenses are approved or not.");
		sink.paragraph_();

		sink.section2();
		sink.sectionTitle2();
		sink.text("Directly Used Licenses");
		sink.sectionTitle2_();
		generateDirectDependencyTable(sink);
		sink.section2_();

		sink.section2();
		sink.sectionTitle2();
		sink.text("Transitively Used Licenses");
		sink.sectionTitle2_();
		generateTransitiveDependencyTable(sink);
		sink.section2_();

		sink.section2();
		sink.sectionTitle2();
		sink.text("Dependency Hierarchy");
		sink.sectionTitle2_();
		generateDependencyHierachy(sink);
		sink.section2_();

		sink.section1_();

		sink.body_();
	}

	private void generateDirectDependencyTable(Sink sink)
			throws MavenReportException {
		sink.paragraph();
		sink.text("This section contains a list of all licenses which are directly referenced to with dependencies of this maven project.");
		sink.paragraph_();

		sink.table();
		sink.tableCaption();
		sink.text("Licenses");
		sink.tableCaption_();
		generateTableHead(sink);
		generateDirectDependenciesTableContent(sink);
		sink.table_();
	}

	private void generateTableHead(Sink sink) {
		sink.tableRow();
		sink.tableHeaderCell();
		sink.text("License from Artifact");
		sink.tableHeaderCell_();
		sink.tableHeaderCell();
		sink.text("License");
		sink.tableHeaderCell_();
		sink.tableHeaderCell();
		sink.text("Validation");
		sink.tableHeaderCell_();
		sink.tableRow_();
	}

	private void generateDirectDependenciesTableContent(Sink sink)
			throws MavenReportException {
		List<DependencyTree> dependencies = dependencyTree.getDependencies();
		Map<String, ValidationResult> directLicenses = new HashMap<>();
		getLicenses(dependencies, directLicenses);

		for (Entry<String, ValidationResult> license : directLicenses
				.entrySet()) {
			String originalLicenseName = license.getKey();
			ValidationResult validationResult = license.getValue();
			sink.tableRow();
			sink.tableCell();
			sink.text(originalLicenseName);
			sink.tableCell_();
			sink.tableCell();
			sink.link(validationResult.getLicense().getUrl().toString());
			sink.text(validationResult.getLicense().getName());
			sink.link_();
			sink.tableCell_();
			sink.tableCell();
			sink.text(validationResult.isValid() ? "valid" : "invalid");
			sink.tableCell_();
			sink.tableRow_();
		}
	}

	private void getLicenses(List<DependencyTree> dependencyTree,
			Map<String, ValidationResult> licenses) {
		for (DependencyTree dependency : dependencyTree) {
			ArtifactInformation artifactInformation = new ArtifactInformation(
					dependency.getArtifact());
			List<ValidationResult> validationResults = results
					.get(artifactInformation);
			for (ValidationResult validationResult : validationResults) {
				String originalLicenseName = validationResult
						.getOriginalLicense().getName();
				if (!licenses.containsKey(originalLicenseName)) {
					licenses.put(originalLicenseName, validationResult);
				}
			}

		}
	}

	private void generateTransitiveDependencyTable(Sink sink)
			throws MavenReportException {
		sink.paragraph();
		sink.text("This section contains a list of all licenses which are "
				+ "not directly referenced to with dependencies of this "
				+ "maven project. All these licenses are coming in "
				+ "transitively via dependencies of the direct project "
				+ "dependencies.");
		sink.paragraph_();

		sink.table();
		sink.tableCaption();
		sink.text("Licenses");
		sink.tableCaption_();
		generateTableHead(sink);
		generateTransitiveDependenciesTableContent(sink);
		sink.table_();
	}

	private void generateTransitiveDependenciesTableContent(Sink sink)
			throws MavenReportException {
		List<DependencyTree> dependencies = dependencyTree.getDependencies();
		Map<String, ValidationResult> transitiveLicenses = new HashMap<>();
		for (DependencyTree dependency : dependencies) {
			for (DependencyTree dependency2 : dependency.getDependencies()) {
				getLicenses(dependency2.getAllDependencies(),
						transitiveLicenses);
			}
		}

		for (Entry<String, ValidationResult> license : transitiveLicenses
				.entrySet()) {
			String originalLicenseName = license.getKey();
			ValidationResult validationResult = license.getValue();
			sink.tableRow();
			sink.tableCell();
			sink.text(originalLicenseName);
			sink.tableCell_();
			sink.tableCell();
			sink.link(validationResult.getLicense().getUrl().toString());
			sink.text(validationResult.getLicense().getName());
			sink.link_();
			sink.tableCell_();
			sink.tableCell();
			sink.text(validationResult.isValid() ? "valid" : "invalid");
			sink.tableCell_();
			sink.tableRow_();
		}
	}

	/**
	 * The hierarchy of the dependencies.
	 * 
	 * @param sink
	 */
	private void generateDependencyHierachy(Sink sink) {
		sink.paragraph();
		sink.text("This section contains the full hierarchy of dependencies, its licenses and their validation result.");
		sink.paragraph_();
		generateDependency(sink, dependencyTree);
	}

	private void generateDependency(Sink sink, DependencyTree parentDependency) {
		sink.list();
		for (DependencyTree dependency : parentDependency.getDependencies()) {
			sink.listItem();
			ArtifactInformation artifactInformation = new ArtifactInformation(
					dependency.getArtifact());
			log.debug("Hierarchy for " + artifactInformation.toString());
			sink.bold();
			sink.text(artifactInformation.toString());
			sink.bold_();
			for (ValidationResult result : results.get(artifactInformation)) {
				KnownLicense license = result.getLicense();
				ValidLicense originalLicense = result.getOriginalLicense();
				String valid = result.isValid() ? "valid" : "invalid";
				sink.lineBreak();
				sink.italic();
				sink.text(valid);
				sink.text(": ");
				sink.text(originalLicense.getName());
				sink.text(" / ");
				sink.link(license.getUrl().toString());
				sink.text(license.getName());
				sink.link_();
				sink.italic_();
			}
			sink.lineBreak();
			generateDependency(sink, dependency);
			sink.listItem_();
		}
		sink.list_();
	}

	@Override
	public String getCategoryName() {
		return CATEGORY_PROJECT_REPORTS;
	}

	@Override
	public String getDescription(Locale locale) {
		return "Reports all licenses for all dependencies for audit.";
	}

	@Override
	public String getName(Locale locale) {
		return "Licenses Report";
	}

	@Override
	public String getOutputName() {
		return "dependency-licenses-report";
	}

	@Override
	public File getReportOutputDirectory() {
		return outputDirectory;
	}

	@Override
	public boolean isExternalReport() {
		return false;
	}

	@Override
	public void setReportOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

}
