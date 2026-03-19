.PHONY: dev demo down test build clean logs

dev:
	docker-compose up --build

# Start detached and seed a demo account + geofence + device.
# Open http://localhost:3000, log in, select "Demo Device" in the top bar,
# then click anywhere on the map to move it and trigger live ENTER/EXIT alerts.
demo:
	docker-compose up --build -d
	bash scripts/demo-seed.sh

down:
	docker-compose down

test:
	cd backend && mvn test -q
	cd frontend && npm test -- --watchAll=false

build:
	docker-compose build

clean:
	docker-compose down -v
	cd backend && mvn clean -q

logs:
	docker-compose logs -f backend frontend
