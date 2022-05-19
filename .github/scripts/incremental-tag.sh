#! /bin/bash
set -euo pipefail

image=$1
tag=$2
original_tag=$tag
c=1

while :
do
  tag="${original_tag}-${c}"
  if DOCKER_CLI_EXPERIMENTAL=enabled docker manifest inspect $image:$tag >/dev/null 2>/dev/null; then
      c=$((c+1))
  else
      echo "-${c}"
      break
  fi
done
