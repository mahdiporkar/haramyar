# Keycloak behind the gateway: same public origin as the shell (issuer = <origin>/realms/haramyar).
proxy_set_header Host $http_host;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
proxy_set_header X-Forwarded-Port $http_x_forwarded_port;
proxy_set_header X-Forwarded-Host $http_host;
proxy_http_version 1.1;
proxy_buffer_size 128k;
proxy_buffers 4 256k;
proxy_busy_buffers_size 256k;
