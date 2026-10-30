-- Persist the settlement cursor so the ticker only processes newly ended rounds.
alter table matches add column settled_rounds integer not null default 0;
