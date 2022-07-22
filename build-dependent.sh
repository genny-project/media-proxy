parentdir="$(dirname `pwd`)"
for value in qwanda-utils bootxport genny-verticle-rules
do
    echo $value
    cd $parentdir/$value
    mvn clean install -DskipTests=true
done