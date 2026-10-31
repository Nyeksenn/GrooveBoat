-- Older versions stored artist image paths as plain file paths, but Ebean reads Path columns as file URIs, so
-- loading such an artist failed (and with it the song list of every album it appears on). The next artist refresh
-- caches the images again.
UPDATE artist SET artist_image_path = NULL WHERE artist_image_path NOT LIKE 'file:%';
