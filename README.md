# ihtsdo-refset-service
Service application to provide REST endpoints for the Refset Tool GUI and to integrate with Snowstorm

## Setup 

* Clone the project

```
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
* Install PostgreSQL Database or run through a docker container. Configure the database to use:
username: postgres
password: rootpwd
port: 5432

* Create a database named: rt2

```
createdb -Upostgres --encoding=UTF-8 rt2
```

* Create the following directory structure: C:\index\data\rt2


## Build, Test, Install, Release
Run these commands from a command prompt in a the root directory of the project
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
* http://localhost:8080/refset/001 (You will see a refset returned)

This endpoint requires Postman (as Put, with a body of type "raw" with JSON. In the body enter "false" to change the concept's active flag.):
* http://localhost:8080/refset/001



