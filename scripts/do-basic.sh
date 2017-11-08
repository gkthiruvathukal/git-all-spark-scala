#!/bin/bash
#
# do-basic.sh: Not a great name but this shows how to start Spark within a Cobalt job and
#   run the actual application (.jar file) on the nodes.
#
#   You can pass any arguments to the Spark job. These are passed in their entirety.

# Start (Cobalt Job-Specific) Apache Spark Cluster
#

JOB_LOG=$HOME/logs/$COBALT_JOBID.txt
JOB_JSON=$HOME/logs/$COBALT_JOBID.json
JOB_XML=$HOME/logs/$COBALT_JOBID.xml

if [ -z "${SPARK_HOME}" ]; then
   export SPARK_HOME=$HOME/code/spark
fi

if [ -z "${SPARK_CONF_DIR}" ]; then
   export SPARK_CONF_DIR=${SPARK_HOME}/conf
fi

export SPARK_SLAVES=${SPARK_CONF_DIR}/slaves.${COBALT_JOBID}

cp $COBALT_NODEFILE $SPARK_SLAVES

pushd $SPARK_HOME
echo "Spark Slaves File ${SPARK_SLAVES}" >> $JOB_LOG
cat $SPARK_SLAVES >> $JOB_LOG
./sbin/start-all.sh
NODES=`wc -l ${SPARK_SLAVES} | cut -d" " -f1`
popd

MASTER=`hostname`

echo "# Spark is now running with $NODES workers:" >> $JOB_LOG
echo "#"
echo "export SPARK_STATUS_URL=http://$MASTER.cooley.pub.alcf.anl.gov:8000" >> $JOB_LOG
echo "export SPARK_MASTER_URI=spark://$MASTER:7077" >> $JOB_LOG

SPARK_MASTER_URI=spark://$MASTER:7077

#
# Done Initializing Apache Spark
#
#
# Submit Application on Spark
#

for ASSEMBLY in $(find . -type f -name git-all-spark-scala-assembly*.jar)
do
   echo "Running: "$SPARK_HOME/bin/spark-submit --master $SPARK_MASTER_URI $ASSEMBLY "$@" >> $JOB_LOG

   $SPARK_HOME/bin/spark-submit --master $SPARK_MASTER_URI $ASSEMBLY "$@" >> $JOB_LOG

done

#
# Done Submitting Application on Spark
#
