import sbt._

object AppDependencies {

  private val bootstrapVersion = "10.8.0"
  private val hmrcMongoVersion = "2.12.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "org.typelevel"     %% "cats-core"                 % "2.13.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.playframework"      %% "play-test"               % "3.0.11"          % Test,
    "com.vladsch.flexmark"   %  "flexmark-all"            % "0.64.8"         % Test,
    "org.scalamock"          %% "scalamock"               % "7.5.5"          % Test,
    "org.scalacheck"         %% "scalacheck"              % "1.19.0"         % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"      % "7.0.2"          % Test,
  )

  val it: Seq[ModuleID] = compile
}