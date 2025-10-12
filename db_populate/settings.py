from peewee import Proxy

# --- Configuration ---
DATABASE_PATH = 'bookreader.db'
DEVICE_BASE_AUDIO_PATH = '/sdcard/Audiobooks/BookReader/audio'

# Use a global proxy for the database connection
# This allows models to be defined before the database connection is initialized
global_db_proxy = Proxy()

