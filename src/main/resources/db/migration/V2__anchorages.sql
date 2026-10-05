-- Source: Vancouver Fraser Port Authority, Port Information Guide (August 2026),
-- section 14.6 "Anchorages (tables)", pages 185-187.
-- Positions are copied as printed: degrees, minutes, seconds (N latitude, W longitude).
-- Roberts Bank (R) and Sandheads (S) are left out: they are outside our map area.
--
-- radius_m is the guide's "maximum vessel length overall" for each anchorage. The guide
-- gives no swing radius, so this is a first estimate; we calibrate it against real ship
-- positions once data is flowing.

INSERT INTO anchorages (id, name, area, center, radius_m)
SELECT id, name, area,
       -- ST_MakePoint takes (longitude, latitude), x first. West longitudes are negative.
       ST_SetSRID(ST_MakePoint(-(lon_d + lon_m / 60.0 + lon_s / 3600.0),
                               lat_d + lat_m / 60.0 + lat_s / 3600.0), 4326)::geography,
       radius_m
FROM (VALUES
    -- id,    name,            area,           lat d  m  s     lon d   m  s     radius_m
    ('E01',   'Anchorage 1',   'English Bay',  49, 17, 57,     123, 14, 19,      400),
    ('E02',   'Anchorage 2',   'English Bay',  49, 17, 33,     123, 13, 53,      260),
    ('E03',   'Anchorage 3',   'English Bay',  49, 18,  4,     123, 13, 33,      400),
    ('E04',   'Anchorage 4',   'English Bay',  49, 17, 39,     123, 13, 11,      260),
    ('E05',   'Anchorage 5',   'English Bay',  49, 17, 15,     123, 12, 42,      230),
    ('E06',   'Anchorage 6',   'English Bay',  49, 18, 12,     123, 12, 48,      400),
    ('E07',   'Anchorage 7',   'English Bay',  49, 17, 47,     123, 12, 25,      260),
    ('E08',   'Anchorage 8',   'English Bay',  49, 17, 22,     123, 11, 59,      230),
    ('E09',   'Anchorage 9',   'English Bay',  49, 16, 56,     123, 11, 33,      200),
    ('E10',   'Anchorage 10',  'English Bay',  49, 18, 19,     123, 12,  3,      400),
    ('E11',   'Anchorage 11',  'English Bay',  49, 17, 54,     123, 11, 38,      260),
    ('E12',   'Anchorage 12',  'English Bay',  49, 17, 29,     123, 11, 14,      230),
    ('E13',   'Anchorage 13',  'English Bay',  49, 17,  5,     123, 10, 49,      200),
    ('E14',   'Anchorage 14',  'English Bay',  49, 18, 25,     123, 11, 19,      400),
    ('E15',   'Anchorage 15',  'English Bay',  49, 18,  1,     123, 10, 53,      260),
    ('E16',   'Anchorage 16',  'English Bay',  49, 19, 57,     123, 13,  8,      260),
    ('E17',   'Anchorage 17',  'English Bay',  49, 19, 56,     123, 13, 54,      260),
    ('E18',   'Anchorage 18',  'English Bay',  49, 19, 55,     123, 14, 39,      260),
    ('EU',    'Uniform (U)',   'English Bay',  49, 17, 45,     123, 15, 13,      400),  -- short term only
    ('EZ',    'Zulu (Z)',      'English Bay',  49, 17,  9,     123, 10,  0,      100),
    ('IH-A',  'Alpha (A)',     'Inner Harbour', 49, 18, 11,    123,  5, 26,      300),
    ('IH-B',  'Bravo (B)',     'Inner Harbour', 49, 18,  6,    123,  4, 46,      260),
    ('IH-C',  'Charlie (C)',   'Inner Harbour', 49, 18,  1,    123,  4, 11,      260),
    ('IH-D',  'Delta (D)',     'Inner Harbour', 49, 17, 39,    123,  5,  3,      300),  -- emergency anchorage
    ('IH-E',  'Echo (E)',      'Inner Harbour', 49, 17, 41.8,  123,  3, 55,      200),
    ('IH-W',  'Whiskey (W)',   'Inner Harbour', 49, 17, 43,    123,  5, 54,      300),  -- short term only
    ('IH-X',  'X-ray (X)',     'Inner Harbour', 49, 18, 17,    123,  6,  5,      200),
    ('IH-Y',  'Yankee (Y)',    'Inner Harbour', 49, 18,  1,    123,  3, 35,      260),  -- short term only
    ('IA-K',  'Kilo (K)',      'Indian Arm',   49, 18,  3,     122, 56, 41,      185),
    ('IA-L',  'Lima (L)',      'Indian Arm',   49, 17, 59,     122, 56,  6,      250),
    ('IA-M',  'Mike (M)',      'Indian Arm',   49, 18, 23,     122, 56, 17,      250),
    ('IA-N',  'November (N)',  'Indian Arm',   49, 17, 37.2,   122, 58,  7.6,    185)
) AS t (id, name, area, lat_d, lat_m, lat_s, lon_d, lon_m, lon_s, radius_m);
