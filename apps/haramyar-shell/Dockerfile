# HaramYar gateway: the single browser origin. Serves the shell, routes Hive paths to the BFF, the identity realm to
# Keycloak and public SEO pages to public-web. Build first: npm run build -w apps/haramyar-shell
FROM nginx:1.29.1-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV HARAMYAR_BFF_UPSTREAM=http://127.0.0.1:8081 HARAMYAR_KEYCLOAK_UPSTREAM=http://keycloak:8080 HARAMYAR_PUBLIC_WEB_UPSTREAM=http://public-web:8093 HARAMYAR_CONSOLE_UPSTREAM=http://operator-console:8080 HARAMYAR_RESOLVER=127.0.0.11
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
COPY security-headers.inc keycloak-proxy.inc /etc/nginx/conf.d/
