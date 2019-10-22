# STAGE: Build
FROM gradle:5.6.2-jdk11 as builder

# Create Working Directory
ENV BUILD_DIR=/home/gradle/src
COPY --chown=gradle:gradle . ${BUILD_DIR}
WORKDIR $BUILD_DIR

# Build Jar
RUN gradle build

# STAGE: Deploy
FROM openjdk:11-jre

# Create app directory, chgrp, and chmod
ENV APP_HOME=/app
RUN chgrp -R 0 $APP_HOME && chmod -R g=u $APP_HOME
WORKDIR $APP_HOME

# Copy jar file over from builder stage
COPY --from=builder /home/gradle/src/build/libs/basic-configmap-0.0.1-SNAPSHOT.jar $APP_HOME
RUN mv ./basic-configmap-0.0.1-SNAPSHOT.jar app.jar

USER 2000

ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar