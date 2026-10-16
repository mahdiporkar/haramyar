-- Each book has three independently reviewed editions. Original documents and
-- translated PDFs live only in a private MinIO bucket, never in PostgreSQL.
create table martyr_book (
  id uuid primary key,
  profile_id uuid not null references martyr_profile(id),
  kind text not null check (kind in ('AUTHORED','ABOUT')),
  original_title text not null,
  source_url text not null,
  created_at timestamptz not null default now()
);
create index martyr_book_profile on martyr_book(profile_id,kind,created_at);

create table martyr_book_edition (
  id uuid primary key,
  book_id uuid not null references martyr_book(id),
  locale text not null check (locale in ('ar','fa','en')),
  title text not null,
  summary text,
  storage_key text unique,
  sha256 text,
  size_bytes bigint,
  status text not null default 'DRAFT' check (status in ('DRAFT','IN_REVIEW','PUBLISHED')),
  submitted_by uuid,
  published_by uuid,
  published_at timestamptz,
  version bigint not null default 0,
  unique (book_id,locale),
  check ((storage_key is null and sha256 is null and size_bytes is null)
      or (storage_key is not null and sha256 is not null and size_bytes between 1 and 31457280))
);
create index martyr_book_edition_public on martyr_book_edition(book_id,locale) where status='PUBLISHED';
