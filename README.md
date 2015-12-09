# safetymaps-onboard

Onboard Java application for embedded servers on fire engines connected to a 
tablet/phone or for on (Java-capable) tablets themselves to provide services 
to the fullscreen safetymapsDBK viewer.

## Design principles

 - No application server installation required (such as Tomcat or NodeJS on Windows)
 - No database required (unfeasable on remote embedded installs)
 - Java client installation is feasable
 - Updatable by filesetsync-client when fire engine returns to base with WiFi

## Auto-update

The Main class monitors the current directory for changes to the file
`locationupdate.flag`. On changes, it exits with exit code 99.

The filesetsync-client should be configured to download a new fileset of the
safetymaps-onboard application (including Lucene search index) in the `update`
directory. When this is completely downloaded, the flag file should be updated.

The script `safetymaps-onboard.cmd` copies the `update` directory to `bin` and
and starts it. The old version is moved to the `bak` directory.
 
## Functionality

The fullscreen safetymapsDBK viewer is designed to work as a static web 
application as much as possible but that is not always feasable, therefore the
following web API functionality is included in this project.

Command line config:
 `-port \<port\>`: Port to start server on, default 1080

### Location search

Path: /q/\<term\>
Command line config: 
  `-search-db \<dir\>`: Directory containing the compressed Lucene index
  `-search-var \`<dir\>': Directory to store uncompressed index, default 'var'
Configuring safetymaps fullscreen:
```
if(!dbkjs.options.urls) {
  dbkjs.options.urls = {};
}
// If running fullscreen safetymaps on server, use default port. Otherwise use port 1080
// because the static webserver is running on port 80
dbkjs.options.urls.autocomplete = 'http://' + window.location.hostname + (window.location.hostname === 'localhost' ? ':1080' : '') + '/q/';
```
Implementing class: LuceneSearcher

Search for adresses/locations. Same API as controllers/bag.js, but the 
implementation is completely different because installing and keeping 
an entire PostGIS installation up-to-date with a BAG database is unworkable
in an embedded situation.

Uses Lucene for searching. Lucene index is created on the server using the
IndexBuilder class, which takes as input the result of the following query:

```
psql bag -c "select openbareruimtenaam || ' ' \
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
  where st_contains(st_transform(st_setsrid(st_geomfromtext('MULTIPOLYGON((( \ 
    4.09204132444052 51.9143105516418,\
    4.08411660696852 52.1873742191799,\
    4.59149658653479 52.1918851784121,\
    4.59633617480214 51.9187859633451,\
    4.09204132444052 51.9143105516418)))'),4326),28992),geopunt)" -Aztq > bag.txt
```
(Replace the MULTIPOLYGON coordinates with organisation area)

This input is parsed and put in a Lucene index and compressed as ZIP with 10MB
split parts to be transferred with filesetsync-client.

### Request forwarding

The fullscreen safetymaps viewers may not be connected to the server all the 
time. For the following functionality the request is saved by
safetymaps-onboard and forwarded to the server when connection to the server is
available (when fire engine is returned to base WiFi):

- support module emails

Path: `/forward/\<forward-path\>` (GET and POST supported)
Command line config: 
  `-forward-url \<url\>`: The URL to safetymaps server to forward the request to
  `-store-dir \<dir\>`: The directory to store requests to be forwarded
  `-save-forwarded`: Keep forwarded requests in "forwarded" subdirectory in store-dir 
Configuring safetymaps fullscreen, support module:
```
if(!dbkjs.options.urls) {
  dbkjs.options.urls = {};
}
// If running fullscreen safetymaps on server, use default port. Otherwise use port 1080
// because the static webserver is running on port 80
dbkjs.options.urls.annotation = 'http://' + window.location.hostname +  (window.location.hostname === 'localhost' ? ':1080' : '') + '/forward/mail';
// Add notification that messages are forwarded on return to base
$(dbkjs).one("dbkjs_init_complete", function() {
    $('#foutknop').on("click", function() {
        $("#email_help").append("<i> Meldingen worden bewaard en verstuurd bij terugkeer in kazerne.</i>");
    });
});
```
Implementing class: RequestStoreAndForward

Feedback is currently available in logging output and examining the store 
directory, users off safetymaps fullscreen do not currently get feedback of 
stored and succesfully forwarded requests.

Only some headers are forwarded:
 - `Content-Type`
 - `Content-Length`
 
Additional headers added:
 - `X-Original-Date`: Original Date header

