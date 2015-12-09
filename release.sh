#!/bin/bash
mvn clean install
mvn dependency:copy-dependencies
rm -rf bin 2>/dev/null
mkdir bin
cp target/safetymaps-onboard-*.jar bin
cp -r target/dependency bin
cp safetymaps-onboard.cmd bin

source ./bag_settings.sh
echo Querying bag database \(host=$PGHOST,port=$PGPORT,user=$PGUSER,db=$PGDATABASE\)
echo Organisation area: $AREA
psql -h $PGHOST -p $PGPORT -U $PGUSER $PGDATABASE -c "select openbareruimtenaam || ' ' \
  || COALESCE(CAST(huisnummer as varchar) || ' ','') \
  || COALESCE(CAST(huisletter as varchar) || ' ','') \
  || COALESCE(CAST(huisnummertoevoeging as varchar) || ' ','') \
  || COALESCE(CAST(postcode as varchar) || ' ','') || \
  CASE WHEN lower(woonplaatsnaam) = lower(gemeentenaam) \
    THEN woonplaatsnaam \
    ELSE woonplaatsnaam || ', ' || gemeentenaam \
  END as display_name, \
  st_x(st_transform(geopunt,4326)) as x, st_y(st_transform(geopunt,4326)) as y \
  from bag_actueel.adres \
  where st_contains(st_transform(st_setsrid(st_geomfromtext('$AREA'),4326),28992),geopunt)" -Aztq > bag.txt
echo Creating Lucene index...
rm -rf db 2>/dev/null
java -cp bin/safetymaps-onboard-*.jar nl.opengeogroep.safetymaps.onboard.IndexBuilder bag.txt
cp -r db bin

