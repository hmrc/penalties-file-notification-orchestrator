
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "penalties-file-notification-orchestrator"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.18"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(PlayKeys.playDefaultPort := 9184, libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(
    ScoverageKeys.coverageExcludedPackages := "controllers.testOnly.*",
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;..*components.*;" +
      ".*Routes.*;.*ControllerConfiguration;.*Modules;.*scheduler.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum    := false,
    ScoverageKeys.coverageHighlighting     := true
  )
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused-imports&src=routes/.*:s",
      "-Wconf:cat=unused&src=routes/.*:s"
    ))

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.itTest)
