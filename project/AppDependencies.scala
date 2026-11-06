
import sbt.*

object AppDependencies {

  val bootstrapVersion = "10.8.0"
  val hmrcMongoVersion = "2.14.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"    % "1.2.0-pekko-1.0.x" excludeAll (
      ExclusionRule("com.mchange", "c3p0"),
      ExclusionRule("com.mchange", "mchange-commons-java")
    )
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.mockito"       %% "mockito-scala-scalatest" % "2.2.3"          % Test
  )

  val itTest: Seq[ModuleID] = Seq.empty

}
