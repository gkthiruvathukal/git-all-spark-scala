rm -rf /projects/SE_HPC/*

NODES=4
CORES=12
START=0
STRIDE=25

qsub -n $NODES -t 01:00:00 -A SE_HPC -q pubnet  ./scripts/do-basic.sh --url https://github.com/sbt/sbt.git --src-root /projects/SE_HPC --src sbt --dst-root /projects/SE_HPC --dst sbt-commits --git-clone --start $START --stride $STRIDE --nodes $NODES --cores $CORES --cloc --cloc-path /home/thiruvat/local/bin/cloc --xml sbt-performance-n$NODES-c$CORES-$START-$STRIDE.xml --cloc-report sbt-cloc-n$NODES-c$CORES-$START-$STRIDE.xml
