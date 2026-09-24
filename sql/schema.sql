-- Lab 2 schema. Run this in the Supabase SQL Editor.
-- This DROPS and recreates everything from scratch, matching the lab's
-- requirement that the script fully reproduce the current schema.

drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id varchar(20) primary key,
    name varchar(100) not null,
    stock integer not null default 0
);

-- An order no longer carries a single product/quantity - it is now a
-- header row, with its line items living in order_items.
create table orders (
    order_id bigserial primary key,
    status varchar(20) not null,      -- CONFIRMED / REJECTED / CANCELLED
    reason varchar(200),
    created_at timestamptz not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id bigint not null references orders(order_id) on delete cascade,
    product_id varchar(20) not null references inventory(product_id),
    quantity integer not null
);

create table notifications (
    notification_id bigserial primary key,
    message varchar(500) not null,
    created_at timestamptz not null default now()
);

create table supplier_orders (
    id bigserial primary key,
    product_id varchar(20) not null references inventory(product_id),
    buyer_ref varchar(40) not null unique,
    request_id varchar(80) not null unique,
    po_number varchar(40),
    cases integer not null,
    units integer not null,
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- RLS enabled on all tables. No policies are defined because the backend
-- connects via a direct Postgres connection (JDBC/pooler) using the
-- postgres role, which bypasses RLS. This only matters if something
-- accesses these tables through Supabase's REST/GraphQL API with
-- anon/authenticated keys, which this project does not use.
alter table inventory enable row level security;
alter table orders enable row level security;
alter table order_items enable row level security;
alter table notifications enable row level security;
alter table supplier_orders enable row level security;
-- Seed data
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
