# Salim memorial: original covers and multilingual redesign

## Publication baseline and Git history

The publication branch was reviewed and merged through [PR #3](https://github.com/mahdiporkar/haramyar/pull/3). Merge commit: `e2b63e34ea0cf271bfdd388046fa83ed531c9eb0`. All three CI jobs passed (run `39066931643`). Before the redesign, the unchanged production library suite passed **7/7**, including SHA-256 verification of all **63 PDF editions** (45 Arabic, 9 Persian, 9 English). The redesign branch `feat/salim-memorial-covers` starts from that merged main.

The unrelated local `tools/reset-local-staff-login.mjs` is excluded. Production versions before this change: public-web v4, gateway/web v7, martyrs-api v2, martyrs-frontend v2, books-storage v1. Only public-web needs deployment.

## Authentic assets and matching

Source: [official catalogue](https://www.shahidsalim.com/books). Its server-rendered `openBookModal` records expose source identifiers, Arabic titles and original PNG URLs. The source ID was joined to the existing edition inventory; each resulting cover title was visually checked in contact sheets against that work. No fuzzy-only or array-position matching was used.

- **43 authenticated source cover images** for 45 books; **2 missing** (source IDs `11` and `5`). The official source supplies a third-party “No Cover” placeholder for these; no placeholder was downloaded or represented as an original.
- Official entries `15` and `37` intentionally show the same artwork/title. This source duplication is documented in the mapping rather than silently assigning different artwork.
- Source IDs `45` and `28` are only 120 px wide; ID `2` is 300 px. Originals were not enlarged or reconstructed. Responsive descriptors reflect actual pixel widths.
- All cards explicitly label the image as the **original Arabic cover**, including cards offering Persian or English translations.
- The complete provenance, source URLs, book UUIDs, titles, dimensions, PNG hashes, WebP filenames and exceptions are in [salim-covers.json](../martyrs/salim-covers.json).

Originals: `apps/public-web/assets/covers/originals/` (43 PNGs, **3,357,881 bytes**). Delivery: `apps/public-web/assets/covers/web/` (86 WebPs, **1,761,654 bytes** total; average **20,484**, maximum **55,898** bytes). Requested widths are 180 and 360, capped at the original size, preserving aspect ratio; encoding quality 85. No historical text/artwork has been edited.

Reproduce optimized assets without network or credentials:

```sh
npm install --prefix .local/cover-tools --no-save @napi-rs/canvas@1.0.11
node tools/rebuild-salim-covers.mjs .local/cover-tools/node_modules/@napi-rs/canvas/index.js
node tools/verify-salim-covers.mjs
```

The tool refuses changed originals. New source artwork requires a new explicit visual review. Encoder/platform differences may produce different hashes; review the manifest before committing. Production builds use checked-in assets and do not depend on this native image tool.

## Storage and architecture

The existing book domain exposes editions and private PDF objects, not cover media references. Profile media is editorial/profile-scoped. For this curated, fixed collection, reuse public-web's existing static asset delivery rather than attach covers to a profile gallery, change PDF storage permissions, or add an unrelated storage service.

`/martyr/_assets/covers/<sourceId>-<size>-<hash>.webp` is first-party, allowlisted by the reviewed manifest, and cached for one year with `immutable`. Only optimized assets are copied into the service image. Originals stay in source control. The gateway already forwards this path. No database migration, book/PDF mutation, credentials, new backend endpoints or infrastructure changes are required. This is a presentation mapping, scoped to Salim and exact original Arabic titles; it is not a general editorial cover-upload feature.

## Design and behavior

The old reading layout becomes an emerald, ivory and restrained gold memorial with the authentic existing portrait, architectural arch, CSS geometric detail, localized hero actions, sticky section navigation, editorial biography, verified dates, references, and a compact responsive library. Shared fonts and site identity remain in use; English editorial headings use a serif face. Existing biography, stories, dates, sources, canonical URLs, alternate language links, sitemap and Person structured data remain intact.

All 45 books remain server-rendered, ordered as before. Persian and English each prefer their 9 translations and explicitly mark the 36 Arabic fallbacks. All 63 download destinations remain unchanged. Search normalizes Arabic/Persian letter forms and searches every available title. An optional translated-only filter returns 9 books. Both progressively enhance the SSR catalogue: no JavaScript is needed to read or download.

Below-fold images are lazy, dimensioned, responsive and uncropped. The portrait has high fetch priority. Missing and failed images have an honest branded fallback. Client JavaScript is **1,741 bytes uncompressed**, with no added runtime library or hydration. The light ivory design, semantic landmarks/headings, skip link, visible keyboard focus, input labels, live result count, per-title language/direction and reduced-motion handling support accessibility. Browser tests verify overflow at 1366/390 px and a 320 px image-failure case. These checks are not a claim of a full assistive-technology audit or a measured Core Web Vitals score.

## Validation before merge

- Full workspace TypeScript checks and public-web build passed.
- Cover verification checks all 45 mappings, original PNG hashes and dimensions, WebP signatures and filename hashes.
- New browser suite: **8/8 passed** against the local built service with live public data. Six language/viewport combinations verify biography equality, all covers decoded, locale/fallback counts, search and translation filters, SEO, no JS errors and no horizontal overflow; remaining cases verify image failures, no-JS downloads and all 86 delivery URLs.
- Unit suite initially exposed an existing Windows `URL.pathname` path bug in the Hive route coverage test. Reading with a file URL fixes Windows compatibility without changing any assertions.
- Screenshots generated under `tests/e2e/test-results/memorial/`: `<fa|ar|en>-<1366|390>-<hero|biography|library|references>.png`. Hero, authentic portrait, RTL/LTR book cards, bibliography and mobile prose were visually inspected. Final production evidence will be recorded below.

## Production completion

Pending the redesign PR's required checks, merge and targeted deployment. The completion record will include its final main SHA, CapRover version, production browser/download results and screenshots. No deployment success is inferred from the local build.
