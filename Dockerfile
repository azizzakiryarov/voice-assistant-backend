# Run stage
FROM eclipse-temurin:21-jdk-alpine

# Copy the JAR file from the build stage
COPY target/voice-assistant-0.0.1-SNAPSHOT.jar target/voice-assistant-0.0.1-SNAPSHOT.jar
# Set Java options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
# Expose the port
EXPOSE 8081
# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/voice-assistant-0.0.1-SNAPSHOT.jar"]