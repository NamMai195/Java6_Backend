FROM openjdk:17

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} backend-service-stm.jar

ENTRYPOINT ["java", "-jar", "backend-service-stm.jar"]

EXPOSE 8080