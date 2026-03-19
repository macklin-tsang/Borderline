-- Remove geo-restriction feature (GeoIpService/GeoRestrictionService removed from backend)
DROP TABLE IF EXISTS api_key_geo_restrictions;
ALTER TABLE api_request_logs DROP COLUMN IF EXISTS country_code;
