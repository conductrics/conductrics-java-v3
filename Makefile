
VERSION=$(shell cat VERSION)

all: Conductrics-${VERSION}.jar

com/conductrics/%.class: %.java org/json/JSONObject.class
	# Compiling source file $<...
	javac -classpath "." -d . $<

org/json/%: json-20190722.jar
	# Unpacking json jar
	jar xf $<
	touch $@

Conductrics-${VERSION}.jar: com/conductrics/Conductrics.class org/json/JSONObject.class
	# Packing jar file...
	jar cf $@ com/conductrics/Conductrics.class org/json/*.class

.test-artifact: com/conductrics/Test.class com/conductrics/Conductrics.class
	# Running tests...
	java -ea -classpath "Conductrics-${VERSION}.jar:." com.conductrics.Test
	touch $@

test: .test-artifact all
	# Tests are passing

maven/release/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar: Conductrics-${VERSION}.jar
	# Building Maven repo (release)...
	mkdir -p ./maven/release
	mvn deploy:deploy-file -Dfile=Conductrics-${VERSION}.jar -DgroupId=com.conductrics -DartifactId=Conductrics -Dversion=${VERSION} -Dpackaging=jar -Durl=file://`pwd`/maven/release
	touch $@

release: maven/release/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar test
	# Deploying Maven to S3 (release)...
	aws s3 sync ./maven s3://conductrics-maven-repo/

maven/snapshot/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar: Conductrics-${VERSION}.jar
	# Building Maven repo (snapshot)...
	mkdir -p ./maven/snapshot
	mvn deploy:deploy-file -Dfile=Conductrics-${VERSION}.jar -DgroupId=com.conductrics -DartifactId=Conductrics -Dversion=${VERSION} -Dpackaging=jar -Durl=file://`pwd`/maven/snapshot
	touch $@

snapshot: maven/snapshot/com/conductrics/Conductrics/${VERSION}/Conductrics-${VERSION}.jar test
	# Deploying Maven to S3 (snapshot)...
	aws s3 sync ./maven s3://conductrics-maven-repo/

clean:
	rm -rf org/json com/conductrics Conductrics.jar Conductrics-${VERSION}.jar META-INF/ ./maven

.PHONY: test clean publish
