# ihtsdo-refset-service
Service application to provide REST endpoints for the Refset Tool GUI and to integrate with Snowstorm

## Setup 


* Clone the project
Make sure your GIT client is setting the proper Unix line endings: you can run this command though it may not change TortiseGit or other GUI client settings 

```
git config --global core.autocrlf false
git clone https://github.com/IHTSDO/snomed-refset-service.git
```

* Install JDK 17
* Install Gradle
* Set up a ~/.gradle/gradle.properties file

```
mkdir ~/.gradle
cat > ~/.gradle/gradle.properties << EOF
nexusUsername=...nexus username...
nexusPassword=...nexus password...
EOF
```

* Create a local directory for export files: %REFSET_EXPORT_DIR%
* Set the following environment variables with appropriate values for the system the application us being run on. 
* For a local install make sure to use 127.0.0.1 as the DB_HOST instead of localhost
* SnowStorm configuration is set with the SNOWSTORM variables below. The username provided must have full read and write permissions to all code systems that will be accessed as well as full branching permissions.
* Crowd configuration is set with the CROWD variables below. The Crowd username provided must have permissions to add permission groups, and full ability to edit group memberships

```
set DB_USER=changeme
set DB_PASSWORD=changeme
set DB_DATABASE=changeme
set DB_HOST=changeme
set DB_PORT=changeme
set ELASTICSEARCH_USER=changeme
set ELASTICSEARCH_PASSWORD=changeme
set ELASTICSEARCH_PROTOCOL=http
set ELASTICSEARCH_HOST=localhost:9200
set ELASTICSEARCH_INDEX_PREFIX=local_
set SNOWSTORM_USERNAME=changeme
set SNOWSTORM_PASSWORD=changeme
set SNOWSTORM_AUTH_URL=https://ims.ihtsdotools.org/api/
set SNOWSTORM_BASE_URL=https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/
set AWS_BUCKET=wci2
set AWS_REGION=us-east-1
set AWS_ID=changeme
set AWS_SECRET_KEY=changeme
set AWS_PROJECT_BASE_DIR=rt2
set AWS_ICON_DIR=icon
set AWS_ARTIFCAT_DIR=artifact
set REFSET_EXPORT_DIR=changeme
set ICON_SERVER_DIR=C:/temp/refset_icon
set ARTIFACT_SERVER_DIR=C:/temp/refset_artifact
set CROWD_URL=https://crowd.ihtsdotools.org/crowd
set CROWD_USERNAME=changeme
set CROWD_PASSWORD=changeme
```

* Install MySql Database v? or run through a docker container. Configure the database to use the environment values you set above:
username: %DB_USER%
password: %DB_PASSWORD%
port: %DB_PORT%

Make sure to have the MySql bin folder on your path in order to run the command below (Should be automatic if you install MySql locally):

* Create a database named: %DB_DATABASE%

```
mysql -u %DB_USER% -p -h %DB_Host%
DROP DATABASE IF EXISTS %DB_DATABASE%;
CREATE DATABASE %DB_DATABASE%;
```

* Create the following directory structure for local elasticsearch indexes: %INDEX_BASE%

Download and install the latest version of Docker.
Option A: Use the Compose stack (recommended for dev). From `.work/mt2-dev`, after setting INDEX_BASE and ELASTICSEARCH_PORT in `.env`, run: `docker compose up -d`. This starts Elasticsearch and NGINX.
Option B: Run Elasticsearch in a container, pointing to the directory you created above:

```
docker run -d --name=es_rt2 --rm -p 9200:9200 -v %INDEX_BASE%:/usr/share/elasticsearch/data -e "discovery.type=single-node" -e "ES_JAVA_OPTS=-Xms1g -Xmx3g" -e "xpack.security.enabled=false" docker.elastic.co/elasticsearch/elasticsearch:8.18.8
```

##  Application Authentication
* This application uses single sign on to authenticate with IMS. The authentication server is set by the Crowd environment variables listed above. Users must have accounts in IMS.
* For a local install you will need to install NGINX proxy server and add the following configuration to to the nginx.conf file under the /conf directory, changing the IMS URL and
port numbers to match you local setup. The standard URL for local server is http://local.ihtsdotools.org:8888

```
http {
    include    mime.types;
	proxy_read_timeout 300;
    proxy_connect_timeout 300;
    proxy_send_timeout 300;
	client_max_body_size 50M;
    server {
		listen		8888;
        server_name localhost;

        ssl_session_timeout  5m;

		# to angular app
        location / {
            proxy_pass http://localhost:4200;
        }
		location /refsetservice {
            proxy_pass http://127.0.0.1:8080/refsetservice;
        }
		location /ims-api {
            proxy_pass https://dev-ims.ihtsdotools.org/api;
            proxy_pass_request_body off;
            proxy_set_header Content-Length "";
            proxy_set_header Accept "application/json";
            proxy_method GET;
        }
    }
}
```

## Build, Test, Install, Release
Run these commands from a command prompt in a the root directory of the project
Be sure to have cygwin, (or install other linux tools like make), and have those tools on your path to run the commands below 

Run this to build the project without running tests (Do not run this command at the moment)

```sh
  $ make build
```

Run this to build the project with tests

```sh
  $ make test
```

Run this to build the project and install into local maven repository. (typically not useful or needed for service applications)

```sh
  $ make install
```

Run this to build the project and install into remote nexus maven repository. NOTE: this requires having configured gradle.properties as described above.

```sh
  $ make release
```

Run this to run the web server 

```sh
  $ make release
  $ java -jar build/libs/snomed-refset-service*jar
```

## Data
To get data into your local database for the first time go to this run Sync in a browser or Postman (as GET). You must be logged in as an application level administrator. This will take several minutes and return a success message when complete.
This procedure will load code systems and reference sets from the configured snowstorm into you database. If you run it on a populated database it will update the database with any changed information in snowstorm.
* http://localhost:8080/refsetservice/admin/sync/snowstorm

## Snowstorm Branching
* All refserence sets in snowstorm are created or edited in a branch under the code system called named in the pattern "REFSETS-{code system short code}". A sub branch is created for each reference set that is 
edited or created, with each edit occuring in individual sub branches. An example branch of a reference set being edited in the Belgium would be:

```
MAIN/SNOMEDCT-BE/REFSETS-BE/REFSET-370021000146101-1675199085099/EDIT-1675199117948
```

* Concepts underlying new reference sets are created as a direct or indirect child of SIMPLE_TYPE_REFERENCE_SET (concept ID: 446609009)

## Usage
Swagger (you must be logged in as a user of the application):
* https://rt2.ihtsdotools.org/refsetservice/swagger-ui/index.html

To hit a REST endpoint go to the following URLs in a browser or in Postman (as GET):
* http://local.ihtsdotools.org:8888/refsetservice/test/info (You will see "Welcome")
* http://local.ihtsdotools.org:8888/refsetservice/refset/721144007 (You will see a refset returned)

This endpoint requires Postman (as Put, with a body of type "raw" with JSON. In the body enter "false" to change the concept's active flag.):
* http://local.ihtsdotools.org:8888/refsetservice/refset/721144007

## Application GUI
The front end GUI of the application is its own GitHub project. There are additional configuration steps outlined there for using the GUI: 
* https://github.com/IHTSDO/snomed-refset-ui/tree/main