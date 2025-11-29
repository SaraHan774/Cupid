#!/bin/sh
# Script to import AWS RDS/DocumentDB certificates into Java truststore

set -e

CERT_BUNDLE="/tmp/rds-ca-bundle.pem"
CERT_DIR="/tmp/certs"
TRUSTSTORE="$JAVA_HOME/lib/security/cacerts"
STOREPASS="changeit"

# Create directory for individual certificates
mkdir -p "$CERT_DIR"

# Split the bundle into individual certificates using awk
awk 'BEGIN {c=0}
     /-----BEGIN CERTIFICATE-----/ {c++}
     {print > "'"$CERT_DIR"'/cert-" c ".pem"}' "$CERT_BUNDLE"

# Import each certificate
for cert_file in "$CERT_DIR"/cert-*.pem; do
    if [ -f "$cert_file" ] && [ -s "$cert_file" ]; then
        cert_alias="aws-rds-ca-$(basename "$cert_file" .pem)"
        echo "Importing $cert_alias..."
        keytool -importcert -trustcacerts -cacerts \
                -storepass "$STOREPASS" -noprompt \
                -alias "$cert_alias" -file "$cert_file" || true
    fi
done

# Clean up
rm -rf "$CERT_DIR" "$CERT_BUNDLE"

echo "Certificate import completed successfully"