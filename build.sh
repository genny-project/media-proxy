echo "Building the media-proxy"

mvn clean package
mvn eclipse:eclipse
