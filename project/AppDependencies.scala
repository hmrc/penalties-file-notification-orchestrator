import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "5.18.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.59.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"   % "5.18.0"  % "test",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"     % "0.58.0" % "test, it",
    "org.scalatest"           %% "scalatest"                   % "3.0.8"   % "test",
    "com.typesafe.play"       %% "play-test"                   % current   % "test",
    "org.pegdown"             %  "pegdown"                     % "1.6.0"   % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"          % "5.1.0"   % "test, it",
    "com.github.tomakehurst"  %  "wiremock-jre8"               % "2.26.3"  % "it",
    "org.scalamock"           %% "scalamock-scalatest-support" % "3.6.0"   % "test",
    "com.github.tomakehurst"  %  "wiremock-standalone"         % "2.27.2"  % "it",
    "org.mockito"             % "mockito-core"                 % "3.1.0"   % "test, it"
  )
}
