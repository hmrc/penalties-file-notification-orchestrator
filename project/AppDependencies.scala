import sbt.*

object AppDependencies {

  val bootstrapVersion = "8.6.0"
  val hmrcMongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"    % "1.2.0-pekko-1.0.x"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % "test, it",
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.37"        % "it, test"
  )
}
