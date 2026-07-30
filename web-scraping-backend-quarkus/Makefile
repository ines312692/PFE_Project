APP_NAME = web-scraping-backend-quarkus
VERSION = 1.0.0-SNAPSHOT
DOCKER_IMAGE = scraping/backend
MVN = ./mvnw

BLUE = \033[36m
RESET = \033[0m

.PHONY: help dev build test clean package native docker-build up down logs ps

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(BLUE)%-20s$(RESET) %s\n", $$1, $$2}'


dev:
	$(MVN) quarkus:dev

clean:
	$(MVN) clean

build:
	$(MVN) compile

test:
	$(MVN) test

package:
	$(MVN) package -DskipTests

native:
	$(MVN) package -Dnative -DskipTests


docker-build:
	docker build -t $(DOCKER_IMAGE):latest -t $(DOCKER_IMAGE):$(VERSION) .

up:
	docker-compose --profile dev up -d

down:
	docker-compose --profile dev down

logs:
	docker-compose --profile dev logs -f quarkus-backend-dev

ps:
	docker-compose --profile dev ps

format:
	$(MVN) spotless:apply || echo "Spotless non configuré"

dependency-check:
	$(MVN) dependency-check:check


.PHONY: dev push

push:
	ssh-add ~/.ssh/id_rsa_elyadata
	-ssh -T git@github.com
	git push