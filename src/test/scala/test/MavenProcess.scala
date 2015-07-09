package test

import java.io.File

import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters._

object MavenProcess {

  sealed trait ExitCode
  case object ExitSuccess extends ExitCode
  case object ExitFailure extends ExitCode

  object ExitCode {
    def fromInt(x : Int) : ExitCode = x match {
      case 0 => ExitSuccess
      case _ => ExitFailure
    }
  }

  case class Result(exitCode : ExitCode, diagnostics : String)

  def mvn(workDir : String, arguments : List[String]) : Result = {

    val workDirFile = new File(workDir)
    val out = ProcessBuilder.Redirect.PIPE
    val pb = new ProcessBuilder()
    pb.directory(workDirFile)
    pb.redirectErrorStream(true) // merge stdout and stderr into the same stream
    pb.command((getMvnExecutable :: arguments).asJava)
    System.out.println(workDir + " $ " + formatCommand(pb.command().asScala))
    val process = pb.start()
    val diagnostics = IOUtils.toString(process.getInputStream)
    val exitInt = process.waitFor()
    Result( exitCode = ExitCode.fromInt(exitInt), diagnostics = diagnostics)
  }

  def getMvnExecutable : String = {
    Option(System.getenv("M2_HOME")) match {
      case None => "mvn"
      case Some(m2Home) => m2Home + "/bin/mvn"
    }
  }

  def formatCommand(list : Iterable[String]) : String = {
    list.map(formatArg).mkString(" ")
  }

  def formatArg(arg : String) : String = {
    if (arg exists (c => c.isSpaceChar || "'\"\\".contains(c))) {
      "'" + arg.replace("\\", "\\\\").replace("'", "\\'") + "'"
    } else {
      arg
    }
  }
}
