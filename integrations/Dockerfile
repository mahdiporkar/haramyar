# One-shot bootstrap image: the Hive CLI of the pinned Hive commit (staged into .hive-cli by tools/images.mjs) plus the
# HaramYar registration and fixture scripts. Contains no secrets; everything comes from the environment at run time.
FROM node:22.20.0-alpine
WORKDIR /opt/hive
COPY .hive-cli/ /opt/hive/
RUN npm install --omit=dev --no-audit --no-fund --ignore-scripts=false esbuild@0.25.10 ./contracts && rm -rf /root/.npm
WORKDIR /haramyar
COPY hive/register.mjs hive/access.json /haramyar/integrations/hive/
COPY hive/solution /haramyar/integrations/hive/solution
COPY keycloak/dev-staff-fixtures.mjs /haramyar/integrations/keycloak/
COPY .modules/ /haramyar/modules/
ENV HIVE_CLI=/opt/hive/cli/bin/hive.mjs
USER node
CMD ["sh", "-c", "node integrations/hive/register.mjs && if [ \"${HARAMYAR_DEV_FIXTURES:-false}\" = true ]; then node integrations/keycloak/dev-staff-fixtures.mjs; fi"]
