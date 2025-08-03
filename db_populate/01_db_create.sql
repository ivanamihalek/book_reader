# turns out this is not such a great idea bcs Room is too picky abou the format
# Create the database
# sqlite3 bookreader.db < this_script.sql


# Create tables with the exact same schema Room expects
CREATE TABLE  IF NOT EXISTS  books (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL default 1,
    title TEXT NOT NULL,
    author TEXT NOT NULL
);

CREATE TABLE  IF NOT EXISTS  chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT  NOT NULL default 1,
    bookId INTEGER NOT NULL,
    title TEXT NOT NULL,
    fileName TEXT NOT NULL,
    lastPlayedPosition INTEGER NOT NULL DEFAULT 0,
    lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0,
    finishedPlaying INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
);
CREATE INDEX "chapter_bookId" ON "chapters" ("bookId");

-- Room master table
CREATE TABLE  IF NOT EXISTS  room_master_table (
    id INTEGER PRIMARY KEY,
    identity_hash TEXT
);



-- Insert the identity hash (you'll need to calculate this based on your schema)
-- For now, you can leave it empty and Room will handle it
-- INSERT INTO room_master_table (id, identity_hash) VALUES (42, '');

-- -- If using FTS (Full Text Search)
-- CREATE TABLE IF NOT EXISTS `room_fts_content_sync_trigger` (
--     id INTEGER PRIMARY KEY,
--     table_name TEXT,
--     trigger_name TEXT
-- );
.exit