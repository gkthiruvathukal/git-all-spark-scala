# git-all-spark-scala

Synopsis
---------

GitAllSpark is an Apache Spark program that explodes all known versions of a git repository in a
clustered environment (shared nothing) for running one or more analyses. As an initial proof of concept,
we support a commonly-used line-counting tool (cloc), which allows us to understand lines of code
at a granular level: non-comment lines, comments, blank lines, etc.

Usage
------

```shell
$ $SPARK_HOME/bin/spark-submit $(find target -name  git-all-spark-scala-assembly*.jar | head -n1) --help
simplemap-spark-scala 0.1.x
Usage: scopt [options]

  -s, --src <value>    s/src is a String property
  -d, --dst <value>    d/dst is a String property
  -n, --nodes <value>  n/nodes is an int property
  -c, --cores <value>  c/cores is an int property (default to 12 for dual-hexcore on Cooley)
  -x, --xml <value>    xml <filename> is where to write XML reports
  --src-root <value>   srcRoot is a base directory for cloning stuff
  --dst-root <value>   dstRoot is a base directory for fetching hashes
  -u, --url <value>    u/url is a String property
  --cloc               
  --cloc-path <value>  u/url is a String property
  --git-clone          
  --start <value>      start is an int property
  --stride <value>     stride is an int property
  --help               prints this usage text
```


In a cluster environment (at least the one I use) there are typically local and remote (shared)
filesystems. On my cluster:

- /scratch is a *local* filesystem mounted on each cluster node.
- /projects is a *shared* filesystem that can be read/written on each cluster node (at a cost)

I was interested in being able to do analysis of git versions on all cluster nodes. The initial
clone is performed to the shared storage. Then we do a distributed fan out of the clone to all of
the nodes. This magic is achieved using git to set an upstream repository, followed by a fetch, 
followed by a checkout of the specific hash we wish to analyze.


Example
--------

So I wish to analyze a git repository, such as the source code for Scala's Build Tool (sbt). This
repoistory lives at https://github.com/sbt/sbt.


```shell
$SPARK_HOME/bin/spark-submit --url https://github.com/sbt/sbt.git --src sbt --dst sbt-commits --git-clone --start 0 --stride 100 --nodes 4 --cores 12 --cloc --cloc-path /home/thiruvat/local/bin/cloc --xml sbt-partial-results.xml

```

In real life, you are probably going to need to start Spark in clustered mode and use --master to specify the head node. None of that is covered here, but I have tested it fully--and it works!

So what is happening above?

- `--url` is for the git repo to be analyzed. This can be any git repo on any service.
- `--src` is where to write the data in the source root directory. This should match the repo name.
- `--dst  is where to write the commits. You can give this any name you like. On my cluster, everything gets deleted after my job runs, so it doesn't matter what I name it.
- `--src-root` is the directory where you have shared storage
- `--dst-root` is the directory where you have local storage
- `--git-lcone` is used to indicate that you want the driver to clone to the shared storage before running. This can be useful when you want to re-run your computation without the upfront overhead of the initial clone, especially for big repos.
- `--start` start with commit 0.
- `--stride` skip every 100 commits.
- `--nodes` number of nodes we're using. Here we have 4. This should match whatever you're using for your Spark clustered setup.
- `--cores` number of cores per node. Here we have 12. This means there are 48 partitions being set up for the computation (4 x 12).
- `--cloc` run the `cloc` program. It must be in your path. Or you use (as we did) `--cloc-path` to run it.
- `--cloc-path` where to find the binary. On my cluster, I had to do a local setup of this, hence the path.
- `--xml` write the performance data as an XML report. This is the only format supported.


