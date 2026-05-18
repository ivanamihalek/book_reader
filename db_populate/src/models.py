from peewee import Model, AutoField, CharField, ForeignKeyField, IntegerField
from settings import global_db_proxy

class Book(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    title = CharField(unique=True)  # Ensuring title is unique for lookups
    author = CharField()

    class Meta:
        database = global_db_proxy
        table_name = 'books'


class Chapter(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    book = ForeignKeyField(Book, backref='chapters', on_delete='CASCADE', column_name='bookId')
    title = CharField()
    fileName = CharField()
    playTime = IntegerField(default=0)
    lastPlayedPosition = IntegerField(default=0)
    lastPlayedTimestamp = IntegerField(default=0)
    finishedPlaying = IntegerField(default=0)

    class Meta:
        database = global_db_proxy
        table_name = 'chapters'
