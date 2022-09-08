import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "7.2.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.70.0",
    "com.enragedginger"       %% "akka-quartz-scheduler"      % "1.9.2-akka-2.6.x"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"   % "7.2.0"    % "test",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"     % "0.71.0"    % "test, it",
    "org.pegdown"             %  "pegdown"                     % "1.6.0"     % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"          % "5.1.0"     % "test, it",
    "com.github.tomakehurst"  %  "wiremock-jre8"               % "2.26.3"    % "it",
    "com.github.tomakehurst"  %  "wiremock-standalone"         % "2.27.2"    % "it",
    "org.mockito"             %  "mockito-all"                 % "1.10.19"  % "test, it"
  )
}