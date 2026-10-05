CREATE EXTENSION IF NOT EXISTS postgis;

-- The ~28 deep-sea anchorages published by the port: a centre point plus a swing radius.
CREATE TABLE anchorages (
    id        text PRIMARY KEY,                 -- e.g. 'E01'
    name      text,
    area      text,                             -- English Bay, Burrard Inlet, ...
    center    geography(Point, 4326) NOT NULL,  -- geography: distances in metres
    radius_m  integer NOT NULL
);

-- Latest known position of each ship, updated in place.
CREATE TABLE vessels (
    mmsi        bigint PRIMARY KEY,             -- 9-digit ship identifier
    name        text,
    position    geography(Point, 4326) NOT NULL,
    sog_knots   real,                           -- speed over ground
    updated_at  timestamptz NOT NULL
);

-- One row per stay at an anchorage.
CREATE TABLE anchorage_visits (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mmsi           bigint NOT NULL,
    anchorage_id   text NOT NULL REFERENCES anchorages (id),
    arrived_at     timestamptz NOT NULL,
    departed_at    timestamptz,                 -- NULL = still waiting
    seen_arriving  boolean NOT NULL             -- false if already anchored when we first saw it
);

-- A ship can have at most one open visit; the database enforces it.
CREATE UNIQUE INDEX one_open_visit_per_ship ON anchorage_visits (mmsi) WHERE departed_at IS NULL;
