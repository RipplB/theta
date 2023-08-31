package hu.bme.mit.theta.common.ltl;

import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.cfa.analysis.CfaAction;
import hu.bme.mit.theta.cfa.analysis.CfaState;

import java.util.Collection;

public final class BuchiLts<D extends ExprState> implements LTS<CfaState<D>, CfaAction> {
	@Override
	public Collection<CfaAction> getEnabledActionsFor(CfaState<D> state) {
		return state.getLoc().getOutEdges().stream().map(CfaAction::create).toList();
	}
}
