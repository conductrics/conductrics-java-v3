
VERSION=$(shell cat VERSION)
JAVAC:=javac
JAVA_FILES=$(wildcard src/*.java)
CLASS_FILES=$(subst src/,com/conductrics/,$(subst .java,.class,${JAVA_FILES}))

all: Conductrics-${VERSION}.jar

com/conductrics/%.class: src/%.java org/json/JSONObject.class
	# Compiling source file $<...
	${JAVAC} -classpath "." -d . src/*.java

test/%.class: test/%.java org/json/JSONObject.class
	# Compiling source file $<...
	${JAVAC} -classpath "." -d . $<

org/json/%: json-20190722.jar
	# Unpacking json jar
	jar xf $<
	touch $@

Conductrics-${VERSION}.jar: ${CLASS_FILES} org/json/JSONObject.class
	# Packing jar file...
	jar cf $@ com/conductrics/*.class org/json/*.class

.test-artifact: test/Test.class com/conductrics/Conductrics.class
	# Running tests...
	java -ea -classpath "Conductrics-${VERSION}.jar:." test.Test
	touch $@

test: .test-artifact all ${CLASS_FILES}
	# All tests are passing

maven-sync:
	# Syncing contents of current Maven repo...
	aws s3 sync s3://conductrics-maven-repo/ ./maven

maven/release/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar: maven-sync Conductrics-${VERSION}.jar
	# Building Maven repo (release)...
	mkdir -p ./maven/release
	mvn deploy:deploy-file -Dfile=Conductrics-${VERSION}.jar -DgroupId=com.conductrics -DartifactId=Conductrics -Dversion=${VERSION} -Dpackaging=jar -Durl=file://`pwd`/maven/release
	touch $@

release: maven/release/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar test
	# Deploying Maven to S3 (release)...
	aws s3 sync ./maven s3://conductrics-maven-repo/

maven/snapshot/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar: maven-sync Conductrics-${VERSION}.jar
	# Building Maven repo (snapshot)...
	mkdir -p ./maven/snapshot
	mvn deploy:deploy-file -Dfile=Conductrics-${VERSION}.jar -DgroupId=com.conductrics -DartifactId=Conductrics -Dversion=${VERSION} -Dpackaging=jar -Durl=file://`pwd`/maven/snapshot
	touch $@

snapshot: maven/snapshot/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar test
	# Deploying Maven to S3 (snapshot)...
	aws s3 sync ./maven s3://conductrics-maven-repo/

clean:
	rm -rf test/*.class org/json com/conductrics Conductrics.jar Conductrics-*.jar META-INF/ ./maven

.PHONY: test clean publish maven-sync
