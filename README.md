# ihtsdo-refset-service
Service application to provide REST endpoints for the Refset Tool GUI and to integrate with Snowstorm

## Setup 


* Clone the project
Make sure your GIT client is setting the proper Unix line endings: you can run this command though it may not change TortiseGit or other GUI client settings 

```
git config --global core.autocrlf false
git clone https://github.com/IHTSDO/snomed-refset-service.git
```

* Install JDK 11
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
* Set the following environment variables with appropriate values for the system the application us being run on. For a local install make sure to use 127.0.0.1 as the DB_HOST instead of localhost:

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
set SNOWSTORM_AUTH_URL=changeme
set AWS_BUCKET=wci2
set AWS_REGION=us-east-1
set AWS_FOLDER_DIRECTORY=rt2
set AWS_ID=changeme
set AWS_SECRET_KEY=changeme
set REFSET_EXPORT_DIR=changeme
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
Download and run elasticsearch in a Docker container, pointing to the directory you created above:

```
docker run -d --name=es_evs --rm -p 9200:9200 -v %INDEX_BASE%:/usr/share/elasticsearch/data  -e "discovery.type=single-node" -e ES_JAVA_OPTS="-Xms1g -Xmx3g"  docker.elastic.co/elasticsearch/elasticsearch:7.1.0
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

## Usage
To get data into your local database for the first time go to this URL in a browser or Postman (as GET). This will only work if the database is empty, and will take several minutes. It will return a success message when complete:
* http://localhost:8080/admin/migration/rtt

To hit a REST endpoint go to the following URLs in a browser or in Postman (as GET):
* http://localhost:8080/test/info (You will see "Welcome")
* http://localhost:8080/refset/721144007 (You will see a refset returned)

This endpoint requires Postman (as Put, with a body of type "raw" with JSON. In the body enter "false" to change the concept's active flag.):
* http://localhost:8080/refset/721144007



