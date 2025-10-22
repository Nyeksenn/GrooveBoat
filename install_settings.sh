#!/usr/bin/bash

sudo cp ./src/main/resources/io.github.Nyeksenn.grooveboat.gschema.xml /usr/local/share/glib-2.0/schemas
sudo glib-compile-schemas /usr/local/share/glib-2.0/schemas/
