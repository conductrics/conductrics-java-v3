
all: com/conductrics/http/Conductrics.class com/conductrics/http/ConductricsTest.class

com/conductrics/http/%.class: %.java
	javac -classpath "json-20190722.jar:." -d . $<

test: all
	java -classpath "json-20190722.jar:." com.conductrics.http.ConductricsTest
