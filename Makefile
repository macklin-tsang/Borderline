.PHONY: dev down test build clean logs

dev:
	docker-compose up --build

down:
	docker-compose down

test:
	cd backend && mvn test -q
	cd geoip-service && python -m pytest -q
	cd frontend && npm test -- --watchAll=false

build:
	docker-compose build

clean:
	docker-compose down -v
	cd backend && mvn clean -q

logs:
	docker-compose logs -f backend geoip
