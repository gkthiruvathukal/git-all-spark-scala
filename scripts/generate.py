import time
import math
import sys

HEADER="""#/bin/bash
# This script is generated.
#
"""

QSUB="""
if [ "$DEPS" == "" ]; then
   DEPENDENCIES=""
else
   DEPENDENCIES="--dependencies $DEPS"
fi


DEPS=`qsub $DEPENDENCIES -n %(nodes)s -t %(qsub_time)s -A SE_HPC -q pubnet \\
	./scripts/do-basic.sh \\
	--github %(github)s --git-clone --checkout \\
	--src-root /projects/SE_HPC --dst-root /scratch/SE_HPC --src %(src)s --dst %(dst)s --start %(start)s --stride %(stride)s \\
	--nodes %(nodes)s --cores %(cores)s \\
	--cloc --cloc-path /home/thiruvat/local/bin/cloc \\
	--xml experiments/%(repo)s-performance-n%(nodes)s-c%(cores)s-%(start)s-%(stride)s.xml \\
	--cloc-report experiments/%(repo)s-cloc-n%(nodes)s-c%(cores)s-%(start)s-%(stride)s.xml`
"""

max_hours = 8  # for one node (which we're not generating at this moment)
max_nodes = 120
cores = 12
fudge = 15 * 60 # in minutes
org = "sbt"
repo = "sbt" 
src = repo
dst = repo + "-commits"
github = "/".join([org,repo])
start = 0
stride = 1
seconds = max_hours * 60 * 60  # hours
print(HEADER)
for i in range(1, 1+int(math.log(max_nodes, 2))):
   nodes = 2 ** i
   qsub_time = time.strftime('%H:%M:%S', time.gmtime(seconds / nodes + fudge))
   print(QSUB % vars())

