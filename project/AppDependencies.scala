import sbt._

object AppDependencies {

  val bootstrapVersion = "7.13.0"
  val hmrcMongoVersion = "0.74.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"                % hmrcMongoVersion,
    "com.enragedginger"       %% "akka-quartz-scheduler"             % "1.9.3-akka-2.6.x",
    "uk.gov.hmrc"             %% "internal-auth-client-play-28"      % "1.4.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"      % bootstrapVersion    % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"     % hmrcMongoVersion    % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"          % "5.1.0"             % "test, it",
    "com.github.tomakehurst"  %  "wiremock-jre8"               % "2.26.3"            % "it",
    "com.github.tomakehurst"  %  "wiremock-standalone"         % "2.27.2"            % "it",
    "org.mockito"             %% "mockito-scala-scalatest"     % "1.17.14"           % "it, test"
  )
}