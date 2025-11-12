FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
COPY target/fraud-engine-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]