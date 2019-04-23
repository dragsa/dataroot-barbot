INSERT INTO bars (name, info_source, is_active, id) VALUES ('Staromak', 'http://localhost:8888/Staromak', true, 1);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD1', 'http://localhost:8888/PD1', true, 2);
-- unreachable
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD2', 'http://192.168.1.1:8888/PD2', true, 3);
-- wrong content
INSERT INTO bars (name, info_source, is_active, id) VALUES ('Error', 'http://localhost:8888/Error', true, 5);
-- not-existent resource
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD3', 'http://localhost:8888/PD3', false, 4);