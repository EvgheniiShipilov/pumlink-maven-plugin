package tech.pumlink;

import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultMavenProjectBuilder;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.ProjectBuildingException;

@Mojo(name = "pumlink", defaultPhase = LifecyclePhase.COMPILE)
public class PumlinkMojo extends AbstractMojo {

  public static final DefaultMavenProjectBuilder MAVEN_PROJECT_BUILDER =
      new DefaultMavenProjectBuilder();

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  MavenProject project;

  @Parameter(defaultValue = "${project.groupId}", required = true, readonly = true)
  String filterGroupId;

  // XXX: Add the possibility to enable/disable relaxed naming that would ignore module suffixes
  // like `-api` and `-logic`.
  @Parameter(defaultValue = "true", readonly = true)
  Boolean relaxedNaming;

  private static final ProjectBuilderConfiguration PROJECT_BUILDER_CONFIGURATION =
      new DefaultProjectBuilderConfiguration();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    File pumlSource = new File(project.getBasedir() + "/project.puml");
    if (!pumlSource.exists()) {
      getLog().warn("No PUML defined for " + project.getName());
      return;
    }

    @SuppressWarnings("unchecked")
    List<Dependency> dependencies = project.getDependencies();

    Set<Dependency> outgoingDependencies = getOutgoingDependencies(dependencies);
    Set<String> expectedDependencyArtifactIds =
        outgoingDependencies.stream().map(Dependency::getArtifactId).collect(Collectors.toSet());
    getLog().info("Expected outgoing dependencies: " + expectedDependencyArtifactIds);

    Set<MavenProject> callerProjects = getIncomingDependencies();
    Set<String> expectedProjectNames =
        callerProjects.stream().map(MavenProject::getName).collect(Collectors.toSet());
    getLog().info("Expected incoming dependencies: " + expectedProjectNames);

    Set<String> expectedPumlLines = new HashSet<>();
    expectedPumlLines.addAll(expectedDependencyArtifactIds);
    expectedPumlLines.addAll(expectedProjectNames);

    validatePuml(expectedPumlLines, pumlSource);
  }

  private void validatePuml(Set<String> expectedPumlComponents, File pumlSource) {
    List<String> allPumlLines;
    try {
      allPumlLines = Files.readAllLines(pumlSource.toPath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Set<String> missingComponents =
        expectedPumlComponents.stream()
            .filter(component -> !relationExists(allPumlLines, component))
            .peek(component -> getLog().error("Component not present in diagram: " + component))
            .collect(Collectors.toSet());

    if (!missingComponents.isEmpty()) {
      throw new IllegalStateException("PlantUML not in sync with actual project state!");
    }
  }

  private boolean relationExists(List<String> allPumlLines, String component) {
    return allPumlLines.stream()
        .anyMatch(line -> line.contains(project.getName()) && line.contains(component));
  }

  private Set<MavenProject> getIncomingDependencies() {
    return getAllRelatedProjects(project.getParent()).stream()
        .filter(this::isRelated)
        .collect(Collectors.toSet());
  }

  @SuppressWarnings("unchecked")
  private boolean isRelated(MavenProject mavenProject) {
    return mavenProject.getDependencies().stream()
        .map(Dependency.class::cast)
        .anyMatch(
            dependency ->
                ((Dependency) dependency).getArtifactId().equals(project.getArtifactId())
                    && ((Dependency) dependency).getGroupId().equals(project.getGroupId()));
  }

  private List<MavenProject> getAllRelatedProjects(MavenProject targetProject) {
    // If project has no modules return itself.
    @SuppressWarnings("unchecked")
    List<String> modulesNames = targetProject.getModules();
    if (modulesNames.isEmpty()) {
      getLog().info(targetProject.getName() + " has no modules");
      return singletonList(targetProject);
    }
    getLog().info("Checking pom.xml in path: " + targetProject.getBasedir());
    // Get child modules recursively.
    return modulesNames.stream()
        .map(moduleName -> project.getBasedir() + "/" + moduleName + "pom.xml")
        .map(File::new)
        .filter(File::exists)
        .peek(pom -> getLog().debug("Found nested pom.xml: " + pom.getPath()))
        .map(PumlinkMojo::buildMavenProject)
        .flatMap(mavenProject -> getAllRelatedProjects(mavenProject).stream())
        .collect(Collectors.toList());
  }

  private Set<Dependency> getOutgoingDependencies(List<Dependency> dependencies) {
    return dependencies.stream()
        .filter(dependency -> dependency.getGroupId().equals(filterGroupId))
        .collect(Collectors.toSet());
  }

  private static MavenProject buildMavenProject(File pom) {
    try {
      return MAVEN_PROJECT_BUILDER.build(pom, PROJECT_BUILDER_CONFIGURATION);
    } catch (ProjectBuildingException e) {
      throw new RuntimeException(e);
    }
  }
}
