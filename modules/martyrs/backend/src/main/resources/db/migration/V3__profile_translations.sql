-- Published profile prose can be translated independently of the source
-- profile and independently of full PDF book editions. Unreviewed translations
-- must never be returned through the public API.
create table martyr_profile_translation (
  profile_id uuid not null references martyr_profile(id),
  locale text not null check (locale in ('fa','ar','en')),
  full_name text not null,
  honorific text,
  short_description text not null,
  biography text not null,
  seo_title text,
  seo_description text,
  status text not null default 'DRAFT' check (status in ('DRAFT','IN_REVIEW','PUBLISHED')),
  submitted_by uuid,
  published_by uuid,
  published_at timestamptz,
  version bigint not null default 0,
  primary key (profile_id,locale)
);
create index martyr_profile_translation_public on martyr_profile_translation(profile_id,locale)
  where status = 'PUBLISHED';
