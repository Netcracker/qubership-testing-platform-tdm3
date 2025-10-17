#!/usr/bin/env sh
echo "pre-stop works well"
xargs -rt -a /atp-tdm/application.pid kill -SIGTERM
sleep 29
