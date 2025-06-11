#!/bin/bash
npx antora antora-playbook.yml
fswatch -o modules | (while read; do npx antora antora-playbook.yml; done)
