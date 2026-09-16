"""Conservative authorization-policy comparison for the annotation-only MVP.

The MVP can prove public/authenticated transitions, but it does not yet have the
role hierarchy or policy AST needed to order two different authenticated role
requirements. Treat those transitions as unresolved evidence instead of allowing
them to pass as a high-confidence safe change.
"""

from dataclasses import dataclass

from .models import EndpointIr


@dataclass(frozen=True)
class UnresolvedAuthorizationDelta:
    method: str
    endpoint: str
    before_role: str | None
    after_role: str | None

    def evidence(self) -> str:
        before = self.before_role or "AUTHENTICATED"
        after = self.after_role or "AUTHENTICATED"
        return (
            f"Authorization requirement changed for {self.method} {self.endpoint}: "
            f"{before} -> {after}; role ordering is outside the annotation-only MVP"
        )


def unresolved_authorization_deltas(
    before: list[EndpointIr], after: list[EndpointIr]
) -> list[UnresolvedAuthorizationDelta]:
    """Return protected-to-protected policy changes the MVP cannot order.

    EndpointPair already rejects conflicting policies for one route within a
    revision. Sets are still used here so repeated dependency-path rows collapse
    deterministically.
    """
    before_policies = _policies_by_route(before)
    after_policies = _policies_by_route(after)
    deltas: list[UnresolvedAuthorizationDelta] = []
    for method, endpoint in sorted(before_policies.keys() & after_policies.keys()):
        previous = before_policies[(method, endpoint)]
        current = after_policies[(method, endpoint)]
        if previous == current:
            continue
        before_auth, before_role = next(iter(previous))
        after_auth, after_role = next(iter(current))
        if before_auth and after_auth:
            deltas.append(UnresolvedAuthorizationDelta(method, endpoint, before_role, after_role))
    return deltas


def _policies_by_route(
    endpoints: list[EndpointIr],
) -> dict[tuple[str, str], set[tuple[bool, str | None]]]:
    policies: dict[tuple[str, str], set[tuple[bool, str | None]]] = {}
    for endpoint in endpoints:
        policies.setdefault((endpoint.method, endpoint.endpoint), set()).add(
            (endpoint.authentication, endpoint.required_role)
        )
    return policies
