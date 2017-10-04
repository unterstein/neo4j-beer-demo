name := "neo4j-beer-analyze"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.neo4j" % "neo4j-jdbc-driver" % "3.1.0",
  "mysql" % "mysql-connector-java" % "6.0.5",
  "org.springframework.boot" % "spring-boot-starter-jdbc" % "1.5.3.RELEASE"
)