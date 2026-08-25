#!/bin/bash

sed -i 's/authorizationEnabled: false/authorizationEnabled: true/' /milvus/configs/milvus.yaml
sed -i 's/useLoonFFI: false/useLoonFFI: true/' /milvus/configs/milvus.yaml
exec milvus run standalone