# Borderline

Live AIS ship tracking for the Port of Vancouver: detects ships waiting at English Bay / Burrard Inlet anchorages with PostGIS and measures how long they wait.

Status: v2 rebuild in progress - see .claude/plans/borderline-v2.plan.md

## Run

```
cp .env.example .env
docker compose up -d db
```

The app service arrives in a later phase.
