-- products
alter table products alter column name set not null;
alter table products alter column price set not null;
alter table products alter column stock_quantity set not null;

-- members
alter table members alter column name set not null;
alter table members alter column password set not null;