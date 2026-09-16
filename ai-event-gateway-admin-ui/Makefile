.PHONY: dev build ci docker-build compose-up compose-down prod-up prod-down health

dev:
	npm run dev

build:
	npm run build

ci:
	npm run ci

docker-build:
	./scripts/build-docker.sh .env.production.example

compose-up:
	docker compose up -d --build

compose-down:
	docker compose down

prod-up:
	./scripts/deploy-compose.sh .env.production

prod-down:
	docker compose -f docker-compose.prod.yml --env-file .env.production down

health:
	./scripts/healthcheck.sh
