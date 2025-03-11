FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY SpigotMCVerificationBot.jar .
RUN mkdir -p /app/config
COPY config.yml /app/config/
CMD ["java", "-jar", "SpigotMCVerificationBot.jar"]