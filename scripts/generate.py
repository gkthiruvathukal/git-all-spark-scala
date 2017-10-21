import time
import math
import sys
import argparse

HEADER = """#/bin/bash
# This script is generated.
#
"""

QSUB = """
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


def get_argparse():
    parser = argparse.ArgumentParser()
    parser.add_argument('--max_hours', type=int,
                        help="time to run one node", default=10)
    parser.add_argument('--max_nodes', type=int,
                        help="maximum number of nodes to generate", default=120)
    parser.add_argument('--min_nodes', type=int,
                        help="miniimum number of nodes to generate", default=1)
    parser.add_argument('--cores', type=int, help="cores per node", default=12)
    parser.add_argument(
        '--fudge', type=int, help="fudge factor (for minimum cluster scheduling time)", default=15 * 60)
    parser.add_argument('--org', type=str, help="GitHub org")
    parser.add_argument('--repo', type=str, help="GitHub repo")
    parser.add_argument('--start', type=int, help="start commit", default=0)
    parser.add_argument('--stride', type=int,
                        help="stride (skip) commits", default=1)

    return parser


def generate():
    parser = get_argparse()
    args = parser.parse_args()

    min_nodes = args.min_nodes
    max_nodes = args.max_nodes
    cores = args.cores
    fudge = args.fudge
    org = args.org
    repo = args.repo
    src = repo
    dst = repo + "-commits"
    github = "/".join([org, repo])
    start = args.start
    stride = args.stride
    seconds = args.max_hours * 60 * 60

    print(HEADER)
    min_log = int(math.log(min_nodes, 2))
    max_log = int(math.log(max_nodes, 2))
    min_log = max(1, min_log)
    max_log = max(min_log+1, max_log)

    for i in range(min_log, max_log+1):
        nodes = 2 ** i
        qsub_time = time.strftime(
            '%H:%M:%S', time.gmtime(seconds / nodes + fudge))
        print(QSUB % vars())


if __name__ == '__main__':
    generate()
