#!/bin/bash

JOB_LOG=$HOME/logs/$COBALT_JOBID.txt
JOB_JSON=$HOME/logs/$COBALT_JOBID.json
JOB_XML=$HOME/logs/$COBALT_JOBID.xml

pushd $HOME/code/spark
cat $COBALT_NODEFILE > conf/slaves
cat $COBALT_NODEFILE >> $JOB_LOG
./sbin/start-all.sh
NODES=`wc -l conf/slaves | cut -d" " -f1`
popd

MASTER=`hostname`

echo "# Spark is now running with $NODES workers:" >> $JOB_LOG
echo "#"
echo "export SPARK_STATUS_URL=http://$MASTER.cooley.pub.alcf.anl.gov:8000" >> $JOB_LOG
echo "export SPARK_MASTER_URI=spark://$MASTER:7077" >> $JOB_LOG

SPARK_MASTER_URI=spark://$MASTER:7077
SPARK_HOME=$HOME/code/spark

#
# Done Initializing Apache Spark
#
#
# Submit Application on Spark
#

for ASSEMBLY in $(find . -type f -name git-all-spark-scala-assembly*.jar)
do
   echo "Running: "$SPARK_HOME/bin/spark-submit \
      --master $SPARK_MASTER_URI $ASSEMBLY \
      --nodes $NODES --cores 12 \
      --json $JOB_JSON >> $JOB_LOG \
      --url https://github.com/gkthiruvathukal/metrics-dashboard-bash-scala.git \
      --src-root /projects/SE_HPC --dst-root /scratch/SE_HPC \
      --src metrics-dashboard-bash-scala --dst metrics-dashboard-commits

   $SPARK_HOME/bin/spark-submit \
      --master $SPARK_MASTER_URI $ASSEMBLY \
      --nodes $NODES --cores 12 \
      --json $JOB_JSON >> $JOB_LOG \
      --url https://github.com/gkthiruvathukal/metrics-dashboard-bash-scala.git \
      --src-root /projects/SE_HPC --dst-root /scratch/SE_HPC \
      --src metrics-dashboard-bash-scala --dst metrics-dashboard-commits
done

