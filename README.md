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

* Set the following environment variables with appropriate values for the system the application us being run on:
set PG_USER=changeme
set PG_PASSWORD=changeme
set PG_DATABASE=changeme
set PG_HOST=changeme
set PG_PORT=changeme
set INDEX_BASE=changeme #/directory for lucene indexes until we switch over to elasticsearch
set ELASTICSEARCH_USER=changeme
set ELASTICSEARCH_PASSWORD=changeme
set ELASTICSEARCH_HOST=changeme
set SNOWSTORM_USERNAME=changeme
set SNOWSTORM_PASSWORD=changeme
set SNOWSTORM_AUTH_URL=changeme
set SNOWSTORM_AUTH_HEADER=changeme

* Install PostgreSQL Database or run through a docker container. Configure the database to use the environment values you set above:
username: %PG_USER%
password: %PG_PASSWORD%
port: %PG_PORT%

Make sure to have the Postgres bin folder on your path in order to run the command below:

* Create a database named: %PG_DATABASE%

```
createdb -Upostgres --encoding=UTF-8 rt2
```

* Create the following directory structure for lucene indexes: %INDEX_BASE%


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
To hit a REST endpoint go to the following URLs in a browser or in Postman (as GET):
* http://localhost:8080/test/info (You will see "Welcome")
* http://localhost:8080/refset/721144007 (You will see a refset returned)

This endpoint requires Postman (as Put, with a body of type "raw" with JSON. In the body enter "false" to change the concept's active flag.):
* http://localhost:8080/refset/721144007



