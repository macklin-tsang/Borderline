from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="Borderline GeoIP Service", version="0.1.0")


class GeoIPResponse(BaseModel):
    ip: str
    latitude: float
    longitude: float
    country_code: str
    city: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/lookup", response_model=GeoIPResponse)
def lookup(ip: str):
    """
    Stub implementation — full MaxMind integration added on Day 14.
    Returns zeroed coordinates for all IPs in this scaffold.
    """
    if not ip:
        raise HTTPException(status_code=400, detail="ip parameter is required")

    return GeoIPResponse(
        ip=ip,
        latitude=0.0,
        longitude=0.0,
        country_code="XX",
        city="Unknown",
    )
