FROM eclipse-temurin:21-jdk-alpine-3.21 AS jre-builder

COPY build/libs/Attendance-0.0.1-SNAPSHOT.jar /app/app.jar

WORKDIR /app

RUN apk update && apk add --no-cache tar binutils
RUN jar xvf app.jar
RUN jdeps \
        --ignore-missing-deps \
        --print-module-deps \
        -q \
        --recursive \
        --multi-release 21 \
        --class-path "BOOT-INF/lib/*" \
        --module-path "BOOT-INF/lib/*" \
        app.jar > modules.info

RUN $JAVA_HOME/bin/jlink \
         --verbose \
         --add-modules $(cat modules.info) \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /optimized-jdk-21

FROM alpine:latest
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# copy JRE from the base image
COPY --from=jre-builder /optimized-jdk-21 $JAVA_HOME

# Add app user
ARG APPLICATION_USER=springboot

RUN adduser --no-create-home -u 1000 -D $APPLICATION_USER

# Create the application directory
RUN mkdir /app && chown -R $APPLICATION_USER /app

COPY --chown=$APPLICATION_USER:$APPLICATION_USER build/libs/Attendance-0.0.1-SNAPSHOT.jar /app/app.jar

WORKDIR /app

USER $APPLICATION_USER

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m"

ENTRYPOINT [ "java", "-jar", "/app/app.jar" ] 