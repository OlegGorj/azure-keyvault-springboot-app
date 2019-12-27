#!bash

cd k8s

kubectl config set-context --current --namespace=aeo0-ccoe-rbc-api-server
#clientSecret=`az keyvault secret show --vault-name eengccoekvcac1 --name clusterServicePrincipalClientSecret --query value -o tsv`
#kubectl --namespace=aeo0-ccoe-rbc-api-server create secret generic sp-secre \
#   --from-literal=clientSecret=${clientSecret}

kubectl apply -f service-account.yaml
kubectl apply -f clusterrole.yaml
kubectl apply -f rolebinding.yaml
kubectl apply -f onboarding-api-deployment.yaml
kubectl apply -f onboarding-api-service.yaml
kubectl apply -f onboarding-api-ingress.yaml
