
VERSION=$(shell cat VERSION)

all: Conductrics-${VERSION}.jar

com/conductrics/%.class: %.java org/json/JSONObject.class
	javac -classpath "." -d . $<

org/json/%: json-20190722.jar
	jar xf $<
	touch $@

Conductrics-${VERSION}.jar: com/conductrics/Conductrics.class org/json/JSONObject.class
	jar cf $@ com/conductrics/Conductrics.class org/json/*.class

test: all com/conductrics/Test.class
	java -ea -classpath "Conductrics-${VERSION}.jar:." com.conductrics.Test

publish: test
	mvn install:install-file -Dfile=Conductrics-${VERSION}.jar -DgroupId=com.conductics -DartifactId=Conductrics -Dversion=${VERSION} -Dpackaging=jar -DlocalRepositoryPath=./maven
	aws s3 sync ./maven s3://conductrics-maven-repo/

clean:
	rm -rf org/json com/conductrics Conductrics.jar Conductrics-${VERSION}.jar META-INF/ ./maven

.PHONY: test clean publish
