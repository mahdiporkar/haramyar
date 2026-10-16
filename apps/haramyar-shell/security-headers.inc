# Security headers for the shell and public pages. Micro-frontends are evaluated from integrity-verified Blob URLs
# (Hive mfe-runtime), hence blob: in script-src; shadow roots carry inline styles. Decorative prototype photos are the
# only third-party resource (images.unsplash.com) until self-hosted copies replace the --hy-photo-* variables.
add_header Content-Security-Policy "default-src 'self'; script-src 'self' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://images.unsplash.com; font-src 'self'; connect-src 'self'; manifest-src 'self'; worker-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'" always;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
add_header X-Frame-Options DENY always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=()" always;
add_header Cross-Origin-Opener-Policy same-origin always;
