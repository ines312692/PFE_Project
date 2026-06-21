FRONTEND_DIR := Frontend

.PHONY: install start build test lint watch clean help

help:
	@echo "Available targets:"
	@echo "  install  - Install npm dependencies"
	@echo "  start    - Start the dev server (ng serve)"
	@echo "  build    - Build the application"
	@echo "  test     - Run unit tests (ng test)"
	@echo "  lint     - Run ESLint"
	@echo "  watch    - Build in watch mode"
	@echo "  clean    - Remove node_modules and dist"

install:
	cd $(FRONTEND_DIR) && npm install

start:
	cd $(FRONTEND_DIR) && npm run start

build:
	cd $(FRONTEND_DIR) && npm run build

test:
	cd $(FRONTEND_DIR) && npm run test

lint:
	cd $(FRONTEND_DIR) && npm run lint

watch:
	cd $(FRONTEND_DIR) && npm run watch

clean:
	rm -rf $(FRONTEND_DIR)/node_modules $(FRONTEND_DIR)/dist


push:
	ssh-add ~/.ssh/id_rsa_elyadata
	-ssh -T git@github.com
	git push

