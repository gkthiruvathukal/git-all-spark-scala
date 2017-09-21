#!/bin/bash
#
# Parameters (for debugging purposes):
#
# nodes = 1
# nparts = 1
# blocks = 12
# blockSize = 1024
# cores = 12
#
#
# Start Apache Spark Cluster
#

SPARK_HOME=$HOME/code/spark

for ASSEMBLY in $(find . -type f -name git-all-spark-scala-assembly*.jar)
do
   echo Running $SPARK_HOME/bin/spark-submit $ASSEMBLY $@
   $SPARK_HOME/bin/spark-submit $ASSEMBLY "$@"
done

#
# Done Submitting Application on Spark
#
