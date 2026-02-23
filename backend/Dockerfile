FROM eclipse-temurin:25.0.2_10-jre

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY target/*.jar app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]