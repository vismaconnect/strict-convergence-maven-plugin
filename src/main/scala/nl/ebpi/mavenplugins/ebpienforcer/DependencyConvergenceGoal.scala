package nl.ebpi.mavenplugins.ebpienforcer

import java.util

import nl.ebpi.mavenplugins.ebpienforcer.DependencyConvergence.Conflict
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.{AbstractMojo, MojoExecution, MojoFailureException}
import org.apache.maven.plugins.annotations.{Component, LifecyclePhase, Mojo, Parameter}
import org.apache.maven.project.{MavenProject, MavenProjectHelper, ProjectBuilder, ProjectBuildingException}
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystemSession

import scala.collection.JavaConverters._
import scala.collection.immutable

/**
 * Maven goal to run dependency conversion. The intention is to run the same dependency conversion
 * that the Maven enforcer runs, but without considering the dependency management. The enforcer
 * plugin doesn't look at dependency conflicts if the dependencies are in the dependencyManagement.
 *
 * Note that this could be an enforcer custom rule, but the plugin solution is preferred because the
 * latest version of the enforcer plugin (1.4) is based on Maven 2.2.1
 *
 * Javadoc of ProjectBuilder,
 * https://wiki.eclipse.org/Aether/Using_Aether_in_Maven_Plugins
 *
 * DEFINITIONS:
 *
 * Dependent:
 *
 *     If project B has a dependency on project A, A has a dependent B.
 *
 * Chain,
 * Dependents,
 * Chain of dependents:
 *
 *     Project B has a dependency on project A, so A has a dependent B.
 *     Project C has a dependency on project B, so B has a dependent C.
 *     The chain of dependents is A,B,C
 *     A chain of dependents may start with a project that is not itself a dependent.
 *     The second element and further always have dependencies.
 *
 *     Note that this is the reverse of a chain of dependencies.
 *
 * Project:
 *
 *     artifact, groupId, version
 *
 * Project Key:
 *
 *     artifact, groupId
 *
 * Conflict:
 *
 *     Situation where for some Project Key, there exist more than one Projects.
 *
 * Side:
 *
 *     Collection of Dependents that depend on the same Project (including version) in a conflict.
 *
 */
@Mojo(
  // name of the goal that runs this plugin
  name = "dependency-convergence",
  // bind by default to the initialize phase.
  defaultPhase = LifecyclePhase.INITIALIZE,
  // classpath resolution. See https://maven.apache.org/developers/mojo-api-specification.html
  requiresDependencyResolution = org.apache.maven.plugins.annotations.ResolutionScope.COMPILE)
class DependencyConvergenceGoal extends AbstractMojo {

  import nl.ebpi.mavenplugins.ebpienforcer.DependencyConvergenceGoal._

  // the name of the members have to be the same as the names of the elements used in the configuration
  // of the plugin in the pom.xml
  @Parameter(defaultValue = "${project}")
  protected var project: MavenProject = null
  @Parameter(defaultValue = "${session}")
  protected var session: MavenSession = null
  @Parameter(defaultValue = "${mojoExecution}")
  protected var mojoExecution: MojoExecution = null
  @Component
  protected var projectHelper: MavenProjectHelper = null
  @Component
  protected var artifactResolver: ArtifactResolver = null
  @Component
  protected var artifactHandlerManager: ArtifactHandlerManager = null
  @Parameter(defaultValue = "${localRepository}")
  protected var localArtifactRepository: ArtifactRepository = null
  @Component
  protected var repositorySystem: RepositorySystem = null

  @Component
  protected var projectBuilder: ProjectBuilder = null

  @Parameter
  protected var assumptions : java.util.List[Assumption] = new util.ArrayList[Assumption]()

  @Parameter(defaultValue = "false")
  protected var shallowCheck : Boolean = false

  def repoSession : RepositorySystemSession = MavenRepositorySystemUtils.newSession()

  override def execute(): Unit = {
    //val conflicts = determineConflicts
    val conflicts = DependencyConvergence.determineConflicts(
      allDependentChains = dependentChainsForAllDependencies(project).map(DependentChain(_)),
      isAssumedSufficientFor = currentAssumptions,
      shallowCheck = shallowCheck)

    if (conflicts.nonEmpty) {
      // Remove conflicts that follow from other conflicts before presenting them.
      // This happens after _.nonEmpty, because it is more defensive.
      displayConflictsAndFail(conflicts)
    }
  }

  /**
   * Just display logic.
   * @param conflicts non-empty iterable of conflicts
   */
  private def displayConflictsAndFail(conflicts: Iterable[Conflict]) {
    getLog.error("")
    getLog.error("CONFLICTING VERSIONS FOUND")
    getLog.error("")

    conflicts foreach {
      conflict: Conflict =>
        val conflictingArtifact = conflict.head._1.key
        val sides = conflict.values
        getLog.error(s"Disputed artifact: ${conflictingArtifact.groupId}:${conflictingArtifact.artifactId}")

        sides.toList.sortBy(_.head.head.version).foreach { dependents =>

            getLog.error(s"    ${dependents.head.head.version} is required through")

            renderTree(dependents).foreach(getLog.error)

            getLog.error("")
        }
    }
    throw new MojoFailureException("Conflicting versions found. See diagnostics above.")
  }

  private def renderTree(dependents: List[DependentChain]): List[String] = {
    // convert from dependent chain to dependency chain
    val dependencies = dependents.map(_.reverse)

    // build trees from the dependency chains (or put differently, merge their prefixes)
    val sideTree = dependencies.foldRight[NForest[Project]](NForest.empty)((a, as) => as.updated(a))

    val lines: List[String] = sideTree.fold { sidePart: Map[Project, List[String]] =>
      val ls = for {
        (proj, depsLines) <- sidePart
        line <- s"      ${proj.key.groupId}:${proj.key.artifactId}:${proj.version}" :: (depsLines map ("  " + _))
      } yield line
      ls.toList
    }
    lines
  }

