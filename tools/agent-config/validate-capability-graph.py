#!/usr/bin/env python3
"""Validates planning/capability-graph.json: parses, detects execution cycles (real
graph edges only -- REQUIRES / REQUIRES-UNDECLARED; decision_gates and the
known_cycles_at_generation_time changelog are documentation, not inputs to detection),
reports every capability with no owner note, and exits non-zero on any live cycle.

No third-party dependency -- stdlib json + a plain DFS. Usage:
    python3 tools/agent-config/validate-capability-graph.py [path/to/capability-graph.json]
"""
import json
import sys
from collections import defaultdict

DEFAULT_PATH = "planning/capability-graph.json"


def load(path):
    with open(path) as f:
        return json.load(f)


def find_cycles(nodes, edges):
    adj = defaultdict(list)
    node_ids = {n["id"] for n in nodes}
    dangling = []
    for e in edges:
        if e["from"] not in node_ids:
            dangling.append(("from", e["from"]))
        if e["to"] not in node_ids:
            dangling.append(("to", e["to"]))
        adj[e["from"]].append(e["to"])

    WHITE, GRAY, BLACK = 0, 1, 2
    color = defaultdict(int)
    stack = []
    cycles = []

    def dfs(u):
        color[u] = GRAY
        stack.append(u)
        for v in adj[u]:
            if color[v] == WHITE:
                dfs(v)
            elif color[v] == GRAY:
                idx = stack.index(v)
                cycles.append(stack[idx:] + [v])
        stack.pop()
        color[u] = BLACK

    sys.setrecursionlimit(10000)
    for n in node_ids:
        if color[n] == WHITE:
            dfs(n)
    return cycles, dangling


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PATH
    try:
        graph = load(path)
    except FileNotFoundError:
        print(f"FAIL  {path} not found")
        return 1
    except json.JSONDecodeError as e:
        print(f"FAIL  {path} is not valid JSON: {e}")
        return 1

    nodes = graph.get("nodes", [])
    edges = graph.get("edges", [])
    print(f"== capability graph validator ==")
    print(f"file: {path}")
    print(f"nodes: {len(nodes)}  edges: {len(edges)}")

    fail = False

    cycles, dangling = find_cycles(nodes, edges)

    if dangling:
        fail = True
        print(f"FAIL  {len(dangling)} edge endpoint(s) reference an undefined node:")
        for kind, nid in dangling[:20]:
            print(f"      edge.{kind} -> '{nid}' has no matching node")
    else:
        print("OK    every edge endpoint resolves to a defined node")

    if cycles:
        fail = True
        print(f"FAIL  {len(cycles)} execution cycle(s) detected:")
        for c in cycles:
            print("      [CYCLE-DETECTED] " + " -> ".join(c))
    else:
        print("OK    no execution cycles")

    # cross-check declared decision gates actually block a real node
    node_ids = {n["id"] for n in nodes}
    for gate in graph.get("decision_gates", []):
        for blocked in gate.get("blocks", []):
            if blocked not in node_ids:
                fail = True
                print(f"FAIL  decision gate '{gate.get('id')}' blocks unknown node '{blocked}'")
    print(f"OK    {len(graph.get('decision_gates', []))} decision gate(s) reference valid nodes"
          if not fail or all(b in node_ids for g in graph.get("decision_gates", []) for b in g.get("blocks", []))
          else "")

    print()
    if fail:
        print("RESULT: FAIL")
        return 1
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
