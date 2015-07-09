package nl.ebpi.mavenplugins.ebpienforcer

import com.google.common.base.Stopwatch
import test.MavenProcess.Result
import test.{MavenProcess, TestBase}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * This is an integration test for the [[DependencyConvergenceGoal]].
 *
 * NB: This test can not be run from IDEs, because it requires the bootstrapping performed by maven
 *     as configured in the package and pre-integration-test phases.
 */
class DependencyConvergenceGoalIT extends TestBase {
  import nl.ebpi.mavenplugins.ebpienforcer.DependencyConvergenceGoalIT._

  describe("The dependency convergence goal") {
    it("must reject project d, with 'diamond-shaped' diverging dependencies") {
      context // match { case () => }
      failureOf(project = "d") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:d:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:d:1
                    |[ERROR]         unit.test:c:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
      // Note that the newlines before and after the required error message are significant!
    }
    it("must reject project e, with 'triangle-shaped' diverging dependencies") {
      context
      failureOf(project = "e") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:e:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:e:1
                    |[ERROR]         unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project f, with diverging dependencies in a dependency") {
      context
      assumeInstall("e", arguments = List("clean", "install", "-Pnocheck"))
      failureOf(project = "f") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                  |[ERROR]     1 is required through
                  |[ERROR]       unit.test:f:1
                  |[ERROR]         unit.test:e:1
                  |[ERROR]           unit.test:b:1
                  |[ERROR]             unit.test:a:1
                  |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:f:1
                    |[ERROR]         unit.test:e:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project dm-d, with 'diamond-shaped' diverging dependencies, despite having specified 'a' in dependencyManagement") {
      context // match { case () => }
      failureOf(project = "dm/d") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:dm-d:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:dm-d:1
                    |[ERROR]         unit.test:c:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project dm-e, with 'triangle-shaped' diverging dependencies, despite having specified 'a' in dependencyManagement") {
      context
      failureOf(project = "dm/e") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:dm-e:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:dm-e:1
                    |[ERROR]         unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project dm-f, with diverging dependencies in a dependency, despite the dependency having specified 'a' in dependencyManagement") {
      context
      assumeInstall("dm/e", arguments = List("clean", "install", "-Pnocheck"))
      failureOf(project = "dm/f") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR] Disputed artifact: unit.test:a
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:dm-f:1
                    |[ERROR]         unit.test:dm-e:1
                    |[ERROR]           unit.test:b:1
                    |[ERROR]             unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:dm-f:1
                    |[ERROR]         unit.test:dm-e:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project dm/d-dm-c, with diverging dependencies in a dependency, despite a dependency version on 'a' being specified in the dependencyManagement of 'c'") {
      context
      assumeInstall("dm/c", arguments = List("clean", "install", "-Pnocheck"))
      failureOf(project = "dm/d-dm-c") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:d-dm-c:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:d-dm-c:1
                    |[ERROR]         unit.test:dm-c:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
    }
    it("must reject project i, but not report conflicts that are masked by other conflicts") {
      context
      failureOf(project = "i") must (
        not (include( """[ERROR] Disputed artifact: unit.test:a""")) and
        include("""[ERROR] Disputed artifact: unit.test:g""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:i:1
                    |[ERROR]         unit.test:h:1
                    |[ERROR]           unit.test:g:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:i:1
                    |[ERROR]         unit.test:g:2
                    |""".stripMargin))
      // Note that the newlines before and after the required error message are significant!
    }
    it("must reject project j, because a test dependency is inconsistent with a main dependency") {
      context
      failureOf(project = "j") must (
        include("""[ERROR] Disputed artifact: unit.test:a""") and
          include("""
                    |[ERROR]     1 is required through
                    |[ERROR]       unit.test:j:1
                    |[ERROR]         unit.test:b:1
                    |[ERROR]           unit.test:a:1
                    |""".stripMargin) and
          include("""
                    |[ERROR]     2 is required through
                    |[ERROR]       unit.test:j:1
                    |[ERROR]         unit.test:c:1
                    |[ERROR]           unit.test:a:2
                    |""".stripMargin))
    }
    it("must accept project m, because the test dependencies of its dependencies need not be considered") {
      context
      assumeInstall("m")
    }
    describe("has assumption handling that") {
      it("must accept project d-assume, because it configures the plugin to assume that a:2 can replace a:1") {
        context
        assumeInstall("d-assume")
      }
      it("must reject project d-assume-no-depmgt, because although an assumption is configured, the choice " +
        "is still nondeterministic, because the version is not pinned by the dependencyManagement section") {
        context
        failureOf(project = "d") must (
          include("""[ERROR] Disputed artifact: unit.test:a""") and
            include("""
                      |[ERROR]     1 is required through
                      |[ERROR]       unit.test:d:1
                      |[ERROR]         unit.test:b:1
                      |[ERROR]           unit.test:a:1
                      |""".stripMargin) and
            include("""
                      |[ERROR]     2 is required through
                      |[ERROR]       unit.test:d:1
                      |[ERROR]         unit.test:c:1
                      |[ERROR]           unit.test:a:2
                      |""".stripMargin))
      }
    }
  }
}

object DependencyConvergenceGoalIT {

  import org.scalatest.MustMatchers._

  def assumeInstall(project : String, arguments : List[String] = List("-e", "clean", "install")) : Unit = {
    val sw = new Stopwatch()
    sw.start()
    System.err.println(s"Installing $project")
    val result = mvn(workDir = s"target/test-classes/poms/$project", arguments)
    sw.stop()
    result match {
      case MavenProcess.Result(MavenProcess.ExitFailure, diagnostics) =>
        System.err.println(diagnostics)
        fail(s"Failed installing $project in $sw")
      case _ =>
        System.err.println(s"Done installing $project in $sw")
    }
  }

  def failureOf(project : String) : String = {

    val result = mvn(workDir = s"target/test-classes/poms/$project", List("clean", "install"))
    
    result match {
      case MavenProcess.Result(MavenProcess.ExitFailure, diagnostics) =>
        diagnostics.replace("\r", "")
      case MavenProcess.Result(_, diagnostics) =>
        System.err.println(diagnostics)
        fail(s"Project $project should have failed")
    }

  }

  def mvn(workDir: String, arguments: List[String]): Result = {
    val cwd = System.getProperty("user.dir")
    val defaults = List(s"-Dmaven.repo.local=$cwd/target/tmp/m2")
    MavenProcess.mvn(workDir = workDir, arguments = defaults ::: arguments)
  }

  lazy val context : Unit = {
    implicit val ec = ExecutionContext.global

    def buildSimultaneously(projects: String*) {
      Await.ready(Future.sequence(projects.toList.map(x => Future(assumeInstall(x)))),
        atMost = Duration.Inf).value.get.get.foreach { x => () }
    }

    buildSimultaneously("a/1", "a/2")
    buildSimultaneously("b", "c", "g/1", "g/2", "k", "l")
    buildSimultaneously("h")
  }

}
