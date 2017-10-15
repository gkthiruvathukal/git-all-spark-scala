#
# Wrapper script to run job quickly by setting just a few 
# variables related to parallel execution

# Remove any previous clones from the web.

echo "Removing /projects/SE_HPC/"
rm -rf /projects/SE_HPC/*

NODES=4
CORES=12
START=0
STRIDE=30

qsub -n $NODES -t 00:45:00 -A SE_HPC -q pubnet  ./scripts/do-basic.sh \
	--url https://github.com/sbt/sbt.git \
	--src-root /projects/SE_HPC \
	--src sbt \
	--dst-root /projects/SE_HPC \
	--dst sbt-commits \
	--checkout --git-clone \
	--start $START --stride $STRIDE \
	--nodes $NODES --cores $CORES \
	--cloc --cloc-path /home/thiruvat/local/bin/cloc \
	--xml experiments/sbt-performance-n$NODES-c$CORES-$START-$STRIDE.xml \
	--cloc-report experiments/sbt-cloc-n$NODES-c$CORES-$START-$STRIDE.xml
