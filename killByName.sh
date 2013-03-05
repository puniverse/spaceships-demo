#!/bin/sh
ps -e | grep -vE 'awk|grep|kill' | awk '/'$1'/{print $1}'| xargs kill -9
