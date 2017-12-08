#!/bin/bash
#
# Example of a workflow script to schedule and run Apache Spark + the user-specific Spark application
# Only works on Cooley, most likely.
#

# Point to the shared build of Spark. In this case, I have my own build, but it's read-only.

if [ -z "${SPARK_HOME}" ]; then
   export SPARK_HOME=$HOME/code/spark
fi

# The slaves file must be specific to this job ${COBALT_JOBID}

export SPARK_SLAVES=${HOME}/spark-state/slaves.${COBALT_JOBID}
cp $COBALT_NODEFILE $SPARK_SLAVES

# These are log files specific to my application

JOB_LOG=$HOME/logs/$COBALT_JOBID.txt
JOB_JSON=$HOME/logs/$COBALT_JOBID.json
JOB_XML=$HOME/logs/$COBALT_JOBID.xml

# Check /scratch on each node

echo "Report of /scratch usage on nodes" >> $JOB_LOG
for host in $(cat $COBALT_NODEFILE)
do
   ssh $host "hostname; df -h /scratch" >> $JOB_LOG
   ssh $host "rm -rf /scratch/SE_HPC/*" >> $JOB_LOG
done


# Start Spark on the allocated nodes
# Note: We probably can do $SPARK_HOME/sbin/start-all.sh here

pushd $SPARK_HOME
#echo "Spark Slaves File ${SPARK_SLAVES}" >> $JOB_LOG
#cat $SPARK_SLAVES >> $JOB_LOG
./sbin/start-all.sh
popd

NODES=`wc -l ${SPARK_SLAVES} | cut -d" " -f1`
MASTER=`hostname`

echo "# Spark is now running with $NODES workers:" >> $JOB_LOG
echo "#"
echo "export SPARK_STATUS_URL=http://$MASTER.cooley.pub.alcf.anl.gov:8000" >> $JOB_LOG
echo "export SPARK_MASTER_URI=spark://$MASTER:7077" >> $JOB_LOG

SPARK_MASTER_URI=spark://$MASTER:7077

if [ -f spark-defaults.conf ]; then
   ADD_SPARK_DEFAULTS_CONF="--properties-file $(pwd)/spark-defaults.conf"
fi

# Run my Apache Spark application code

for ASSEMBLY in $(find . -type f -name *.jar)
do
   echo "Running: "$SPARK_HOME/bin/spark-submit $ADD_SPARK_DEFAULTS_CONF --master $SPARK_MASTER_URI $ASSEMBLY "$@" >> $JOB_LOG
   $SPARK_HOME/bin/spark-submit $ADD_SPARK_DEFAULTS_CONF --master $SPARK_MASTER_URI $ASSEMBLY "$@" >> $JOB_LOG
done


