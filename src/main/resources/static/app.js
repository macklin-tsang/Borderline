'use strict';

// The map page: draws the anchorages and ships, and keeps them up to date by polling the API.
// Ship names are untrusted radio text, so everything below builds DOM nodes with textContent.
// Nothing here ever parses a string as HTML (StaticPageTest fails the build if that changes).

const VESSEL_REFRESH_MS = 10_000;
const STATS_REFRESH_MS = 60_000;

const ANCHORED_STYLE = { radius: 7, color: '#ffffff', weight: 1.5, fillColor: '#d9480f', fillOpacity: 0.9 };
const OTHER_STYLE = { radius: 4, color: '#ffffff', weight: 1, fillColor: '#495057', fillOpacity: 0.7 };

const map = L.map('map').setView([49.29, -123.15], 12);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 18,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
}).addTo(map);

const shipMarkers = new Map();  // mmsi -> circle marker, so a ship's marker moves instead of being redrawn

// ---- small helpers ----

/** A new element whose text is set with textContent, so it can never be read as HTML. */
function el(tag, text) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  return node;
}

async function getJson(path) {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${path} answered ${response.status}`);
  return response.json();
}

function setStatus(text, isError = false) {
  const status = document.getElementById('status');
  status.textContent = text;
  status.classList.toggle('error', isError);
}

/** 3 days 4 h -> "3d 4h"; 5 h 12 min -> "5h 12m"; 7 min -> "7m". */
function formatDuration(ms) {
  const minutes = Math.max(0, Math.floor(ms / 60_000));
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes % 60}m`;
  return `${minutes}m`;
}

/** How long a ship has waited. "≥" when it was already anchored before we started watching. */
function waited(vessel) {
  const text = formatDuration(Date.now() - Date.parse(vessel.anchoredSince));
  return vessel.arrivalSeen ? text : `≥ ${text}`;
}

function shipName(vessel) {
  return vessel.name || `MMSI ${vessel.mmsi}`;
}

function hours(value) {
  return value == null ? '–' : value.toFixed(1);
}

/** Replaces a table's rows, or shows one message row when there are none. */
function fillBody(tableId, rows, columns, emptyMessage) {
  if (rows.length === 0) {
    const cell = el('td', emptyMessage);
    cell.colSpan = columns;
    const row = el('tr');
    row.append(cell);
    rows = [row];
  }
  document.querySelector(`#${tableId} tbody`).replaceChildren(...rows);
}

// ---- the anchorage circles (drawn once) ----

async function drawAnchorages() {
  const anchorages = await getJson('/api/anchorages');
  const circles = anchorages.map((a) => {
    // reachM is the distance the detector uses, so the circle is exactly where a ship counts as anchored
    const circle = L.circle([a.lat, a.lon], { radius: a.reachM, color: '#1971c2', weight: 1, fillOpacity: 0.08 });
    circle.bindTooltip(el('span', `${a.id} · ${a.name}, ${a.area}`), { sticky: true });
    return circle.addTo(map);
  });
  map.fitBounds(L.featureGroup(circles).getBounds());
}

// ---- the ships (refreshed every 10 seconds) ----

function shipLabel(vessel) {
  const box = el('div');
  box.append(el('strong', shipName(vessel)));
  if (vessel.anchorageId) {
    box.append(el('div', `waiting ${waited(vessel)} at ${vessel.anchorageId}`));
  } else {
    box.append(el('div', vessel.sogKnots == null ? 'speed unknown' : `${vessel.sogKnots.toFixed(1)} knots`));
  }
  return box;
}

function showShips(vessels) {
  const present = new Set();
  for (const vessel of vessels) {
    present.add(vessel.mmsi);
    const style = vessel.anchorageId ? ANCHORED_STYLE : OTHER_STYLE;
    let marker = shipMarkers.get(vessel.mmsi);
    if (marker) {
      marker.setLatLng([vessel.lat, vessel.lon]);
      marker.setStyle(style);
      marker.setRadius(style.radius);
      marker.setTooltipContent(shipLabel(vessel));
    } else {
      marker = L.circleMarker([vessel.lat, vessel.lon], style).addTo(map);
      marker.bindTooltip(shipLabel(vessel));
      shipMarkers.set(vessel.mmsi, marker);
    }
  }
  // Ships we have not heard from lately are no longer in the list: take their markers off the map
  for (const [mmsi, marker] of shipMarkers) {
    if (!present.has(mmsi)) {
      marker.remove();
      shipMarkers.delete(mmsi);
    }
  }
}

function showAnchoredList(vessels) {
  const rows = vessels
    .filter((v) => v.anchorageId)
    .sort((a, b) => Date.parse(a.anchoredSince) - Date.parse(b.anchoredSince))  // longest wait first
    .map((v) => {
      const row = el('tr');
      row.append(el('td', shipName(v)), el('td', v.anchorageId), el('td', waited(v)));
      return row;
    });
  fillBody('anchored', rows, 3, 'No ships at anchor right now');
}

async function refreshShips() {
  try {
    const vessels = await getJson('/api/vessels');
    showShips(vessels);
    showAnchoredList(vessels);
    const atAnchor = vessels.filter((v) => v.anchorageId).length;
    setStatus(`${vessels.length} ships heard in the last 30 minutes · ${atAnchor} at anchor · updated ${new Date().toLocaleTimeString()}`);
  } catch (error) {
    setStatus('Cannot reach the server. Showing the last data and retrying…', true);
  }
}

// ---- the statistics table (refreshed every minute) ----

async function refreshStats() {
  try {
    const days = document.getElementById('days').value;
    const stats = await getJson(`/api/stats?days=${encodeURIComponent(days)}`);
    const rows = stats
      .filter((s) => s.waitingNow > 0 || s.completedVisits > 0)
      .map((s) => {
        const row = el('tr');
        const zone = el('td', s.id);
        zone.title = `${s.name}, ${s.area}`;
        row.append(zone, el('td', String(s.waitingNow)), el('td', hours(s.longestWaitHours)),
                   el('td', String(s.completedVisits)), el('td', hours(s.medianDwellHours)),
                   el('td', hours(s.avgDwellHours)));
        return row;
      });
    fillBody('stats', rows, 6, 'No visits yet');
  } catch (error) {
    // The status line already reports connection problems; keep the old table
  }
}

// ---- start ----

async function start() {
  try {
    await drawAnchorages();  // first, so the ship markers are drawn on top of the circles
  } catch (error) {
    setStatus('Could not load the anchorages.', true);
  }
  refreshShips();
  refreshStats();
  setInterval(refreshShips, VESSEL_REFRESH_MS);
  setInterval(refreshStats, STATS_REFRESH_MS);
  document.getElementById('days').addEventListener('change', refreshStats);
}

start();
