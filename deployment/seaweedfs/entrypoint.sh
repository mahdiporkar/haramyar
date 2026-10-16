#!/bin/sh
# S3-compatible private object storage for martyr media (MinIO community images are no longer published).
# Credentials come only from the environment.
set -eu
mkdir -p /etc/seaweedfs
cat > /etc/seaweedfs/s3.json <<JSON
{"identities":[{"name":"martyrs","credentials":[{"accessKey":"${S3_ACCESS_KEY}","secretKey":"${S3_SECRET_KEY}"}],"actions":["Admin","Read","Write","List","Tagging"]}]}
JSON
exec weed server -filer -s3 -s3.config=/etc/seaweedfs/s3.json -dir=/data -master.volumeSizeLimitMB=256 -volume.max=8
