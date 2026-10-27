USE hanbaguni;

ALTER TABLE users
ADD CONSTRAINT uq_users_display_name UNIQUE (display_name);