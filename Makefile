# global service name
SERVICE                 := ihtsdo-refset-service

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

# Build docker images and tag them consistently
docker: .gradle/gradle.properties
	@echo APP_VERSION=$(APP_VERSION)
	@echo FULL_VERSION=$(FULL_VERSION)
	docker build -t $(SERVICE) .
	@echo REGISTRY=$(DOCKER_INT_REGISTRY)
	@echo SERVICE=$(SERVICE)
	@echo $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	docker tag $(SERVICE) $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	@echo $(SERVICE) $(DOCKER_INT_REGISTRY)/$(SERVICE):$(APP_VERSION)
	docker tag $(SERVICE) $(DOCKER_INT_REGISTRY)/$(SERVICE):$(APP_VERSION)

#######################################################################
#          OVERRIDE this to match your docker/service config          #
#######################################################################

# Run the docker image local for development and testing. This is may be
# specific to each project, however, it should run with the minimal of inputs.
# PGHOST=host.docker.internal
# PGPORT=5432
# PGUSER=postgres
# PGPASSWORD=
# PGDATABASE=rt2
# DV=TEST-20200110
### To add customizations, use something like this:
# -v c:/ihtsdo/data/refsetservice/custom:/custom
run: docker
	docker run -it -d -e PGHOST=$PGHOST -e PGPORT=$PGPORT -e PGUSER=$PGUSER -e PGPASSWORD=$PASSWORD -e PGDATABASE=$PGDATABASE -p 8080:8080 -v c:/index/terminologynationwide:/index $(SERVICE)

# Publish artifacts to repository
package: docker
	@echo $(DISPLAY_BOLD)"Publishing container to $(DOCKER_INT_REGISTRY) registry"$(DISPLAY_RESET)
	@echo $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	docker -D push $(DOCKER_INT_REGISTRY)/$(SERVICE):$(FULL_VERSION)
	@echo $(DOCKER_INT_REGISTRY)/$(SERVICE):$(APP_VERSION)
	docker -D push $(DOCKER_INT_REGISTRY)/$(SERVICE):$(APP_VERSION)

# consider also "docker save..." and "docker load..." to avoid registry.

# Build the library without tests
build:
	./gradlew clean build -x test -x spotbugsMain -x spotbugsTest

test:
	./gradlew test

install:
	./gradlew clean build install -x test -x spotbugsMain -x spotbugsTest

# Publish artifacts to nexus (requires a local .gradle/gradle.properties propery configured)
release:
	./gradlew uploadArchives
