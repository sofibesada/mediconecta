# syntax=docker/dockerfile:1
# Imagen de la aplicacion: compila los tres WAR (MediConecta y los dos simuladores de
# sistemas externos) y los despliega en un WildFly ya configurado.

# --- 1. Compilacion ------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
COPY videollamadas-mock videollamadas-mock
COPY obras-sociales-mock obras-sociales-mock
COPY pagos-mock pagos-mock
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests package \
 && mvn -q -B -DskipTests -f videollamadas-mock/pom.xml package \
 && mvn -q -B -DskipTests -f obras-sociales-mock/pom.xml package \
 && mvn -q -B -DskipTests -f pagos-mock/pom.xml package \
 && mvn -q -B dependency:copy -Dartifact=org.postgresql:postgresql:42.7.4 -DoutputDirectory=/drivers

# --- 2. Servidor ---------------------------------------------------------------------
FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk21
COPY --from=build /drivers/postgresql-42.7.4.jar /tmp/postgresql.jar
COPY docker/wildfly/configurar.cli /tmp/configurar.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configurar.cli \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history \
           /opt/jboss/wildfly/standalone/data /opt/jboss/wildfly/standalone/log
COPY --from=build /src/target/mediconecta.war \
                  /src/videollamadas-mock/target/videollamadas-mock.war \
                  /src/obras-sociales-mock/target/obras-sociales-mock.war \
                  /src/pagos-mock/target/pagos-mock.war \
                  /opt/jboss/wildfly/standalone/deployments/

# Zona horaria de la clinica. Las fechas de los turnos se guardan sin zona (hora local), asi
# que el servidor tiene que estar en la misma zona que los usuarios: si no, el vencimiento de
# la reserva, la regla de 24 horas y los recordatorios se corren 3 horas (el contenedor viene
# en UTC). Java toma la zona de la variable TZ.
ENV TZ=America/Argentina/Buenos_Aires
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0", "-bmanagement", "127.0.0.1"]
