@description('Región de Azure. Por defecto, la del grupo de recursos.')
param location string = resourceGroup().location

@description('Nombre del registro de contenedores. Debe ser único en Azure: solo minúsculas y números, 5 a 50 caracteres.')
@minLength(5)
@maxLength(50)
param acrName string

@description('Nombre del clúster AKS.')
param aksName string = 'vidacruz-aks'

@description('Tamaño de las máquinas del clúster. Verifica que tu suscripción lo permita en la región elegida.')
param nodeVmSize string = 'Standard_B2s'

@description('Cantidad de nodos.')
@minValue(1)
@maxValue(5)
param nodeCount int = 2

var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

resource aks 'Microsoft.ContainerService/managedClusters@2024-05-01' = {
  name: aksName
  location: location
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    dnsPrefix: aksName
    agentPoolProfiles: [
      {
        name: 'system'
        mode: 'System'
        count: nodeCount
        vmSize: nodeVmSize
        osType: 'Linux'
        osDiskSizeGB: 64
      }
    ]
    networkProfile: {
      networkPlugin: 'azure'
      networkPluginMode: 'overlay'
    }
    ingressProfile: {
      webAppRouting: {
        enabled: true
      }
    }
  }
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, aks.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: aks.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
  }
}

output acrLoginServer string = acr.properties.loginServer
output aksName string = aks.name
