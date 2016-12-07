package org.apache.streamline.streams.catalog.container;

import org.apache.streamline.streams.catalog.Cluster;
import org.apache.streamline.streams.catalog.Component;
import org.apache.streamline.streams.catalog.Namespace;
import org.apache.streamline.streams.catalog.NamespaceServiceClusterMapping;
import org.apache.streamline.streams.catalog.Service;
import org.apache.streamline.streams.catalog.service.StreamCatalogService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * This class is a base container implementation which element (instance) should be created per Namespace.
 * This only provides container functionality, and implementations should provide the way to initialize element (instance).
 */
public abstract class NamespaceAwareContainer<T> {
    private Map<Long, T> namespaceToInstance;
    protected final StreamCatalogService catalogService;

    public NamespaceAwareContainer(StreamCatalogService catalogService) {
        this.catalogService = catalogService;
        namespaceToInstance = new HashMap<>();
    }

    public T findInstance(Namespace namespace) {
        Long namespaceId = namespace.getId();
        if (namespaceToInstance.containsKey(namespaceId)) {
            return namespaceToInstance.get(namespaceId);
        }

        T newInstance = initializeInstance(namespace);
        namespaceToInstance.put(namespaceId, newInstance);
        return newInstance;
    }

    public void invalidateInstance(Long namespaceId) {
        namespaceToInstance.remove(namespaceId);
    }

    protected abstract T initializeInstance(Namespace namespace);

    protected Service getFirstOccurenceServiceForNamespace(Namespace namespace, String serviceName) {
        Collection<Service> services = getServiceForNamespace(namespace, serviceName);
        if (services.isEmpty()) {
            return null;
        }

        return services.iterator().next();
    }

    protected Collection<Service> getServiceForNamespace(Namespace namespace, String serviceName) {
        Collection<NamespaceServiceClusterMapping> serviceClusterMappings =
                catalogService.listServiceClusterMapping(namespace.getId(), serviceName);
        if (serviceClusterMappings == null) {
            throw new RuntimeException("Service name " + serviceName + " is not set in namespace " +
                    namespace.getName() + "(" + namespace.getId() + ")");
        }

        Collection<Service> services = new ArrayList<>(serviceClusterMappings.size());
        for (NamespaceServiceClusterMapping mapping : serviceClusterMappings) {
            Long clusterId = mapping.getClusterId();
            Cluster cluster = catalogService.getCluster(clusterId);
            if (cluster == null) {
                throw new RuntimeException("Cluster " + clusterId + " is not found");
            }

            Service service = catalogService.getServiceByName(clusterId, serviceName);
            if (service == null) {
                throw new RuntimeException("Service name " + serviceName + " is not found in Cluster " + clusterId);
            }

            services.add(service);
        }

        return services;
    }

    protected Component getComponent(Service service, String componentName) {
        Collection<Component> allComponents = catalogService.listComponents(service.getId());

        List<Component> components = allComponents.stream().filter(x -> x.getName().equals(componentName)).collect(toList());
        if (components == null || components.isEmpty()) {
            throw new RuntimeException(service.getName() + " doesn't have " + componentName + " as component");
        }

        return components.get(0);
    }

    protected T instantiate(String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<T> clazz = (Class<T>) Class.forName(className);
        return clazz.newInstance();
    }

    protected void assertHostAndPort(String componentName, String host, Integer port) {
        if (host == null || host.isEmpty() || port == null) {
            throw new RuntimeException(componentName + " component doesn't have enough information - host: " + host +
                    " / port: " + port);
        }
    }

    protected void assertHostsAndPort(String componentName, List<String> hosts, Integer port) {
        if (hosts == null || hosts.isEmpty() || port == null) {
            throw new RuntimeException(componentName + " component doesn't have enough information - hosts: " + hosts +
                    " / port: " + port);
        }
    }
}