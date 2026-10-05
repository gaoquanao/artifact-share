FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/artifact-share-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
