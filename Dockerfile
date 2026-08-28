# Minimal OCI image that carries just the built JAR.
# Consumed by other repos' Dockerfiles via:
#   COPY --from=ghcr.io/sky-cloak/keycloak-idp-discovery:vX.Y.Z /jars/*.jar /opt/keycloak/providers/
#
# We do NOT include a JRE or an entrypoint. This image is never run, only pulled
# from for its artifact layer.
FROM scratch
COPY target/keycloak-idp-discovery*.jar /jars/
