import sbt._

object AppDependencies {

  val bootstrapVersion = "7.22.0"
  val hmrcMongoVersion = "1.3.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"                % hmrcMongoVersion,
    "com.enragedginger"       %% "akka-quartz-scheduler"             % "1.9.3-akka-2.6.x"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"      % bootstrapVersion    % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"     % hmrcMongoVersion    % "test, it",
    "org.mockito"             %% "mockito-scala-scalatest"     % "1.17.14"           % "it, test"
  )
}