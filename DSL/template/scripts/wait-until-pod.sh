#!/bin/bash
POD_NAME=$1
EXPECT_POD_PHASE=$2
echo "wait for " $POD_NAME " to " $EXPECT_POD_PHASE
echo "(or Running or Succeeded)"
for i in {1..60}
do
POD_PHASE=$(oc get po "$POD_NAME" --template={{.status.phase}})
echo "Current POD Phase : " $POD_PHASE
if [ "$POD_PHASE" == "$EXPECT_POD_PHASE" ]; then
  break;
elif [ "$POD_PHASE" == "Running" ]; then
  break;
elif [ "$POD_PHASE" == "Succeeded" ]; then
  break;
else
sleep 1s;
echo "."
fi  
done
sleep 5s;
