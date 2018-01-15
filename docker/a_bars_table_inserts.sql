-- should be executed only in prod environment after barbot application started
INSERT INTO bars (name, info_source, is_active, id) VALUES ('Staromak', 'http://bars:8888/Staromak', true, 1);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD1', 'http://bars:8888/PD1', true, 2);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD2', 'http://bars-fake:8888/PD2', true, 3);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('Error', 'http://bars:8888/Error', true, 5);
INSERT INTO bars (name, info_source, is_active, id) VALUES ('PD3', 'http://bars:8888/PD3', false, 4);