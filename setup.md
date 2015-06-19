# NoMaD Lab Base Layer Installation


# scala

    #wget http://downloads.typesafe.com/scala/2.11.5/scala-2.11.5.deb?_ga=1.172612385.307956976.1430825833
    wget http://downloads.typesafe.com/scala/2.11.6/scala-2.11.6.deb
    sudo dpkg -i scala-2.11.6.deb

# sbt installation

    if [ ! -e "/etc/apt/sources.list.d/sbt.list" ] ; then
       echo "deb http://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
    fi
    sudo aptitude update
    sudo aptitude install sbt
