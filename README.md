# Borderline

Live AIS ship tracking for the Port of Vancouver: detects ships waiting at English Bay / Burrard Inlet anchorages with PostGIS and measures how long they wait.

Status: v2 rebuild in progress - see .claude/plans/borderline-v2.plan.md

## Run

```
cp .env.example .env          # then put your DB_PASSWORD and AISSTREAM_API_KEY in .env
docker compose up --build -d  # starts PostGIS and the app
docker compose logs -f app    # watch it connect to AISStream and detect anchored ships
```

Needs Docker. The app listens on http://localhost:8080 (the API and map arrive in later phases).
