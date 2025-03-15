# 1. Dockerfile for Spring Boot
FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw package -DskipTests
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:21-jre-alpine

VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency

COPY --from=builder ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder ${DEPENDENCY}/BOOT-INF/classes /app

ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseContainerSupport"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp app:app/lib/* com.voiceassistant.VoiceAssistantApplication"]