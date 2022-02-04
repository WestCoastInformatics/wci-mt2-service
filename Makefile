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
	./gradlew clean build buildDeb -x test -x spotbugsMain -x spotbugsTest -x checkstyleTest -x checkstyleMain

test:
	./gradlew test

install:
	./gradlew clean build install -x test -x spotbugsMain -x spotbugsTest

# Publish artifacts to nexus (requires a local .gradle/gradle.properties propery configured)
release:
	./gradlew clean uploadArchives
