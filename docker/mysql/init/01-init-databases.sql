-- ===========================================
-- Orai Database Initialization Script
-- ===========================================

-- Create databases for each service
CREATE DATABASE IF NOT EXISTS orai_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orai_calendar CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orai_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orai_etc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions to orai user
GRANT ALL PRIVILEGES ON orai_user.* TO 'orai'@'%';
GRANT ALL PRIVILEGES ON orai_calendar.* TO 'orai'@'%';
GRANT ALL PRIVILEGES ON orai_chat.* TO 'orai'@'%';
GRANT ALL PRIVILEGES ON orai_etc.* TO 'orai'@'%';
GRANT ALL PRIVILEGES ON orai.* TO 'orai'@'%';

FLUSH PRIVILEGES;
