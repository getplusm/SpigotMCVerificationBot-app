FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/*.jar /app/SpigotMCVerificationBot.jar
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "SpigotMCVerificationBot.jar"]
CMD []