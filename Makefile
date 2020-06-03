
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
	java -classpath "Conductrics-${VERSION}.jar:." com.conductrics.Test

clean:
	rm -rf org/json com/conductrics Conductrics.jar Conductrics-${VERSION}.jar META-INF/

.PHONY: test clean
