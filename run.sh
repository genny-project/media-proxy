export MEDIA_PROXY_SERVER_PORT="9080"
export MINIO_SERVER_URL="http://localhost:9000"
export MINIO_ACCESS_KEY="minioadmin"
export MINIO_SECRET_KEY="minioadmin"
export BUCKET_NAME="internmatch"
java -jar target/*-fat.jar
