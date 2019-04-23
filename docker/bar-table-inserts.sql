CREATE TABLE bars (
                name varchar(100) not null
               	    constraint bars_name_key unique,
               	info_source varchar not null,
               	is_active boolean not null,
               	id serial not null
               		constraint bars_pkey primary key
               );

INSERT INTO bars (name, info_source, is_active, id) VALUES ('Staromak', 'http://bars:80/Staromak', true, 1);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD1', 'http://bars:80/PD1', true, 2);
-- unreachable
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD2', 'http://bars-fake:80/PD2', true, 3);
-- wrong content
INSERT INTO bars (name, info_source, is_active, id) VALUES ('Error', 'http://bars:80/Error', true, 5);
-- not-existent resource
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD3', 'http://bars:80/PD3', false, 4);