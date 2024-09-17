FROM eclipse-temurin:21 AS builder
COPY . /src
WORKDIR /src
RUN ./mvnw clean package

FROM quay.io/keycloak/keycloak:25.0.4 AS keycloak
COPY --from=builder /src/requiredaction/target/awms.lscsde-requiredaction.jar /opt/keycloak/providers/awms.lscsde-requiredaction.jar