# For this to work in your environment, you'll have to specify these variables
# on the command line to override.

SHELL=/bin/bash

all:
	sbt assembly

cobalt_clean:
	rm -f *.error *.cobaltlog *.output

sbt_clean:
	sbt clean

fs_clean:
	rm -f ~/logs/*
	find . -type d -name target -print | xargs rm -rf
	rm -rf qscripts.d/

clean:
	make cobalt_clean
	make sbt_clean
	make fs_clean
