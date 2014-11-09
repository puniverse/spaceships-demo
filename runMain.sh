#!/bin/sh
if [ "$#" == "0" ]; then
	echo "Usage: $0 className arg0 args1 ... argN"
	exit 1
fi
main=$1
shift
if [ "$#" == "0" ]; then
	./gradlew -q run -PmainClass=$main
else
	./gradlew -q run -PmainClass=$main -Pargs="$*"
fi