  /**
   * Generates dependent chains for all dependencies, or equivalently, all paths from everywhere to the root project.
   *
   * dependentChainsForAllDependencies(project).map(_.head) =
   * transitive closure of non-test dependencies of project + direct test dependencies of project and their transitive non-test closures
   */
  private def dependentChainsForAllDependencies(project : MavenProject): List[List[Project]] = {

    def withAncestors(aProject : MavenProject, aProjectAndAncestors : List[Project], dependencyFilter : Dependency => Boolean) : List[List[Project]] = {
      for {
        dependency <- buildDependencyProjects(aProject.getDependencies.asScala.filter(dependencyFilter)).toList
        dependencyChain = toProject(dependency) :: aProjectAndAncestors
        results <- dependencyChain :: withAncestors(dependency, dependencyChain, _.getScope != "test")
      } yield results
    }

    withAncestors(project, toProject(project) :: Nil, dependency => true)
  }

  private def findDuplicates[A,B](iterable : List[(A, B)]) : Map[A, List[(A, B)]] = {
    iterable.groupBy(_._1).filter(_._2.size > 1)
  }

  lazy val dependencyManagement : Map[ProjectKey, Project] = {
    val dmFromProject = (for {
      dm <- Option(project.getDependencyManagement)
      deps <- Option(dm.getDependencies)
    } yield {deps.iterator().asScala}).getOrElse(Nil.iterator)

    val dependencies = dmFromProject.map {
      dependency => (toProjectKey(dependency), toProject(dependency))
    }.toList

    val duplicates = findDuplicates(dependencies)

    if (duplicates.nonEmpty)
      throw new MojoFailureException("Conflicting versions found in dependencyManagement")
    else
      dependencies.toMap
  }

  def checkAssumptionsWithDependencyManagement(assumptions: Map[Project, Project], dependencyManagement: Map[ProjectKey, Project]) : Unit = {
    val messages = (for {
      replacement <- assumptions.values
      message <- dependencyManagement.get(replacement.key) match {
        case None => IndexedSeq(s"Assumption is not reflected in dependencyManagement. Add ${replacement.key.groupId}:${replacement.key.artifactId}:${replacement.version} to the dependencyManagement section.")
        case Some(dmProject) if dmProject.version != replacement.version => IndexedSeq(s"Assumption is inconsistent with dependencyManagement. Assumption suggest ${replacement.key.groupId}:${replacement.key.artifactId}:${replacement.version} but dependencyManagement suggests version ${dmProject.version}")
        case _ => IndexedSeq.empty
      }
    } yield message).toIndexedSeq
    if (messages.nonEmpty) {
      throw new MojoFailureException(s"Assumptions and dependencyManagement do not match:\t\n${messages.mkString("\t\n")}")
    }
  }

  def currentAssumptions : Map[Project, Project] = {
    val theAssumptions = (for {
      assumption <- assumptions.iterator().asScala
      replacement = toProject(assumption.artifact)
      isSufficientFor <- assumption.isSufficientFor.split(",").map(_.trim)
      replaceable = Project(replacement.key, isSufficientFor)
    } yield (replaceable, replacement)).toMap
    checkAssumptionsWithDependencyManagement(theAssumptions, dependencyManagement)
    theAssumptions
  }

  /**
   * build a MavenProject from a list of dependencies, basically reading the pom.xml of the dependency
   */
  private def buildDependencyProjects(dependencies: Iterable[Dependency]): immutable.IndexedSeq[MavenProject] = {
    val allResponses = dependencies map memoProject
    allResponses.toIndexedSeq.flatten
  }

  private val memoProject : Dependency => Option[MavenProject] = Memo(readProject,
    dependency => (dependency.getGroupId, dependency.getArtifactId, dependency.getVersion, dependency.getType))

  private def readProject(dependency : Dependency) : Option[MavenProject] = {
      val artifact = repositorySystem.createArtifact(dependency.getGroupId,
        dependency.getArtifactId,
        dependency.getVersion,
        dependency.getType)

      val request = session.getRequest.getProjectBuildingRequest.setProcessPlugins(false)

      try {
        val response = projectBuilder.build(artifact, false, request)

        if (response.getProblems.size > 0) {
          getLog.warn("Error while reading pom file for artifact " + artifact.getGroupId + ':' + artifact.getArtifactId + ':' + artifact.getVersion)

          response.getProblems.asScala foreach { problem =>
            getLog.warn(problem.getSource + ": " + problem.getMessage)
          }

          None
        } else
          Some(response.getProject)
      } catch {
        case e: ProjectBuildingException =>
          getLog.warn("Error while reading pom file for artifact " + artifact.getGroupId + ':' + artifact.getArtifactId + ':' + artifact.getVersion)
          None
      }
  }
}

object DependencyConvergenceGoal {

  private def toProjectKey(project : MavenProject) : ProjectKey =
    ProjectKey(project.getGroupId, project.getArtifactId)

  private def toProjectKey(dependency : Dependency) : ProjectKey =
    ProjectKey(dependency.getGroupId, dependency.getArtifactId)

  private def toProject(project : MavenProject) : Project =
    Project(toProjectKey(project), project.getVersion)

  private def toProject(dependency : Dependency) : Project =
    Project(toProjectKey(dependency), dependency.getVersion)

}
