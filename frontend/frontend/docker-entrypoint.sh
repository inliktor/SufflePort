#!/bin/sh
set -eu

CERT_DIR="${CERT_DIR:-/etc/nginx/certs}"
CERT_FILE="${CERT_DIR}/cert.pem"
KEY_FILE="${CERT_DIR}/key.pem"
CERT_HOST="${CERT_HOST:-localhost}"
CERT_ALT_NAMES="${CERT_ALT_NAMES:-DNS:localhost,IP:127.0.0.1}"
CERT_DAYS="${CERT_DAYS:-825}"

mkdir -p "${CERT_DIR}"

if [ ! -s "${CERT_FILE}" ] || [ ! -s "${KEY_FILE}" ]; then
  echo "[tls] generating self-signed certificate for ${CERT_HOST}"
  openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout "${KEY_FILE}" \
    -out "${CERT_FILE}" \
    -days "${CERT_DAYS}" \
    -subj "/CN=${CERT_HOST}" \
    -addext "subjectAltName = ${CERT_ALT_NAMES}" \
    -addext "keyUsage = digitalSignature, keyEncipherment" \
    -addext "extendedKeyUsage = serverAuth"
fi

exec nginx -g 'daemon off;'
