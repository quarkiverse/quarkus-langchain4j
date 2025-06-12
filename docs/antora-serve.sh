#!/bin/bash
# Watch for modifications and rebuild the dev docs automatically.
# Depends on inotify-tools (apt install inotify-tools)

npx antora antora-playbook.yml
fswatch -o modules | (while read; do npx antora antora-playbook.yml; done)
