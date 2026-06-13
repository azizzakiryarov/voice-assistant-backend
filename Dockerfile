FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY mvnw ./
COPY .mvn .mvn
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM docker.io/library/eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /build/target/voice-assistant-0.0.1-SNAPSHOT.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
