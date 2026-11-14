# Despliegue en Azure

Requiere Azure CLI (`az`) y `kubectl`. El nombre del registro (`ACR`) debe coincidir con el que usa el overlay en
`deploy/k8s/overlays/azure/kustomization.yaml` (`vidacruzjdg2026` por defecto).

```bash
RG=vidacruz-rg
ACR=vidacruzjdg2026

az login
az group create -n $RG -l eastus
az deployment group create -g $RG -f deploy/azure/main.bicep -p acrName=$ACR
az aks get-credentials -g $RG -n vidacruz-aks
```

## Imágenes

`az acr build` compila en Azure con el Dockerfile del repo, así que no necesita Docker ni Podman locales:

```bash
for s in triage-service assistant-service alert-service; do
  az acr build -r $ACR -f deploy/docker/Dockerfile --build-arg SERVICE=$s -t $s:latest .
done
```

Algunas suscripciones de prueba no permiten ACR Tasks y devuelven un error de operación no permitida. En ese caso,
construye con Docker y sube con `az acr login`, que es lo que hace el workflow `deploy.yml`:

```bash
az acr login --name $ACR
for s in triage-service assistant-service alert-service; do
  docker build -f deploy/docker/Dockerfile --build-arg SERVICE=$s -t $ACR.azurecr.io/$s:latest .
  docker push $ACR.azurecr.io/$s:latest
done
```

## Secretos

El `Secret` no está en el repo. Se crea antes de aplicar los manifests, con los valores en variables de entorno:

```bash
kubectl create namespace vidacruz
kubectl -n vidacruz create secret generic vidacruz-secrets \
  --from-literal=API_KEY="$API_KEY" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=LLM_API_KEY="$LLM_API_KEY" \
  --from-literal=TEAMS_WEBHOOK_URL="$TEAMS_WEBHOOK_URL" \
  --from-literal=DISCORD_WEBHOOK_URL="$DISCORD_WEBHOOK_URL"
```

## Manifests

```bash
kubectl apply -k deploy/k8s/overlays/azure
kubectl -n vidacruz get pods
kubectl -n vidacruz get ingress
```

La IP pública sale en la columna `ADDRESS` del Ingress. Las APIs responden en `/triage` y `/asistente`, y exigen el
header `X-API-Key`.
