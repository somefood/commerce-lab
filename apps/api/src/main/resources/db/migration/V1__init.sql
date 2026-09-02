create table products
(
    id             bigserial primary key,
    name           varchar(255),
    description    text,
    price          bigint,
    stock_quantity integer,
    status         varchar(255) not null
);

create table members
(
    id       bigserial primary key,
    name     varchar(255),
    email    varchar(255) not null unique,
    password varchar(255),
    role     varchar(255)
);
