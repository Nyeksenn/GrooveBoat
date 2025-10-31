-- apply alter tables
alter table song add column album_id bigint;
-- foreign keys and indices
create index ix_song_album_id on song (album_id);
alter table song add constraint fk_song_album_id foreign key (album_id) references album (id) on delete restrict on update restrict;

