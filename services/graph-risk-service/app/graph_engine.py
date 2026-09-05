import networkx as nx

from .models import (
    Edge, EndpointIr, GraphDelta, Node, NodeType, PathEvidence, Relationship, SecurityGraph,
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

    for endpoint in sorted(endpoints, key=lambda item: (item.endpoint, item.method)):
        endpoint_id = endpoint_node_id(endpoint)
        controller_id = f"controller:{endpoint.controller}"
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
            service_id = f"service:{endpoint.service}"
            add_node(service_id, "SERVICE", endpoint.service)
            add_edge(controller_id, service_id, "CALLS")
            previous_id = service_id

        if endpoint.repository:
            repository_id = f"repository:{endpoint.repository}"
            add_node(repository_id, "DATABASE", endpoint.repository)
            add_edge(previous_id, repository_id, "READS")
            add_edge(repository_id, resource_id, "RETURNS")
        else:
            add_edge(previous_id, resource_id, "RETURNS")

    return SecurityGraph(
        nodes=[nodes[node_id] for node_id in sorted(nodes)],
        edges=list(edges.values()),
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
        removed_paths=[before_paths[key] for key in sorted(before_paths.keys() - after_paths.keys())],
    )


def endpoint_node_id(endpoint: EndpointIr) -> str:
    return f"endpoint:{endpoint.method}:{endpoint.endpoint}"


def resource_node_id(resource: str) -> str:
    return f"resource:{resource}"


def anonymous_sensitive_paths(
    graph: SecurityGraph,
    endpoints: list[EndpointIr],
) -> dict[tuple[str, str], PathEvidence]:
    nx_graph = nx.DiGraph()
    nx_graph.add_nodes_from(node.id for node in graph.nodes)
    for edge in graph.edges:
        nx_graph.add_edge(edge.source_id, edge.target_id, relationship=edge.relationship)

    targets = {
        resource_node_id(endpoint.resource)
        for endpoint in endpoints
        if endpoint.sensitivity in SENSITIVE_LEVELS
    }
    paths: dict[tuple[str, str], PathEvidence] = {}
    for target in sorted(targets):
        try:
            node_path = nx.shortest_path(nx_graph, ANONYMOUS_NODE_ID, target)
        except (nx.NetworkXNoPath, nx.NodeNotFound):
            continue
        relationships = [
            nx_graph.edges[source, destination]["relationship"]
            for source, destination in zip(node_path, node_path[1:])
        ]
        key = (ANONYMOUS_NODE_ID, target)
        paths[key] = PathEvidence(
            source=ANONYMOUS_NODE_ID,
            target=target,
            nodes=node_path,
            edges=relationships,
        )
    return paths
