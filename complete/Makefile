COMMIT?=$(shell git rev-parse --short HEAD)
BUILD_TIME?=$(shell date -u '+%Y-%m-%d_%H:%M:%S')
PROJECT?=
#include vars-az.mk

APP?=onboarding-api
RELEASE?=v1alpha1-31
IMAGE?=eengccoelabacrcac1.azurecr.io/onboarding-api:${RELEASE}
ENV?=DEV


clean:
		mvn clean

build: clean
	export MAVEN_OPTS=$(cat ~/.mvn/jvm.config) && echo "MAVEN_OPTS: " ${MAVEN_OPTS} && mvn install

run: container
		docker stop ${APP} || true && docker rm ${APP} || true
		docker run --name ${APP} --rm ${IMAGE}

push: container
		docker push ${IMAGE}

container: build
		docker build -t ${IMAGE} .
