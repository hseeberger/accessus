addSbtPlugin("com.dwijnand"      % "sbt-dynver"      % "3.0.0")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"    % "1.4.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "5.0.0")

libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25" // Needed by sbt-git
