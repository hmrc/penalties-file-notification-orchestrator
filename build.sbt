import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "penalties-file-notification-orchestrator"

val silencerVersion = "1.17.13"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion                     := 0,
    PlayKeys.playDefaultPort         := 9184,
    scalaVersion                     := "2.13.12",
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(
    ScoverageKeys.coverageExcludedPackages := "controllers.testOnly.*",
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;..*components.*;" +
      ".*Routes.*;.*ControllerConfiguration;.*Modules;.*scheduler.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true)
  .settings(scalacOptions += "-Wconf:cat=unused-imports&src=routes/.*:s")

