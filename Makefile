# global service name
SERVICE                 := snomed-refset-service

#######################################################################
#                 OVERRIDE THIS TO MATCH YOUR PROJECT                 #
#######################################################################
APP_VERSION             := $(shell echo `grep "version =" build.gradle | sed 's/version = //'`)

# Builds should be repeatable, therefore we need a method to reference the git
# sha where a version came from.
GIT_VERSION          	?= $(shell echo `git describe --match=NeVeRmAtCh --always --dirty`)
GIT_COMMIT          	?= $(shell echo `git log | grep -m1 -oE '[^ ]+$'`)
GIT_COMMITTED_AT        ?= $(shell echo `git log -1 --format=%ct`)
GIT_BRANCH				?=
FULL_VERSION            := v$(APP_VERSION)-g$(GIT_VERSION)
#DOCKER_INT_REGISTRY     := docker.io
DOCKER_INT_REGISTRY     := ihtsdo

ENV                     ?= dev
#KUBECONFIG              ?=~/.kube/config.${ENV}

clean:
	./gradlew clean

# Build the library without tests
build:
	./gradlew clean test dependencyCheckAnalyze build buildDeb -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest

devBuild:
	./gradlew clean build -x buildDeb -x test -x dependencyCheckAnalyze -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest

# Build the library and check style
checkstyle:
	./gradlew --daemon checkstyleMain checkstyleTest -x clean -x build -x buildDeb -x test -x spotbugsMain -x spotbugsTest

# Build the library and check for bugs
spotbugs:
	./gradlew spotbugsMain -x clean -x build -x buildDeb -x test -x checkstyleTest -x checkstyleMain

dependencyCheck:
	./gradlew dependencyCheckAnalyze

je:
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5555 -jar build/libs/snomed-refset-service-*-SNAPSHOT.jar > output.log 2>&1 

test:
	./gradlew test
	
run:
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5555 -jar build/libs/snomed-refset-service-*-SNAPSHOT.jar > build/log.log 2>&1 &

install:
	./gradlew clean build install -x test -x spotbugsMain -x spotbugsTest

# Publish artifacts to nexus (requires a local .gradle/gradle.properties properly configured)
release:
	./gradlew clean uploadArchives
	
version:
	@echo $(APP_VERSION)
