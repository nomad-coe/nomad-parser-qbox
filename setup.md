# NOMAD Lab Base Layer Installation

# submodules

Get the git submodules in this repo:

    git submodule update --init

# scala

Scala by itself is not strictly required, but it ensures that all dependencies are there (mainly java).

    #wget http://downloads.typesafe.com/scala/2.11.5/scala-2.11.5.deb?_ga=1.172612385.307956976.1430825833
    wget http://downloads.typesafe.com/scala/2.11.6/scala-2.11.6.deb
    sudo dpkg -i scala-2.11.6.deb

# sbt installation

    if [ ! -e "/etc/apt/sources.list.d/sbt.list" ] ; then
       echo "deb http://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
       sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
    fi
    sudo aptitude update
    sudo aptitude install sbt

# hdf5 / netCDF installation

read & execute (line by line if you have problems/worry about security)

    hdf5/getAndBuild.sh

# compilation

IMPORTANT, before the first compilation you need

    sbt jooq:codegen

as this compiles the classes for the db connection (jooq), it will automatically execute flaywayMigrate to bring the DB schema up to date

then just use sbt normally:

    $ sbt
    > compile
    > test
    > testOnly *MyTests*
    * tool/run
    > re-start
    > re-stop
    ...

this interactive use keeps things cached and gives faster compilation/test, console will start an interactive scala environment where you can import and play with all the infrastructure (i.e. scala REPL + all dependencies and compiled code).
