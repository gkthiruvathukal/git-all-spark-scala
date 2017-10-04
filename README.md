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


Performance Report
--------------------

The performance report allows you to look at how long it took to do the clone and to analyze all commits.
We also show the average time to fetch each commit and perform the analysis. The report can be helpful
to understand how long you'd need to do all commits, especially for a large repository like Linux.

```xml
<results>
  <experiment id="git-all-spark-scala"/>
  <config>
    <property key="src" value="sbt"/>
    <property key="dst" value="sbt-commits"/>
    <property key="cores" value="12"/>
    <property key="nodes" value="4"/>
    <property key="src-root" value="/projects"/>
    <property key="dst-root" value="/scratch"/>
    <property key="url" value="https://github.com/sbt/sbt.git"/>
    <property key="cloc" value="true"/>
    <property key="clocPath" value="/home/thiruvat/local/bin/cloc"/>
    <property key="start" value="0"/>
    <property key="stride" value="100"/>
    <property key="git-clone" value="true"/>
    <property key="xml" value="sbt-partial-results.xml"/>
  </config>
  <report>
    <time id="clone-time" t="4.53710968" unit="s"/>
    <time id="hash-fetch-plus-loc-time" t="203.767256509" unit="s"/>
    <time id="hash-fetch-plus-loc-time-per-commit" t="3.574864149280702" unit="s"/>
    <commits n="57"/>
  </report>
</results>

```

CLOC Report (first of many planned)
-------------------------------------

The following shows what you get when the `--cloc-path` is used. A beautiful XML report containing all information about how the computation
was performed is presented. (The format has not yet been finalized.)

As you can see, the details specific to running cloc, which does the actual work of counting KLOC, etc. are shown. The idea being that this
analysis could be paired with another analysis to compute various derived metrics. As you can see, the details about which programming languages
are used and how many lines of code, blank lines, and comments are shown by language. Again, this work is done by the `cloc` open source tool, so 
we simply are presenting this analysis for each commit.

```xml
<cloc_report>
  <info>
    <hostname>cc019.mycluster.com</hostname>
    <path>
      /scratch/SE_HPC/sbt-commits/a958fa6484dd6780fc20685a96cdf03b81b1ff1f
    </path>
    <cloc_time>5.701680002</cloc_time>
    <cloc>
      <header 
      url="http://cloc.sourceforge.net" cloc_version="1.64" elapsed_seconds="5.30345392227173" number_of_files="146" number_of_lines="10449" files_per_second="27.529229468154" lines_per_second="1970.22547063522">
</header>
      <languages>
        <language name="Scala" files="129" blank="710" comment="649" code="8864"/>
        <language name="Java" files="17" blank="31" comment="52" code="143"/>
      </languages>
    </cloc>
  </info>
  <info>
    <hostname>cc039.mycluster.com</hostname>
    <path>
      /scratch/SE_HPC/sbt-commits/9e6c14f200acbf98547e12ee188ed5e5d5e256bd
    </path>
    <cloc_time>53.443537377</cloc_time>
    <cloc>
      <header 
      url="http://cloc.sourceforge.net" cloc_version="1.64" elapsed_seconds="52.5243098735809" number_of_files="230" number_of_lines="15369" files_per_second="4.37892474082153" lines_per_second="292.607366702983">
</header>
      <languages>
        <language name="Scala" files="207" blank="1091" comment="1469" code="12650"/>
        <language name="Java" files="14" blank="11" comment="0" code="82"/>
        <language name="XML" files="8" blank="1" comment="0" code="60"/>
        <language name="JSP" files="1" blank="0" comment="0" code="5"/>
      </languages>
    </cloc>
  </info>
  <info>
    <hostname>cc039.mycluster.com</hostname>
    <path>
      /scratch/SE_HPC/sbt-commits/482e041464eb35884d659f6ad8b8b1c87cbc974c
    </path>
    <cloc_time>75.785050945</cloc_time>
    <cloc>
      <header 
      url="http://cloc.sourceforge.net" cloc_version="1.64" elapsed_seconds="74.8971869945526" number_of_files="525" number_of_lines="31584" files_per_second="7.00960905298331" lines_per_second="421.698080627476">
</header>
      <languages>
        <language name="Scala" files="477" blank="2975" comment="2181" code="25843"/>
        <language name="Java" files="43" blank="68" comment="77" code="400"/>
        <language name="XML" files="5" blank="1" comment="0" code="39"/>
      </languages>
    </cloc>
  </info>
  ...
</cloc_report>
```
