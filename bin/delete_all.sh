#!/bin/sh
curl http://localhost:9000/solr/lire/update -H "Content-Type: text/xml" --data-binary "<delete><query>*:*</query></delete>"
curl http://localhost:9000/solr/lire/update -H "Content-Type: text/xml" --data-binary "<commit/>"
