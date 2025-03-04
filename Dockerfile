# Базовый образ с Java 17
FROM openjdk:17-jdk-slim

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем скомпилированный JAR-файл из сборки Gradle
COPY build/libs/SpigotMCVerificationBot.jar /app/SpigotMCVerificationBot.jar

# Указываем точку входа для запуска приложения
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "SpigotMCVerificationBot.jar"]

# Указываем команду по умолчанию (можно переопределить в docker-compose)
CMD []