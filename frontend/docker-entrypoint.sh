#!/bin/sh
set -eu

: "${PUBLIC_BACKEND_URL:=/api}"

cat > /usr/share/nginx/html/env.js <<EOF
window.__RDFARCHITECT_CONFIG__ = {
    PUBLIC_BACKEND_URL: "${PUBLIC_BACKEND_URL}",
};
EOF

exec nginx -g "daemon off;"
