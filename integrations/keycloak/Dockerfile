# Keycloak with the HaramYar mobile-username provider and login theme (optimized build).
# Build first: ./mvnw -pl integrations/keycloak/mobile-username-spi package
FROM quay.io/keycloak/keycloak:26.3.3 AS builder
ENV KC_DB=postgres KC_HEALTH_ENABLED=true KC_METRICS_ENABLED=false
COPY mobile-username-spi/target/haramyar-keycloak-mobile-username.jar /opt/keycloak/providers/
COPY themes/haramyar /opt/keycloak/themes/haramyar
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.3.3
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY realm/haramyar-realm.json /opt/keycloak/data/import/haramyar-realm.json
USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
