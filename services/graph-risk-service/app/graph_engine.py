from urllib.parse import quote

import networkx as nx

from .models import (
    Edge,
    EndpointIr,
    GraphDelta,
    Node,
    NodeType,
    PathEvidence,
    Relationship,
    SecurityGraph,
)

ANONYMOUS_NODE_ID = "user:anonymous"
SENSITIVE_LEVELS = {"MEDIUM", "HIGH", "CRITICAL"}


def build_graph(endpoints: list[EndpointIr]) -> SecurityGraph:
    nodes: dict[str, Node] = {
        ANONYMOUS_NODE_ID: Node(id=ANONYMOUS_NODE_ID, node_type="USER", name="Anonymous")
    }
    edges: dict[tuple[str, str, str], Edge] = {}

    def add_node(node_id: str, node_type: NodeType, name: str) -> None:
        nodes[node_id] = Node(id=node_id, node_type=node_type, name=name)

    def add_edge(source_id: str, target_id: str, relationship: Relationship) -> None:
        key = (source_id, target_id, relationship)
        edges[key] = Edge(source_id=source_id, target_id=target_id, relationship=relationship)

    for endpoint in sorted(endpoints, key=endpoint_sort_key):
        endpoint_id = endpoint_node_id(endpoint)
        controller_id = scoped_id("controller", endpoint.controller, endpoint)
        resource_id = resource_node_id(endpoint.resource)

        add_node(endpoint_id, "ENDPOINT", f"{endpoint.method} {endpoint.endpoint}")
        add_node(controller_id, "CONTROLLER", endpoint.controller)
        add_node(resource_id, "DATA_RESOURCE", endpoint.resource)

        if endpoint.authentication:
            if endpoint.required_role:
                role_id = f"role:{endpoint.required_role}"
                add_node(role_id, "ROLE", endpoint.required_role)
                add_edge(endpoint_id, role_id, "REQUIRES_ROLE")
        else:
            add_edge(ANONYMOUS_NODE_ID, endpoint_id, "CAN_ACCESS")

        add_edge(endpoint_id, controller_id, "CALLS")

        previous_id = controller_id
        if endpoint.service:
            service_id = scoped_id("service", endpoint.service, endpoint)
            add_node(service_id, "SERVICE", endpoint.service)
            add_edge(controller_id, service_id, "CALLS")
            previous_id = service_id

        if endpoint.repository:
            repository_id = scoped_id("repository", endpoint.repository, endpoint)
            add_node(repository_id, "DATABASE", endpoint.repository)
            add_edge(previous_id, repository_id, "READS")
            add_edge(repository_id, resource_id, "RETURNS")
        else:
            add_edge(previous_id, resource_id, "RETURNS")

    return SecurityGraph(
        nodes=[nodes[node_id] for node_id in sorted(nodes)],
        edges=[edges[key] for key in sorted(edges)],
    )


def compare_graphs(before: list[EndpointIr], after: list[EndpointIr]) -> GraphDelta:
    before_graph = build_graph(before)
    after_graph = build_graph(after)
    before_paths = anonymous_sensitive_paths(before_graph, before)
    after_paths = anonymous_sensitive_paths(after_graph, after)
    return GraphDelta(
        before=before_graph,
        after=after_graph,
        new_paths=[after_paths[key] for key in sorted(after_paths.keys() - before_paths.keys())],
        removed_paths=[
            before_paths[key] for key in sorted(before_paths.keys() - after_paths.keys())
        ],
    )


def endpoint_node_id(endpoint: EndpointIr) -> str:
    return f"endpoint:{endpoint.method}:{endpoint.endpoint}"


def resource_node_id(resource: str) -> str:
    return f"resource:{resource}"


def scoped_id(kind: str, name: str, endpoint: EndpointIr) -> str:
    # Route context prevents a public method inheriting another method's private
    # repository access through a shared controller/service node. No source inference.
    context = ":".join(
        quote(value or "", safe="")
        for value in (
            endpoint.method,
            endpoint.endpoint,
            endpoint.controller,
            endpoint.service,
            endpoint.repository,
            endpoint.resource,
        )
    )
    return f"{kind}:{quote(name, safe='')}@{context}"


def endpoint_sort_key(endpoint: EndpointIr) -> tuple[str | bool, ...]:
    """Provide deterministic ordering without serializing every IR row to JSON."""
    return (
        endpoint.endpoint,
        endpoint.method,
        endpoint.controller,
        endpoint.authentication,
        endpoint.required_role or "",
        endpoint.service or "",
        endpoint.repository or "",
        endpoint.resource,
        endpoint.sensitivity,
    )


def anonymous_sensitive_paths(
    graph: SecurityGraph,
    endpoints: list[EndpointIr],
) -> dict[tuple[str, ...], PathEvidence]:
    edge_relationships = {
        (edge.source_id, edge.target_id): edge.relationship for edge in graph.edges
    }

    paths: dict[tuple[str, ...], PathEvidence] = {}
    # Each row is a resolved call/resource path. BFS on its route-context nodes
    # enumerates every represented path, including two routes to the same resource.
    for endpoint in sorted(endpoints, key=endpoint_sort_key):
        target = resource_node_id(endpoint.resource)
        if endpoint.authentication or endpoint.sensitivity not in SENSITIVE_LEVELS:
            continue
        node_chain = [
            ANONYMOUS_NODE_ID,
            endpoint_node_id(endpoint),
            scoped_id("controller", endpoint.controller, endpoint),
        ]
        if endpoint.service:
            node_chain.append(scoped_id("service", endpoint.service, endpoint))
        if endpoint.repository:
            node_chain.append(scoped_id("repository", endpoint.repository, endpoint))
        node_chain.append(target)

        # Build the bounded route slice explicitly. NetworkX subgraph views inspect
        # the anonymous node's full adjacency for every endpoint, which turns a
        # valid many-route request into quadratic work.
        route_graph = nx.DiGraph()
        route_graph.add_nodes_from(node_chain)
        for source, destination in zip(node_chain, node_chain[1:], strict=False):
            relationship = edge_relationships.get((source, destination))
            if relationship is not None:
                route_graph.add_edge(
                    source,
                    destination,
                    relationship=relationship,
                )
        try:
            node_path = nx.shortest_path(route_graph, ANONYMOUS_NODE_ID, target)
        except (nx.NetworkXNoPath, nx.NodeNotFound):
            continue
        relationships = [
            route_graph.edges[source, destination]["relationship"]
            for source, destination in zip(node_path, node_path[1:], strict=False)
        ]
        # Security identity is deliberately independent of implementation names.
        # Refactoring a controller/service/repository must not create a new access
        # path when the principal, route and resource are unchanged.
        key = (ANONYMOUS_NODE_ID, endpoint_node_id(endpoint), target)
        paths[key] = PathEvidence(
            source=ANONYMOUS_NODE_ID,
            target=target,
            nodes=node_path,
            edges=relationships,
        )
    return paths
