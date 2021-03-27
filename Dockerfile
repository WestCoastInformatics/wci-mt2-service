#######################################################################
#                              Base Node                              #
#######################################################################
# This is the build container
FROM gradle:4.10-jdk8-alpine as gradle-host
USER root
RUN apk add --no-cache curl tar bash procps

# Set environment
ENV HOME /home/gradle
ENV PROJECT /home/gradle/project
ENV GRADLE_USER_HOME="${HOME}/.gradle"

# Setup gradle user
RUN mkdir -p "${PROJECT}"
RUN chown -R gradle:gradle "${PROJECT}"
USER gradle
RUN mkdir -p "${PROJECT}/build/output"
RUN mkdir -p "${PROJECT}/.gradle"
RUN mkdir -p "${PROJECT}/.cache"

# copy source
COPY --chown=gradle:gradle .gradle/gradle.properties ${HOME}/.gradle/gradle.properties
COPY --chown=gradle:gradle config/ "${PROJECT}"/config
COPY --chown=gradle:gradle gradle/ "${PROJECT}"/gradle
COPY --chown=gradle:gradle src/ "${PROJECT}/src"
COPY --chown=gradle:gradle [bgs]*.gradle ${PROJECT}/
COPY --chown=gradle:gradle gradlew ${PROJECT}/
RUN chown -R gradle:gradle "${PROJECT}"

WORKDIR ${PROJECT}

# Skip tests for docker build
RUN ./gradlew bundleWithDependencies --no-daemon -x test
RUN curl -o /home/gradle/project/build/output/webapp-runner.jar https://repo1.maven.org/maven2/com/github/jsimone/webapp-runner/9.0.27.1/webapp-runner-9.0.27.1.jar

# This is the runtime container
FROM openjdk:8-jre-alpine as target
ARG RUNTIME_USER=server
#
# Create the user that will be used to run the product and set up the directory it'll reside in.
RUN addgroup -S -g 1001 ${RUNTIME_USER}
RUN mkdir /srv/rt
RUN adduser -D -S -H -G ${RUNTIME_USER} -u 1001 -s /bin/false -h /srv/rt ${RUNTIME_USER}
RUN chown -R ${RUNTIME_USER}:${RUNTIME_USER} /srv/rt

# Copy the launcher script in.
# This serves to ensure that all necessary dependencies end up on the classpath.
ADD run.sh /srv/rt/
RUN chmod +x /srv/rt/run.sh

# Switch to the runtime user and copy the product in.
USER $RUNTIME_USER
COPY --from=gradle-host --chown=server:server /home/gradle/project/build/output/ /srv/rt/
COPY --from=gradle-host --chown=server:server /home/gradle/project/src/resources/ /srv/rt/src/resources/
WORKDIR /srv/rt

# Run the built product when the container launches
CMD "/srv/rt/run.sh"

