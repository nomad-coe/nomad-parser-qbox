#!/bin/bash
# sets up docker on ubuntu 14.04

if ! which docker >& /dev/null ; then
    echo "docker missing, setting it up"
    sudo apt-key adv --keyserver hkp://pgp.mit.edu:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D
    if [[ -e "/etc/timeshift.json" ]] ; then
        echo "ensure that timeshift skips /var/lib/docker"
        sudo python <<EOF
import json
config = json.load(open("/etc/timeshift.json"))
exc = config.get(u"exclude",[])
if not u'/var/lib/docker/' in exc:
    config["exclude"] = exc.append(u'/var/lib/docker/')
    with open("/etc/timeshift.json","w") as f:
        json.dump(config, indent = 4)
EOF
    fi
    if ! [[ -e "/etc/apt/sources.list.d/docker.list" ]] ; then
        sudo cat > /etc/apt/sources.list.d/docker.list <<APTLIST

## Ubuntu Precise
#deb https://apt.dockerproject.org/repo ubuntu-precise main
# Ubuntu Trusty
deb https://apt.dockerproject.org/repo ubuntu-trusty main
## Ubuntu Vivid
#deb https://apt.dockerproject.org/repo ubuntu-vivid main
## Ubuntu Wily
#deb https://apt.dockerproject.org/repo ubuntu-wily main
APTLIST
    fi
    sudo apt-get update
    sudo apt-get purge lxc-docker*
    apt-cache policy docker-engine
    sudo apt-get install docker-engine
fi
