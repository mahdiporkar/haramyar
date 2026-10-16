-- Martyrs schema (owned exclusively by the martyrs service).
create extension if not exists pg_trgm;

create table martyr_profile (
  id                 uuid        primary key,
  slug               text        not null unique,
  status             text        not null check (status in ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
  full_name          text        not null,
  honorific          text,
  content_locale     text        not null check (content_locale in ('fa', 'ar', 'en')),
  short_description  text,
  biography          text,
  birth              jsonb,
  martyrdom          jsonb,
  seo_title          text,
  seo_description    text,
  portrait           jsonb,
  gallery            jsonb       not null default '[]',
  sources            jsonb       not null default '[]',
  stories            jsonb       not null default '[]',
  created_by         uuid        not null,
  submitted_by       uuid,
  created_at         timestamptz not null,
  updated_at         timestamptz not null,
  first_published_at timestamptz,
  published_at       timestamptz,
  search_text        text        not null,
  version            bigint      not null
);
create index martyr_profile_status on martyr_profile (status, updated_at desc);
-- Public search only ever scans published rows.
create index martyr_profile_published_search on martyr_profile using gin (search_text gin_trgm_ops) where status = 'PUBLISHED';

create table martyr_editorial_history (
  id          bigserial   primary key,
  profile_id  uuid        not null references martyr_profile (id),
  sequence_no integer     not null,
  operation   text        not null,
  from_status text,
  to_status   text        not null,
  actor_id    uuid        not null,
  actor_role  text        not null,
  note        text,
  occurred_at timestamptz not null,
  constraint martyr_history_sequence unique (profile_id, sequence_no)
);
create or replace function martyr_history_append_only() returns trigger language plpgsql as $$
begin
  raise exception 'martyr_editorial_history is append-only';
end $$;
create trigger martyr_history_no_update before update or delete on martyr_editorial_history
  for each row execute function martyr_history_append_only();

create table media_object (
  id           uuid        primary key,
  profile_id   uuid        not null references martyr_profile (id),
  content_type text        not null check (content_type in ('image/jpeg', 'image/png', 'image/webp')),
  size_bytes   bigint      not null check (size_bytes > 0 and size_bytes <= 5242880),
  sha256       text        not null,
  storage_key  text        not null unique,
  uploaded_by  uuid        not null,
  uploaded_at  timestamptz not null
);
