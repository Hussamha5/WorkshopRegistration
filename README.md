# Workshop Registration – EX2 Maven Evolution

## One-time setup: install the API jars into the local Maven repository

The API jars and the tester are not on Maven Central, so they must be installed
into the local repository (`~/.m2`) once on every computer.
The jars are in the `lib` folder. Run these commands from the root folder of the project.

- `-N` = run only on the root pom, not on every module.
- `-DgeneratePom=true` is needed because the jars contain an embedded pom whose parent
  (`workshops-aggregator`) is not available, so Maven must generate a simple pom instead.

```
mvn -N install:install-file -Dfile=lib/model-2.0.jar -DgroupId=patmal.workshops -DartifactId=model -Dversion=2.0 -Dpackaging=jar -DgeneratePom=true
mvn -N install:install-file -Dfile=lib/logic-2.0.jar -DgroupId=patmal.workshops -DartifactId=logic -Dversion=2.0 -Dpackaging=jar -DgeneratePom=true
mvn -N install:install-file -Dfile=lib/workshop-tester-2.0.jar -DgroupId=patmal.workshops -DartifactId=workshop-tester -Dversion=2.0 -Dpackaging=jar -DgeneratePom=true
```

## Build

```
mvn clean install
```

The runnable uber jar is created at `app/target/workshop-cli.jar`.

The build also runs the tester automatically in the `verify` phase (bonus 8.2,
`exec-maven-plugin` in `app/pom.xml`). If the tester fails, the build fails.

## Run

The demo students are imported from a CSV file (bonus 8.1).

With the `students.csv` that is packed inside the jar (`app/src/main/resources/students.csv`):
```
java -jar app/target/workshop-cli.jar
```
With your own CSV file (header line `id,name,email`):
```
java -jar app/target/workshop-cli.jar my-students.csv
```
The application writes `instructors.json`, `students.json` and `teams.json` to the current folder.

## Run the tester

Windows:
```
java -cp "lib/workshop-tester-2.0.jar;app/target/workshop-cli.jar" patmal.workshops.main.Main
```
Linux / macOS:
```
java -cp "lib/workshop-tester-2.0.jar:app/target/workshop-cli.jar" patmal.workshops.main.Main
```