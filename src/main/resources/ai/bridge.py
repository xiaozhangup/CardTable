#!/usr/bin/env python3
"""CardTable JSONL adapter for the original RLCard rule agents."""

import contextlib
import json
import os
import sys

# RLCard imports its agents registry by running pip freeze; it needs no cache.
os.environ["PIP_NO_CACHE_DIR"] = "1"

# Third-party imports may print diagnostics. stdout belongs to the JSONL protocol.
with contextlib.redirect_stdout(sys.stderr):
    from rlcard.models.doudizhu_rule_models import DouDizhuRuleAgentV1
    from rlcard.models.uno_rule_models import UNORuleAgentV1
    from rlcard.games.doudizhu.utils import INDEX


DOUDIZHU = DouDizhuRuleAgentV1()
UNO = UNORuleAgentV1()


def bid(state):
    """RLCard has no bidding agent; rate only this seat's own starting hand."""
    hand = state["current_hand"]
    counts = {rank: hand.count(rank) for rank in INDEX}
    strength = (counts["R"] * 4 + counts["B"] * 3
                + counts["2"] * 1.5 + counts["A"]
                + sum(5 for count in counts.values() if count == 4)
                + sum(1 for count in counts.values() if count == 3))
    desired = 3 if strength >= 15 else 2 if strength >= 11 else 1 if strength >= 8 else 0
    score = desired if desired > state["highest_bid"] else 0
    return {"action": "bid", "argument": str(score)}


def doudizhu_fallback(state):
    """Upstream grouping has known edge cases; execute only native legal moves."""
    actions = [action for action in state["legal_actions"] if action != "pass"]
    if not actions:
        return "pass"
    lowest = min(state["current_hand"], key=INDEX.__getitem__)
    containing_lowest = [action for action in actions if lowest in action]
    return min(containing_lowest or actions,
               key=lambda action: (len(action), max(INDEX[rank] for rank in action),
                                   tuple(INDEX[rank] for rank in action)))


def choose(request):
    game = request["game"]
    state = request["state"]
    if game == "doudizhu" and state["phase"] == "bid":
        return bid(state)
    legal_actions = state["legal_actions"]
    if not legal_actions:
        raise ValueError("No legal actions supplied")
    if game == "doudizhu":
        observation = dict(state, actions=legal_actions)
        agent = DOUDIZHU
        raw = {"raw_obs": observation}
    elif game == "uno":
        agent = UNO
        raw = {"raw_obs": state, "raw_legal_actions": legal_actions}
    else:
        raise ValueError("Unsupported game")
    try:
        with contextlib.redirect_stdout(sys.stderr):
            selected = str(agent.step(raw))
    except (KeyError, IndexError, ValueError) as error:
        print(f"RLCard rule edge case: {type(error).__name__}; choosing native legal action",
              file=sys.stderr, flush=True)
        selected = None
    if selected not in legal_actions:
        selected = doudizhu_fallback(state) if game == "doudizhu" else legal_actions[0]
    mapped = state["action_map"][selected]
    return {"action": mapped["action"], "argument": mapped.get("argument")}


def emit(value):
    print(json.dumps(value, ensure_ascii=False, separators=(",", ":")), flush=True)


def main():
    emit({"ready": True, "engine": "rlcard-rule"})
    for line in sys.stdin:
        request_id = None
        try:
            request = json.loads(line)
            request_id = request["id"]
            result = choose(request)
            emit({"id": request_id, **result})
        except Exception as error:
            print(f"AI request failed: {type(error).__name__}: {error}", file=sys.stderr, flush=True)
            emit({"id": request_id, "error": f"{type(error).__name__}: {error}"})


if __name__ == "__main__":
    main()
