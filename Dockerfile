FROM java:8

MAINTAINER Johannes Unterstein <junterstein@mesosphere.io>

ADD target/neo4j-beer-demo-1.0-SNAPSHOT.jar /app.jar
EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]
